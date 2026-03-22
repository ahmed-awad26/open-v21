package com.opencontacts.app

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SecurityRoute(
    onBack: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.appLockSettings.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val activeVaultId by viewModel.activeVaultId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var retentionDays by remember(settings.trashRetentionDays) { mutableStateOf(settings.trashRetentionDays.toString()) }
    var exportPath by remember(settings.exportPath) { mutableStateOf(settings.exportPath) }
    var backupPath by remember(settings.backupPath) { mutableStateOf(settings.backupPath) }
    var deleteVaultId by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Settings & security", style = MaterialTheme.typography.headlineMedium)
                        Text("Manage lock options, theme mode, trash retention, and vault deletion from one place.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecurityStat("PIN", if (settings.hasPin) "Enabled" else "Off")
                            SecurityStat("Biometric", if (settings.biometricEnabled) "Enabled" else "Off")
                            SecurityStat("Theme", settings.themeMode)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null)
                            Text("Appearance", style = MaterialTheme.typography.titleLarge)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = settings.themeMode == "LIGHT", onClick = { viewModel.setThemeMode("LIGHT") }, label = { Text("Light") })
                            FilterChip(selected = settings.themeMode == "DARK", onClick = { viewModel.setThemeMode("DARK") }, label = { Text("Dark") })
                            FilterChip(selected = settings.themeMode == "SYSTEM", onClick = { viewModel.setThemeMode("SYSTEM") }, label = { Text("System") })
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Password, contentDescription = null)
                            Text("PIN", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                pin = it
                                viewModel.clearError()
                            },
                            label = { Text(if (settings.hasPin) "Change PIN" else "Set PIN") },
                            singleLine = true,
                            supportingText = {
                                if (pinError != null) Text(pinError ?: "") else Text("Use at least 4 digits")
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.setPin(pin) }) { Text(if (settings.hasPin) "Update PIN" else "Save PIN") }
                            if (settings.hasPin) Button(onClick = viewModel::clearPin) { Text("Clear PIN") }
                        }
                    }
                }
            }

            if (viewModel.canUseBiometric()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Column {
                                    Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
                                    Text("Use fingerprint or face unlock when available")
                                }
                            }
                            Switch(
                                checked = settings.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        viewModel.setBiometricEnabled(false)
                                    } else {
                                        val activity = context.findFragmentActivity() ?: run {
                                            viewModel.showUiError("Biometric unlock is unavailable on this screen.")
                                            return@Switch
                                        }
                                        runCatching {
                                            val prompt = BiometricPrompt(
                                                activity,
                                                ContextCompat.getMainExecutor(activity),
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        super.onAuthenticationSucceeded(result)
                                                        viewModel.setBiometricEnabled(true)
                                                    }

                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                        super.onAuthenticationError(errorCode, errString)
                                                        viewModel.showUiError(errString.toString())
                                                    }
                                                },
                                            )
                                            prompt.authenticate(viewModel.biometricPromptInfo("OpenContacts"))
                                        }.onFailure {
                                            viewModel.showUiError(it.message ?: "Unable to start biometric prompt")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Text("Export & backup locations", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(value = exportPath, onValueChange = { exportPath = it }, label = { Text("Export folder (inside app storage)") }, singleLine = true)
                        Button(onClick = { viewModel.setExportPath(exportPath) }) { Text("Save export path") }
                        OutlinedTextField(value = backupPath, onValueChange = { backupPath = it }, label = { Text("Backup folder (inside app storage)") }, singleLine = true)
                        Button(onClick = { viewModel.setBackupPath(backupPath) }) { Text("Save backup path") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Delete confirmation", style = MaterialTheme.typography.titleMedium)
                                Text("Show confirmation before deleting items anywhere in the app")
                            }
                            Switch(checked = settings.confirmDelete, onCheckedChange = viewModel::setConfirmDelete)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Text("Trash retention", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(
                            value = retentionDays,
                            onValueChange = { retentionDays = it.filter(Char::isDigit) },
                            label = { Text("Keep deleted contacts for days") },
                            singleLine = true,
                        )
                        Button(onClick = { viewModel.setTrashRetentionDays(retentionDays.toIntOrNull() ?: settings.trashRetentionDays) }) {
                            Text("Save retention")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Text("Immediate actions", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = viewModel::lockNow) { Text("Lock app now") }
                    }
                }
            }

            item {
                Text("Vault deletion", style = MaterialTheme.typography.titleLarge)
            }
            items(vaults, key = { it.id }) { vault ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    vault.id == activeVaultId -> "Current vault"
                                    vault.isLocked -> "Locked"
                                    else -> "Private vault"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = { if (settings.confirmDelete) deleteVaultId = vault.id else viewModel.deleteVault(vault.id) },
                            enabled = vault.id != activeVaultId && vaults.size > 1,
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete vault")
                        }
                    }
                }
            }
        }

        deleteVaultId?.let { vaultId ->
            val vaultName = vaults.firstOrNull { it.id == vaultId }?.displayName ?: "this vault"
            DeleteConfirmationDialog(
                title = "Delete vault",
                message = "Delete $vaultName? This action removes the vault from the registry.",
                onDismiss = { deleteVaultId = null },
                onConfirm = {
                    viewModel.deleteVault(vaultId)
                    deleteVaultId = null
                },
            )
        }
    }
}

@Composable
private fun SecurityStat(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = CardDefaults.shape) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }

        deleteVaultId?.let { vaultId ->
            val vaultName = vaults.firstOrNull { it.id == vaultId }?.displayName ?: "this vault"
            DeleteConfirmationDialog(
                title = "Delete vault",
                message = "Delete $vaultName? This action removes the vault from the registry.",
                onDismiss = { deleteVaultId = null },
                onConfirm = {
                    viewModel.deleteVault(vaultId)
                    deleteVaultId = null
                },
            )
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
