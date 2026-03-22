package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index("display_name")]
)
data class FolderEntity(
    @PrimaryKey @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "icon_token") val iconToken: String,
    @ColumnInfo(name = "color_token") val colorToken: String,
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
