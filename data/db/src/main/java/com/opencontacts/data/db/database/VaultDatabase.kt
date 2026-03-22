package com.opencontacts.data.db.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opencontacts.data.db.dao.ContactsDao
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity

@Database(
    entities = [
        ContactEntity::class,
        NoteEntity::class,
        ReminderEntity::class,
        TimelineEntity::class,
        BackupRecordEntity::class,
        ImportExportHistoryEntity::class,
        TagEntity::class,
        FolderEntity::class,
        ContactTagCrossRef::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun contactsDao(): ContactsDao
}
