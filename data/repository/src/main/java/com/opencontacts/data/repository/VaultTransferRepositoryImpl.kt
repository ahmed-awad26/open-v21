package com.opencontacts.data.repository

import android.content.Context
import android.util.Base64
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.VaultPassphraseManager
import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.data.db.database.VaultDatabaseFactory
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.mapper.toModel
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class VaultTransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseFactory: VaultDatabaseFactory,
    private val vaultPassphraseManager: VaultPassphraseManager,
    private val appLockRepository: AppLockRepository,
    private val googleDriveBackupAdapter: GoogleDriveBackupAdapter,
    private val oneDriveBackupAdapter: OneDriveBackupAdapter,
    private val phoneContactsBridge: PhoneContactsBridge,
    private val backupFileCodec: BackupFileCodec,
    private val vcfHandler: VcfHandler,
    private val csvHandler: CsvHandler,
) : VaultTransferRepository {
    override fun observeBackupRecords(vaultId: String): Flow<List<BackupRecordSummary>> = flow {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        emitAll(db.contactsDao().observeBackupRecords().map { it.map { entity -> entity.toModel() } })
    }

    override fun observeImportExportHistory(vaultId: String): Flow<List<ImportExportHistorySummary>> = flow {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        emitAll(db.contactsDao().observeImportExportHistory().map { it.map { entity -> entity.toModel() } })
    }

    override suspend fun createLocalBackup(vaultId: String): BackupRecordSummary {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        val now = System.currentTimeMillis()
        val file = File(backupDir(), stamped("vault", vaultId, "ocbak"))
        val payload = JSONObject().apply {
            put("vaultId", vaultId)
            put("createdAt", now)
            put("contacts", JSONArray(dao.getAll().map { it.toJson() }))
            put("notes", JSONArray(dao.getAllNotes().map { it.toJson() }))
            put("reminders", JSONArray(dao.getAllReminders().map { it.toJson() }))
            put("timeline", JSONArray(dao.getAllTimelineItems().map { it.toJson() }))
            put("tags", JSONArray(dao.getTags().map { it.toJson() }))
            put("folders", JSONArray(dao.getFolders().map { it.toJson() }))
            put("crossRefs", JSONArray(dao.getAllCrossRefs().map { it.toJson() }))
        }
        val encryptedPayload = encryptForVault(vaultId, payload.toString(2).encodeToByteArray())
        file.writeBytes(backupFileCodec.wrap(encryptedPayload.encodeToByteArray(), now, dao.count()))
        val entity = BackupRecordEntity(UUID.randomUUID().toString(), "LOCAL", vaultId, now, "SUCCESS", file.absolutePath, file.length())
        dao.upsertBackupRecord(entity)
        return entity.toModel()
    }

    override suspend fun restoreLatestLocalBackup(vaultId: String): Boolean {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        val latest = backupDir().listFiles()?.filter { it.name.startsWith("vault-${vaultId}-") }?.maxByOrNull { it.lastModified() } ?: return false
        val wrappedPayload = latest.readBytes()
        val encryptedPayload = backupFileCodec.unwrap(wrappedPayload).decodeToString()
        val root = JSONObject(decryptForVault(vaultId, encryptedPayload).decodeToString())
        dao.clearTimeline(); dao.clearNotes(); dao.clearReminders(); dao.clearAll(); dao.clearBackupRecords(); dao.clearImportExportHistory()
        dao.upsertFolders(root.optJSONArray("folders")?.toFolderEntities().orEmpty())
        dao.upsertTags(root.optJSONArray("tags")?.toTagEntities().orEmpty())
        dao.upsertAll(root.optJSONArray("contacts")?.toContactEntities().orEmpty())
        dao.insertContactTagCrossRefs(root.optJSONArray("crossRefs")?.toCrossRefs().orEmpty())
        dao.upsertNotes(root.optJSONArray("notes")?.toNoteEntities().orEmpty())
        dao.upsertReminders(root.optJSONArray("reminders")?.toReminderEntities().orEmpty())
        dao.insertTimelineItems(root.optJSONArray("timeline")?.toTimelineEntities().orEmpty())
        dao.upsertBackupRecord(BackupRecordEntity(UUID.randomUUID().toString(), "LOCAL", vaultId, System.currentTimeMillis(), "RESTORED", latest.absolutePath, latest.length()))
        return true
    }

    override suspend fun stageLatestBackupToGoogleDrive(vaultId: String): BackupRecordSummary {
        val local = createLocalBackup(vaultId)
        val staged = googleDriveBackupAdapter.stageEncryptedBackup(File(local.filePath))
        return persistCloudRecord(vaultId, "GOOGLE_DRIVE_STAGED", staged)
    }

    override suspend fun stageLatestBackupToOneDrive(vaultId: String): BackupRecordSummary {
        val local = createLocalBackup(vaultId)
        val staged = oneDriveBackupAdapter.stageEncryptedBackup(File(local.filePath))
        return persistCloudRecord(vaultId, "ONEDRIVE_STAGED", staged)
    }

    override suspend fun exportContactsJson(vaultId: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val file = File(exportDir(), stamped("contacts", vaultId, "json"))
        val payload = JSONObject().apply {
            put("vaultId", vaultId)
            put("createdAt", now)
            put("contacts", JSONArray(contacts.map { it.toJson() }))
        }
        file.writeText(payload.toString(2))
        return persistHistory(dao, "EXPORT_JSON", vaultId, now, "SUCCESS", file.absolutePath, contacts.size)
    }

    override suspend fun importLatestContactsJson(vaultId: String): ImportExportHistorySummary {
        val latest = exportDir().listFiles()?.filter { it.name.startsWith("contacts-${vaultId}-") && it.extension == "json" }?.maxByOrNull { it.lastModified() }
            ?: return persistHistory(vaultDatabaseFactory.getDatabase(vaultId).contactsDao(), "IMPORT_JSON", vaultId, System.currentTimeMillis(), "NO_FILE", "", 0)
        val root = JSONObject(latest.readText())
        val contacts = root.getJSONArray("contacts").toContactSummaries()
        return importSummaries(vaultId, contacts, "IMPORT_JSON", latest.absolutePath)
    }

    override suspend fun exportContactsCsv(vaultId: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val file = File(exportDir(), stamped("contacts", vaultId, "csv"))
        file.outputStream().use { csvHandler.write(contacts, it) }
        return persistHistory(dao, "EXPORT_CSV", vaultId, now, "SUCCESS", file.absolutePath, contacts.size)
    }

    override suspend fun importLatestContactsCsv(vaultId: String): ImportExportHistorySummary {
        val latest = exportDir().listFiles()?.filter { it.name.startsWith("contacts-${vaultId}-") && it.extension == "csv" }?.maxByOrNull { it.lastModified() }
            ?: File(importDir(), "contacts.csv").takeIf { it.exists() }
            ?: return persistHistory(vaultDatabaseFactory.getDatabase(vaultId).contactsDao(), "IMPORT_CSV", vaultId, System.currentTimeMillis(), "NO_FILE", "", 0)
        val contacts = latest.inputStream().use { csvHandler.parse(it) }
        return importSummaries(vaultId, contacts, "IMPORT_CSV", latest.absolutePath)
    }

    override suspend fun exportContactsVcf(vaultId: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val file = File(exportDir(), stamped("contacts", vaultId, "vcf"))
        file.outputStream().use { vcfHandler.write(contacts, it) }
        return persistHistory(dao, "EXPORT_VCF", vaultId, now, "SUCCESS", file.absolutePath, contacts.size)
    }

    override suspend fun exportContactsExcel(vaultId: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val file = File(exportDir(), stamped("contacts", vaultId, "xls"))
        file.writeText(buildString {
            append("<html><head><meta charset=\"utf-8\"></head><body><table border=\"1\">")
            append("<tr><th>Name</th><th>Phone</th><th>Tags</th><th>Folder</th><th>Favorite</th></tr>")
            contacts.forEach { contact ->
                append("<tr>")
                append("<td>${escapeHtml(contact.displayName)}</td>")
                append("<td>${escapeHtml(contact.primaryPhone.orEmpty())}</td>")
                append("<td>${escapeHtml(contact.tags.joinToString(" | "))}</td>")
                append("<td>${escapeHtml(contact.folderName.orEmpty())}</td>")
                append("<td>${if (contact.isFavorite) "Yes" else "No"}</td>")
                append("</tr>")
            }
            append("</table></body></html>")
        })
        return persistHistory(dao, "EXPORT_EXCEL", vaultId, now, "SUCCESS", file.absolutePath, contacts.size)
    }

    override suspend fun importLatestContactsVcf(vaultId: String): ImportExportHistorySummary {
        val latest = exportDir().listFiles()?.filter { it.name.startsWith("contacts-${vaultId}-") && it.extension == "vcf" }?.maxByOrNull { it.lastModified() }
            ?: File(importDir(), "contacts.vcf").takeIf { it.exists() }
            ?: return persistHistory(vaultDatabaseFactory.getDatabase(vaultId).contactsDao(), "IMPORT_VCF", vaultId, System.currentTimeMillis(), "NO_FILE", "", 0)
        val contacts = latest.inputStream().use { vcfHandler.parse(it) }
        return importSummaries(vaultId, contacts, "IMPORT_VCF", latest.absolutePath)
    }

    override suspend fun importFromPhoneContacts(vaultId: String): ImportExportHistorySummary {
        val contacts = phoneContactsBridge.importContacts()
        return importSummaries(vaultId, contacts, "IMPORT_PHONE", "content://contacts")
    }

    override suspend fun exportAllContactsToPhone(vaultId: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val inserted = phoneContactsBridge.exportContacts(contacts)
        return persistHistory(dao, "EXPORT_PHONE", vaultId, System.currentTimeMillis(), "SUCCESS", "content://contacts", inserted)
    }

    private suspend fun importSummaries(vaultId: String, contacts: List<ContactSummary>, operation: String, filePath: String): ImportExportHistorySummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        contacts.forEach { summary ->
            val id = summary.id.ifBlank { UUID.randomUUID().toString() }
            dao.upsert(ContactEntity(id, summary.displayName, summary.displayName.lowercase(), summary.primaryPhone, summary.tags.joinToString(","), summary.isFavorite, summary.folderName, now, now, false, null))
            summary.folderName?.takeIf { it.isNotBlank() }?.let { dao.upsertFolder(FolderEntity(it, it, "folder", "blue", null, now)) }
            dao.deleteCrossRefsForContact(id)
            val tags = summary.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            dao.upsertTags(tags.map { TagEntity(it, it, "default", now) })
            dao.insertContactTagCrossRefs(tags.map { ContactTagCrossRef(id, it) })
        }
        return persistHistory(dao, operation, vaultId, now, "SUCCESS", filePath, contacts.size)
    }

    private suspend fun persistCloudRecord(vaultId: String, provider: String, stagedFile: File): BackupRecordSummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val entity = BackupRecordEntity(UUID.randomUUID().toString(), provider, vaultId, System.currentTimeMillis(), "STAGED", stagedFile.absolutePath, stagedFile.length())
        dao.upsertBackupRecord(entity)
        return entity.toModel()
    }

    private suspend fun persistHistory(
        dao: com.opencontacts.data.db.dao.ContactsDao,
        operationType: String,
        vaultId: String,
        createdAt: Long,
        status: String,
        filePath: String,
        itemCount: Int,
    ): ImportExportHistorySummary {
        val entity = ImportExportHistoryEntity(UUID.randomUUID().toString(), operationType, vaultId, createdAt, status, filePath, itemCount)
        dao.upsertImportExportHistory(entity)
        return entity.toModel()
    }

    private suspend fun backupDir(): File {
        val relative = appLockRepository.settings.first().backupPath.ifBlank { "vault_backups" }
        return File(context.filesDir, relative).apply { mkdirs() }
    }
    private suspend fun exportDir(): File {
        val relative = appLockRepository.settings.first().exportPath.ifBlank { "vault_exports" }
        return File(context.filesDir, relative).apply { mkdirs() }
    }
    private fun importDir(): File = File(context.filesDir, "vault_imports").apply { mkdirs() }
    private fun stamped(prefix: String, vaultId: String, extension: String): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date())
        return "${prefix}-${vaultId}-${stamp}.${extension}"
    }

    private suspend fun encryptForVault(vaultId: String, payload: ByteArray): String {
        val keyBytes = vaultPassphraseManager.getOrCreatePassphrase(vaultId)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = SecretKeySpec(keyBytes.copyOf(32), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(payload)
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } finally { keyBytes.fill(0) }
    }

    private suspend fun decryptForVault(vaultId: String, payload: String): ByteArray {
        val keyBytes = vaultPassphraseManager.getOrCreatePassphrase(vaultId)
        return try {
            val parts = payload.split(':', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = SecretKeySpec(keyBytes.copyOf(32), "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(encrypted)
        } finally { keyBytes.fill(0) }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

private fun ContactEntity.toJson(): JSONObject = JSONObject().apply {
    put("contactId", contactId)
    put("displayName", displayName)
    put("sortKey", sortKey)
    put("primaryPhone", primaryPhone)
    put("tagCsv", tagCsv)
    put("isFavorite", isFavorite)
    put("folderName", folderName)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
    put("deletedAt", deletedAt)
    put("photoUri", photoUri)
}
private fun TagEntity.toJson(): JSONObject = JSONObject().apply { put("tagName", tagName); put("displayName", displayName); put("colorToken", colorToken); put("createdAt", createdAt) }
private fun FolderEntity.toJson(): JSONObject = JSONObject().apply { put("folderName", folderName); put("displayName", displayName); put("iconToken", iconToken); put("colorToken", colorToken); put("imageUri", imageUri); put("createdAt", createdAt) }
private fun ContactTagCrossRef.toJson(): JSONObject = JSONObject().apply { put("contactId", contactId); put("tagName", tagName) }
private fun NoteEntity.toJson(): JSONObject = JSONObject().apply { put("noteId", noteId); put("contactId", contactId); put("body", body); put("createdAt", createdAt) }
private fun ReminderEntity.toJson(): JSONObject = JSONObject().apply { put("reminderId", reminderId); put("contactId", contactId); put("title", title); put("dueAt", dueAt); put("isDone", isDone); put("createdAt", createdAt) }
private fun TimelineEntity.toJson(): JSONObject = JSONObject().apply { put("timelineId", timelineId); put("contactId", contactId); put("type", type); put("title", title); put("subtitle", subtitle); put("createdAt", createdAt) }
private fun ContactSummary.toJson(): JSONObject = JSONObject().apply { put("id", id); put("displayName", displayName); put("primaryPhone", primaryPhone); put("tags", JSONArray(tags)); put("isFavorite", isFavorite); put("folderName", folderName); put("photoUri", photoUri) }

private fun JSONArray.toContactEntities(): List<ContactEntity> = (0 until length()).map { index ->
    val obj = getJSONObject(index)
    ContactEntity(
        obj.getString("contactId"),
        obj.getString("displayName"),
        obj.getString("sortKey"),
        obj.optString("primaryPhone").takeIf { it.isNotBlank() },
        obj.optString("tagCsv"),
        obj.optBoolean("isFavorite", false),
        obj.optString("folderName").takeIf { it.isNotBlank() },
        obj.optLong("createdAt"),
        obj.optLong("updatedAt"),
        obj.optBoolean("isDeleted", false),
        obj.optLong("deletedAt").takeIf { it > 0L },
        obj.optString("photoUri").takeIf { it.isNotBlank() },
    )
}
private fun JSONArray.toTagEntities(): List<TagEntity> = (0 until length()).map { i -> getJSONObject(i).let { TagEntity(it.getString("tagName"), it.getString("displayName"), it.optString("colorToken", "default"), it.optLong("createdAt")) } }
private fun JSONArray.toFolderEntities(): List<FolderEntity> = (0 until length()).map { i -> getJSONObject(i).let { FolderEntity(it.getString("folderName"), it.getString("displayName"), it.optString("iconToken", "folder"), it.optString("colorToken", "blue"), it.optString("imageUri").takeIf { v -> v.isNotBlank() }, it.optLong("createdAt")) } }
private fun JSONArray.toCrossRefs(): List<ContactTagCrossRef> = (0 until length()).map { i -> getJSONObject(i).let { ContactTagCrossRef(it.getString("contactId"), it.getString("tagName")) } }
private fun JSONArray.toNoteEntities(): List<NoteEntity> = (0 until length()).map { i -> getJSONObject(i).let { NoteEntity(it.getString("noteId"), it.getString("contactId"), it.getString("body"), it.optLong("createdAt")) } }
private fun JSONArray.toReminderEntities(): List<ReminderEntity> = (0 until length()).map { i -> getJSONObject(i).let { ReminderEntity(it.getString("reminderId"), it.getString("contactId"), it.getString("title"), it.optLong("dueAt"), it.optBoolean("isDone", false), it.optLong("createdAt")) } }
private fun JSONArray.toTimelineEntities(): List<TimelineEntity> = (0 until length()).map { i -> getJSONObject(i).let { TimelineEntity(it.getString("timelineId"), it.getString("contactId"), it.getString("type"), it.getString("title"), it.optString("subtitle").takeIf { s -> s.isNotBlank() }, it.optLong("createdAt")) } }
private fun JSONArray.toContactSummaries(): List<ContactSummary> = (0 until length()).map { i ->
    val obj = getJSONObject(i)
    ContactSummary(obj.optString("id", UUID.randomUUID().toString()), obj.getString("displayName"), obj.optString("primaryPhone").takeIf { it.isNotBlank() }, obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { idx -> arr.getString(idx) } }.orEmpty(), obj.optBoolean("isFavorite", false), obj.optString("folderName").takeIf { it.isNotBlank() }, null, obj.optString("photoUri").takeIf { it.isNotBlank() })
}

private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
