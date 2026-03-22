package com.opencontacts.data.db.mapper

import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.model.NoteSummary
import com.opencontacts.core.model.ReminderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.model.TimelineItemSummary
import com.opencontacts.core.model.VaultSummary
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactWithRelations
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.entity.VaultRegistryEntity

fun VaultRegistryEntity.toModel() = VaultSummary(
    id = vaultId,
    displayName = displayName,
    colorToken = colorToken,
    iconToken = iconToken,
    isLocked = isLocked,
    isArchived = isArchived,
)

fun ContactWithRelations.toModel() = ContactSummary(
    id = contact.contactId,
    displayName = contact.displayName,
    primaryPhone = contact.primaryPhone,
    tags = tags.map { it.displayName },
    isFavorite = contact.isFavorite,
    folderName = folder?.displayName,
    deletedAt = contact.deletedAt,
    photoUri = contact.photoUri,
)

fun ContactEntity.toModel() = ContactSummary(
    id = contactId,
    displayName = displayName,
    primaryPhone = primaryPhone,
    tags = tagCsv.split(',').mapNotNull { tag -> tag.trim().takeIf(String::isNotEmpty) },
    isFavorite = isFavorite,
    folderName = folderName,
    deletedAt = deletedAt,
    photoUri = photoUri,
)

fun ContactSummary.toEntity(now: Long = System.currentTimeMillis()) = ContactEntity(
    contactId = id,
    displayName = displayName,
    sortKey = displayName.trim().lowercase(),
    primaryPhone = primaryPhone,
    tagCsv = tags.joinToString(","),
    isFavorite = isFavorite,
    folderName = folderName,
    createdAt = now,
    updatedAt = now,
    isDeleted = deletedAt != null,
    deletedAt = deletedAt,
    photoUri = photoUri,
)

fun ContactDraft.toEntity(contactId: String, createdAt: Long, now: Long = System.currentTimeMillis()) = ContactEntity(
    contactId = contactId,
    displayName = displayName,
    sortKey = displayName.trim().lowercase(),
    primaryPhone = primaryPhone,
    tagCsv = tags.joinToString(","),
    isFavorite = isFavorite,
    folderName = folderName,
    createdAt = createdAt,
    updatedAt = now,
    isDeleted = false,
    deletedAt = null,
    photoUri = photoUri,
)

fun TagEntity.toModel(usageCount: Int = 0) = TagSummary(
    name = displayName,
    colorToken = colorToken,
    usageCount = usageCount,
)

fun FolderEntity.toModel(usageCount: Int = 0) = FolderSummary(
    name = displayName,
    iconToken = iconToken,
    colorToken = colorToken,
    usageCount = usageCount,
    imageUri = imageUri,
)

fun NoteEntity.toModel() = NoteSummary(
    id = noteId,
    contactId = contactId,
    body = body,
    createdAt = createdAt,
)

fun ReminderEntity.toModel() = ReminderSummary(
    id = reminderId,
    contactId = contactId,
    title = title,
    dueAt = dueAt,
    isDone = isDone,
    createdAt = createdAt,
)

fun TimelineEntity.toModel() = TimelineItemSummary(
    id = timelineId,
    contactId = contactId,
    type = type,
    title = title,
    subtitle = subtitle,
    createdAt = createdAt,
)

fun BackupRecordEntity.toModel() = BackupRecordSummary(
    id = backupId,
    provider = provider,
    vaultId = vaultId,
    createdAt = createdAt,
    status = status,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
)

fun ImportExportHistoryEntity.toModel() = ImportExportHistorySummary(
    id = historyId,
    operationType = operationType,
    vaultId = vaultId,
    createdAt = createdAt,
    status = status,
    filePath = filePath,
    itemCount = itemCount,
)
