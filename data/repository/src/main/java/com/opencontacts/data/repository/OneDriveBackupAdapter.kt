package com.opencontacts.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveBackupAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun stageEncryptedBackup(sourceFile: File): File {
        val dir = File(context.filesDir, "cloud_staging/onedrive").apply { mkdirs() }
        val target = File(dir, sourceFile.name)
        sourceFile.copyTo(target, overwrite = true)
        return target
    }
}
