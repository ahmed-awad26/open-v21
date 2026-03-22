package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Backup & restore", style = MaterialTheme.typography.headlineMedium)
                        Text("Create encrypted vault backups, restore previous snapshots, and connect cloud destinations.")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BackupTile("Create local backup", "Encrypted snapshot", Icons.Default.Save, Modifier.weight(1f)) { viewModel.createBackup() }
                    BackupTile("Restore latest", "Recover recent snapshot", Icons.Default.Restore, Modifier.weight(1f)) { viewModel.restoreLatest() }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BackupTile("Connect Google Drive", "Open browser sign-in for Drive", Icons.Default.CloudSync, Modifier.weight(1f)) { openBrowser(context, Uri.parse("https://accounts.google.com/AccountChooser?continue=https://drive.google.com/drive/my-drive")); viewModel.stageGoogleDrive() }
                    BackupTile("Connect OneDrive", "Open browser sign-in for OneDrive", Icons.Default.CloudSync, Modifier.weight(1f)) { openBrowser(context, Uri.parse("https://login.live.com/")); viewModel.stageOneDrive() }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Lock, null); Text("Cloud note", style = MaterialTheme.typography.titleMedium) }
                        Text("Google Drive and OneDrive buttons currently stage encrypted backup packages and expose the handoff point needed for OAuth/cloud sync wiring.")
                    }
                }
            }
            if (status != null) {
                item { Card(modifier = Modifier.fillMaxWidth()) { Text(status!!, modifier = Modifier.padding(16.dp)) } }
            }
            item { Text("Backup history", style = MaterialTheme.typography.titleLarge) }
            if (records.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No backup records yet", style = MaterialTheme.typography.titleMedium)
                            Text("Create a local backup first, then stage it to a cloud destination.")
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record -> BackupRecordCard(record) }
            }
        }
    }
}

@Composable
private fun BackupTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BackupRecordCard(record: BackupRecordSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.status, style = MaterialTheme.typography.titleMedium)
            Text("Provider: ${record.provider}")
            Text("File: ${record.filePath}")
            Text("Size: ${record.fileSizeBytes} bytes")
            Text("Created: ${formatTimestamp(record.createdAt)}")
        }
    }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val transferRepository: VaultTransferRepository,
) : ViewModel() {
    val records: StateFlow<List<BackupRecordSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) flowOf(emptyList()) else transferRepository.observeBackupRecords(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun createBackup() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.createLocalBackup(vaultId)
            _status.value = "Backup created: ${result.filePath}"
        }
    }

    fun restoreLatest() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val restored = transferRepository.restoreLatestLocalBackup(vaultId)
            _status.value = if (restored) "Latest backup restored into active vault" else "No backup file found for active vault"
        }
    }

    fun stageGoogleDrive() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.stageLatestBackupToGoogleDrive(vaultId)
            _status.value = "Google Drive handoff ready: ${result.filePath}"
        }
    }

    fun stageOneDrive() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.stageLatestBackupToOneDrive(vaultId)
            _status.value = "OneDrive handoff ready: ${result.filePath}"
        }
    }
}

private fun formatTimestamp(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

private fun openBrowser(context: android.content.Context, uri: Uri) {
    val pm = context.packageManager
    val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
    val candidates = pm.queryIntentActivities(baseIntent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
    val preferred = candidates.firstOrNull { pkg ->
        listOf("chrome", "firefox", "browser", "edge", "opera").any { key -> pkg.contains(key, ignoreCase = true) }
    }
    val finalIntent = baseIntent.apply { preferred?.let { setPackage(it) } }
    context.startActivity(finalIntent)
}
