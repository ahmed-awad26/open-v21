package com.opencontacts.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.opencontacts.core.model.ContactDetails
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.data.db.dao.ContactsDao
import com.opencontacts.data.db.database.VaultDatabaseFactory
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.mapper.toEntity
import com.opencontacts.data.db.mapper.toModel
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseFactory: VaultDatabaseFactory,
) : ContactRepository {

    override fun observeContacts(vaultId: String): Flow<List<ContactSummary>> = flow {
        val database = vaultDatabaseFactory.getDatabase(vaultId)
        emitAll(
            database.contactsDao()
                .observeAllDetailed()
                .map { entities -> entities.map { it.toModel() } },
        )
    }

    override fun observeContactDetails(
        vaultId: String,
        contactId: String,
    ): Flow<ContactDetails?> = flow {
        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        emitAll(
            combine(
                dao.observeByIdDetailed(contactId),
                dao.observeNotes(contactId),
                dao.observeReminders(contactId),
                dao.observeTimeline(contactId),
            ) { contact, notes, reminders, timeline ->
                contact?.let {
                    ContactDetails(
                        contact = it.toModel(),
                        notes = notes.map { note -> note.toModel() },
                        reminders = reminders.map { reminder -> reminder.toModel() },
                        timeline = timeline.map { item -> item.toModel() },
                    )
                }
            },
        )
    }

    override fun observeTags(vaultId: String): Flow<List<TagSummary>> = flow {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        emitAll(dao.observeTags().map { list -> list.map { it.toModel() } })
    }

    override fun observeFolders(vaultId: String): Flow<List<FolderSummary>> = flow {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        emitAll(dao.observeFolders().map { list -> list.map { it.toModel() } })
    }

    override fun observeTrash(vaultId: String): Flow<List<ContactSummary>> = flow {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        emitAll(dao.observeTrashDetailed().map { list -> list.map { it.toModel() } })
    }

    override suspend fun upsertContact(vaultId: String, contact: ContactSummary) {
        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        val now = System.currentTimeMillis()

        val normalizedTags = extractTagsFromMetadata(contact.displayName, contact.tags)
        dao.upsert(contact.copy(tags = normalizedTags).toEntity(now))
        syncClassifications(dao, contact.id, normalizedTags, contact.folderName, now)
        dao.insertTimeline(
            TimelineEntity(
                timelineId = UUID.randomUUID().toString(),
                contactId = contact.id,
                type = "CONTACT_UPDATED",
                title = "Contact saved",
                subtitle = contact.displayName,
                createdAt = now,
            ),
        )
    }

    override suspend fun saveContactDraft(vaultId: String, draft: ContactDraft): String {
        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        val now = System.currentTimeMillis()
        val contactId = draft.id ?: UUID.randomUUID().toString()
        val existing = dao.getById(contactId)

        val normalizedTags = extractTagsFromMetadata(draft.displayName, draft.tags)
        dao.upsert(
            draft.copy(tags = normalizedTags).toEntity(
                contactId = contactId,
                createdAt = existing?.createdAt ?: now,
                now = now,
            ),
        )

        syncClassifications(dao, contactId, normalizedTags, draft.folderName, now)
        dao.insertTimeline(
            TimelineEntity(
                timelineId = UUID.randomUUID().toString(),
                contactId = contactId,
                type = if (existing == null) "CONTACT_CREATED" else "CONTACT_UPDATED",
                title = if (existing == null) "Contact created" else "Contact updated",
                subtitle = draft.displayName,
                createdAt = now,
            ),
        )

        return contactId
    }

    override suspend fun deleteContact(vaultId: String, contactId: String) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val current = dao.getAnyById(contactId) ?: return
        dao.upsert(
            current.copy(
                isDeleted = true,
                deletedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun restoreContact(vaultId: String, contactId: String) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val current = dao.getAnyById(contactId) ?: return
        dao.upsert(
            current.copy(
                isDeleted = false,
                deletedAt = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun permanentlyDeleteContact(vaultId: String, contactId: String) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        dao.deleteCrossRefsForContact(contactId)
        dao.deleteNotesForContact(contactId)
        dao.deleteRemindersForContact(contactId)
        dao.deleteTimelineForContact(contactId)
        dao.hardDeleteById(contactId)
    }

    override suspend fun purgeDeletedOlderThan(vaultId: String, cutoffMillis: Long) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()

        dao.getTrashDetailed()
            .map { it.contact }
            .filter { (it.deletedAt ?: Long.MAX_VALUE) <= cutoffMillis }
            .forEach { entity ->
                dao.deleteCrossRefsForContact(entity.contactId)
                dao.deleteNotesForContact(entity.contactId)
                dao.deleteRemindersForContact(entity.contactId)
                dao.deleteTimelineForContact(entity.contactId)
                dao.hardDeleteById(entity.contactId)
            }
    }

    override suspend fun addNote(vaultId: String, contactId: String, body: String) {
        if (body.isBlank()) return

        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        val now = System.currentTimeMillis()

        dao.upsertNote(
            NoteEntity(
                noteId = UUID.randomUUID().toString(),
                contactId = contactId,
                body = body.trim(),
                createdAt = now,
            ),
        )

        dao.insertTimeline(
            TimelineEntity(
                timelineId = UUID.randomUUID().toString(),
                contactId = contactId,
                type = "NOTE_ADDED",
                title = "Note added",
                subtitle = body.trim().take(80),
                createdAt = now,
            ),
        )
    }

    override suspend fun deleteNote(vaultId: String, noteId: String) {
        vaultDatabaseFactory.getDatabase(vaultId).contactsDao().deleteNoteById(noteId)
    }

    override suspend fun addReminder(vaultId: String, contactId: String, title: String, dueAt: Long) {
        if (title.isBlank()) return

        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        val now = System.currentTimeMillis()
        val reminderId = UUID.randomUUID().toString()

        dao.upsertReminder(
            ReminderEntity(
                reminderId = reminderId,
                contactId = contactId,
                title = title.trim(),
                dueAt = dueAt,
                isDone = false,
                createdAt = now,
            ),
        )

        dao.insertTimeline(
            TimelineEntity(
                timelineId = UUID.randomUUID().toString(),
                contactId = contactId,
                type = "REMINDER_ADDED",
                title = "Reminder scheduled",
                subtitle = title.trim(),
                createdAt = now,
            ),
        )

        scheduleReminder(reminderId, title.trim(), dueAt)
    }

    override suspend fun setReminderDone(vaultId: String, reminderId: String, done: Boolean) {
        val database = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = database.contactsDao()
        val existing = dao.getReminderById(reminderId) ?: return

        dao.updateReminder(existing.copy(isDone = done))
        dao.insertTimeline(
            TimelineEntity(
                timelineId = UUID.randomUUID().toString(),
                contactId = existing.contactId,
                type = if (done) "REMINDER_DONE" else "REMINDER_REOPENED",
                title = if (done) "Reminder completed" else "Reminder reopened",
                subtitle = existing.title,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteReminder(vaultId: String, reminderId: String) {
        vaultDatabaseFactory.getDatabase(vaultId).contactsDao().deleteReminderById(reminderId)
    }

    override suspend fun upsertTag(vaultId: String, tag: TagSummary) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        dao.upsertTag(
            TagEntity(
                tagName = tag.name,
                displayName = tag.name,
                colorToken = tag.colorToken,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteTag(vaultId: String, tagName: String) {
        vaultDatabaseFactory.getDatabase(vaultId).contactsDao().deleteTag(tagName)
    }

    override suspend fun upsertFolder(vaultId: String, folder: FolderSummary) {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        dao.upsertFolder(
            FolderEntity(
                folderName = folder.name,
                displayName = folder.name,
                iconToken = folder.iconToken,
                colorToken = folder.colorToken,
                imageUri = folder.imageUri,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteFolder(vaultId: String, folderName: String) {
        vaultDatabaseFactory.getDatabase(vaultId).contactsDao().deleteFolder(folderName)
    }

    private suspend fun syncClassifications(
        dao: ContactsDao,
        contactId: String,
        tags: List<String>,
        folderName: String?,
        now: Long,
    ) {
        folderName?.takeIf { it.isNotBlank() }?.let {
            dao.upsertFolder(
                FolderEntity(
                    folderName = it.trim(),
                    displayName = it.trim(),
                    iconToken = "folder",
                    colorToken = "blue",
                    imageUri = null,
                    createdAt = now,
                ),
            )
        }

        dao.deleteCrossRefsForContact(contactId)

        val contactEntity = dao.getAnyById(contactId)
        val normalized = extractTagsFromMetadata(contactEntity?.displayName.orEmpty(), tags)

        dao.upsertTags(
            normalized.map {
                TagEntity(
                    tagName = it,
                    displayName = it,
                    colorToken = "default",
                    createdAt = now,
                )
            },
        )

        dao.insertContactTagCrossRefs(
            normalized.map {
                ContactTagCrossRef(
                    contactId = contactId,
                    tagName = it,
                )
            },
        )
    }

    private fun extractTagsFromMetadata(displayName: String, explicitTags: List<String>): List<String> {
        val inlineTags = Regex("""(?:^|[^\p{L}\p{N}_])#([\p{L}\p{N}_-]+)(?=$|[^\p{L}\p{N}_-])""")
            .findAll(displayName)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        return (explicitTags + inlineTags)
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun scheduleReminder(reminderId: String, title: String, dueAt: Long) {
        val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)

        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_BODY, "Contact reminder is due")
            .build()

        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder-$reminderId")
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }
}
