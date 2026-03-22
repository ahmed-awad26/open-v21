package com.opencontacts.feature.contacts

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.widget.Toast
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.VaultSummary

import com.opencontacts.core.ui.fastscroll.AlphabetFastScroller
import com.opencontacts.core.ui.fastscroll.activeSectionForPosition
import com.opencontacts.core.ui.fastscroll.buildAlphabetIndex
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SortMode { NAME, FAVORITES, FOLDER }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsRoute(
    activeVaultName: String = "Active vault",
    vaults: List<VaultSummary> = emptyList(),
    onOpenDetails: (String) -> Unit,
    onOpenWorkspace: (() -> Unit)? = null,
    onOpenImportExport: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenBackup: (() -> Unit)? = null,
    onOpenTrash: (() -> Unit)? = null,
    onOpenVaults: (() -> Unit)? = null,
    onSwitchVault: ((String) -> Unit)? = null,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val editing by viewModel.editingContact.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var permissionPromptDismissed by rememberSaveable { mutableStateOf(false) }
    var dialPadVisible by remember { mutableStateOf(false) }
    var dialNumber by rememberSaveable { mutableStateOf("") }
    var bulkFolderEditor by remember { mutableStateOf<String?>(null) }
    var bulkTagEditor by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshCallLogs()
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty() && !permissionPromptDismissed) permissionLauncher.launch(missing.toTypedArray()) else viewModel.refreshCallLogs()
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.refreshCallLogs()
    }

    val filteredContacts = remember(contacts, favoritesOnly, sortMode) {
        contacts.filter { contact -> !favoritesOnly || contact.isFavorite }
            .sortedWith(
                when (sortMode) {
                    SortMode.NAME -> compareBy { it.displayName.lowercase() }
                    SortMode.FAVORITES -> compareByDescending<ContactSummary> { it.isFavorite }.thenBy { it.displayName.lowercase() }
                    SortMode.FOLDER -> compareBy<ContactSummary> { it.folderName.orEmpty().lowercase() }.thenBy { it.displayName.lowercase() }
                }
            )
    }

    val selectedContacts = remember(selectedIds, contacts) { contacts.filter { it.id in selectedIds } }
    val listState = rememberLazyListState()
    val alphabetIndex = remember(filteredContacts) {
        buildAlphabetIndex(filteredContacts) { it.displayName }
    }
    val activeSection by remember(filteredContacts, listState) {
        derivedStateOf {
            activeSectionForPosition(
                items = filteredContacts,
                position = listState.firstVisibleItemIndex,
                nameSelector = { it.displayName },
            )
        }
    }

    fun openDial(phone: String?) {
        val raw = phone?.trim().orEmpty()
        if (raw.isBlank()) return
        val uri = Uri.parse("tel:$raw")
        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPermission) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
        context.startActivity(intent)
    }

    fun shareSelectedAsText() {
        if (selectedContacts.isEmpty()) return
        val payload = selectedContacts.joinToString("\n\n") { contact ->
            buildString {
                append(contact.displayName)
                contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("\n$it") }
                if (contact.tags.isNotEmpty()) append("\nTags: ${contact.tags.joinToString()}")
                contact.folderName?.let { append("\nFolder: $it") }
            }
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                },
                "Share contact(s)",
            )
        )
    }

    fun shareQr(contact: ContactSummary) {
        qrPayload = buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("FN:${contact.displayName}\n")
            contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("TEL:$it\n") }
            if (contact.tags.isNotEmpty()) append("NOTE:Tags=${contact.tags.joinToString()}\n")
            contact.folderName?.let { append("ORG:$it\n") }
            append("END:VCARD")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Vaults", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                vaults.forEach { vault ->
                    DrawerItem(
                        title = vault.displayName,
                        subtitle = if (vault.displayName == activeVaultName) "Current vault" else if (vault.isLocked) "Locked" else "Switch vault",
                        icon = Icons.Default.Folder,
                        selected = vault.displayName == activeVaultName,
                    ) {
                        onSwitchVault?.invoke(vault.id)
                        scope.launch { drawerState.close() }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DrawerItem("Add / change vault", "Create or rename vaults", Icons.Default.Add) { onOpenVaults?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Groups", "Tags & folders in current vault", Icons.Default.Groups) { onOpenWorkspace?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem(if (favoritesOnly) "Favorites only" else "Show favorites", "Toggle favorite filter", Icons.Default.Star, selected = favoritesOnly) { favoritesOnly = !favoritesOnly; scope.launch { drawerState.close() } }
                DrawerItem("Import / Export", "CSV, VCF, Excel", Icons.Default.Sync) { onOpenImportExport?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Backup", "Local and Drive staging", Icons.Default.Storage) { onOpenBackup?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Trash", "Restore or permanently delete", Icons.Default.DeleteSweep) { onOpenTrash?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Settings", "Security, theme, retention", Icons.Default.Settings) { onOpenSecurity?.invoke(); scope.launch { drawerState.close() } }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(26.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeAction("Search", Icons.Default.Search) { onOpenSearch?.invoke() }
                        HomeAction("Groups", Icons.Default.Groups) { onOpenWorkspace?.invoke() }
                        HomeAction("Add", Icons.Default.Add) { viewModel.startCreate() }
                        HomeAction("Dial", Icons.Default.Dialpad) { dialPadVisible = true }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    onClick = { onOpenSearch?.invoke() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Search contacts",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TabRow(pagerState.currentPageIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Contacts") },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Call log") },
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> {
                            if (selectedIds.isNotEmpty()) {
                                SelectionBar(
                                    count = selectedIds.size,
                                    allSelected = selectedIds.size == filteredContacts.size && filteredContacts.isNotEmpty(),
                                    onSelectAll = { selectedIds = if (selectedIds.size == filteredContacts.size) emptySet() else filteredContacts.map { it.id }.toSet() },
                                    onShare = ::shareSelectedAsText,
                                    onDelete = { viewModel.deleteMany(selectedIds); selectedIds = emptySet() },
                                    onEdit = { selectedContacts.singleOrNull()?.let(viewModel::startEdit) },
                                    onQr = { selectedContacts.singleOrNull()?.let(::shareQr) },
                                    onAssignFolder = { bulkFolderEditor = selectedContacts.firstOrNull()?.folderName.orEmpty() },
                                    onAssignTag = { bulkTagEditor = selectedContacts.firstOrNull()?.tags?.firstOrNull().orEmpty() },
                                    qrEnabled = selectedIds.size == 1,
                                    editEnabled = selectedIds.size == 1,
                                )
                            }
                            if (filteredContacts.isEmpty()) {
                                EmptyStateCard("No contacts yet", "Add a private contact, import a CSV or VCF file, or bring in all your phone contacts.")
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize().padding(end = 24.dp),
                                    ) {
                                        items(filteredContacts, key = { it.id }) { contact ->
                                            val selected = contact.id in selectedIds
                                            ContactModernCard(
                                                contact = contact,
                                                selected = selected,
                                                selectionMode = selectedIds.isNotEmpty(),
                                                onToggleSelection = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                                onLongPress = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                                onOpen = {
                                                    if (selectedIds.isNotEmpty()) selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id
                                                    else onOpenDetails(contact.id)
                                                },
                                                onCall = { openDial(contact.primaryPhone) },
                                            )
                                        }
                                        item { Spacer(Modifier.height(96.dp)) }
                                    }
                                    AlphabetFastScroller(
                                        index = alphabetIndex,
                                        activeLetter = activeSection,
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        onLetterChanged = { selected ->
                                            val target = alphabetIndex.resolveNearestPosition(selected)
                                            if (target != null && target != listState.firstVisibleItemIndex) {
                                                scope.launch { listState.scrollToItem(target) }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        else -> {
                            if (callLogs.isEmpty()) {
                                EmptyStateCard("No recent calls", "Grant call-log permission to show recent incoming, outgoing and missed calls.")
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                    items(callLogs, key = { it.id }) { item -> CallLogCard(item = item, onDial = { openDial(item.number) }) }
                                    item { Spacer(Modifier.height(96.dp)) }
                                }
                            }
                        }
                    }
                }
            editing?.let { editor ->
                FullScreenContactEditor(
                    state = editor,
                    onStateChange = viewModel::updateEditor,
                    onDismiss = viewModel::dismissEditor,
                    onConfirm = viewModel::saveEditor,
                )
            }
            bulkFolderEditor?.let { value ->
                ClassificationPickerDialog(
                    title = "Assign folder",
                    value = value,
                    existingNames = folders.map { it.name },
                    label = "Folder name",
                    onValueChange = { bulkFolderEditor = it },
                    onDismiss = { bulkFolderEditor = null },
                    onConfirm = {
                        val clean = bulkFolderEditor?.trim().orEmpty()
                        if (clean.isNotBlank()) viewModel.assignFolderToMany(selectedIds, clean)
                        bulkFolderEditor = null
                        selectedIds = emptySet()
                    },
                )
            }
            bulkTagEditor?.let { value ->
                ClassificationPickerDialog(
                    title = "Assign tag",
                    value = value,
                    existingNames = tags.map { it.name },
                    label = "Tag name",
                    onValueChange = { bulkTagEditor = it },
                    onDismiss = { bulkTagEditor = null },
                    onConfirm = {
                        val clean = bulkTagEditor?.trim().orEmpty().removePrefix("#")
                        if (clean.isNotBlank()) viewModel.assignTagToMany(selectedIds, clean)
                        bulkTagEditor = null
                        selectedIds = emptySet()
                    },
                )
            }
            qrPayload?.let { payload -> QrCodeDialog(payload = payload, onDismiss = { qrPayload = null }) }
            if (dialPadVisible) {
                DialPadBottomSheet(
                    number = dialNumber,
                    onNumberChange = { dialNumber = it },
                    onDismiss = { dialPadVisible = false },
                    onCall = { openDial(dialNumber) },
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(title: String, subtitle: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun HomeAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.combinedClickable(onClick = onClick)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, contentDescription = label, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    qrEnabled: Boolean,
    editEnabled: Boolean,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
    onAssignFolder: () -> Unit,
    onAssignTag: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSelectAll) { Icon(if (allSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, contentDescription = "Select all") }
                IconButton(onClick = onAssignFolder) { Icon(Icons.Default.CreateNewFolder, contentDescription = "Assign folder") }
                IconButton(onClick = onAssignTag) { Icon(Icons.Default.Groups, contentDescription = "Assign tag") }
                IconButton(onClick = onEdit, enabled = editEnabled) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
                IconButton(onClick = onQr, enabled = qrEnabled) { Icon(Icons.Default.QrCode2, contentDescription = "QR") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun ContactModernCard(contact: ContactSummary, selected: Boolean, selectionMode: Boolean, onToggleSelection: () -> Unit, onLongPress: () -> Unit, onOpen: () -> Unit, onCall: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        val avatarBitmap = remember(contact.photoUri, context) { loadAvatarBitmap(context = context, uri = contact.photoUri) }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                    if (selectionMode) { Checkbox(checked = selected, onCheckedChange = { onToggleSelection() }); Spacer(Modifier.width(8.dp)) }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (avatarBitmap != null) {
                                Image(bitmap = avatarBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            } else {
                                Text(contact.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(contact.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (contact.isFavorite) Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(contact.primaryPhone ?: "No phone number", style = MaterialTheme.typography.bodyLarge)
                        contact.folderName?.let { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Folder, null) }) }
                    }
                }
                IconButton(onClick = onCall, enabled = !contact.primaryPhone.isNullOrBlank()) { Icon(Icons.Default.Phone, contentDescription = "Call") }
            }
            if (contact.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contact.tags.take(8).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) } }
            }
        }
    }
}

@Composable
private fun CallLogCard(item: CallLogItem, onDial: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.cachedName ?: item.number, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${item.type} • ${formatTime(item.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.type == "Missed") Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                )
                if (item.durationSeconds > 0) Text("${item.durationSeconds}s", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDial) { Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = "Dial") }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle) } }
}

@Composable
private fun FullScreenContactEditor(state: ContactEditorState, onStateChange: (ContactEditorState) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onStateChange(state.copy(photoUri = uri?.toString().orEmpty()))
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.id == null) "Add contact" else "Edit contact", style = MaterialTheme.typography.headlineMedium)
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onConfirm) { Text("Save") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Main details", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.OutlinedTextField(value = state.displayName, onValueChange = { onStateChange(state.copy(displayName = it)) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    androidx.compose.material3.OutlinedTextField(value = state.phone, onValueChange = { onStateChange(state.copy(phone = it)) }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    androidx.compose.material3.OutlinedTextField(value = state.folderName, onValueChange = { onStateChange(state.copy(folderName = it)) }, label = { Text("Folder") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    androidx.compose.material3.OutlinedTextField(value = state.tags, onValueChange = { onStateChange(state.copy(tags = it)) }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.isFavorite, onCheckedChange = { onStateChange(state.copy(isFavorite = it)) })
                        Text("Favorite")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Contact photo", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.photoUri.isBlank()) "No photo selected" else "Photo attached")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { photoPicker.launch("image/*") }) { Text(if (state.photoUri.isBlank()) "Add photo" else "Change photo") }
                        if (state.photoUri.isNotBlank()) {
                            TextButton(onClick = { onStateChange(state.copy(photoUri = "")) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialPadBottomSheet(number: String, onNumberChange: (String) -> Unit, onDismiss: () -> Unit, onCall: () -> Unit) {
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to "",
    )
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(number.ifBlank { "Enter number" }, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { if (number.isNotEmpty()) onNumberChange(number.dropLast(1)) }, modifier = Modifier.size(84.dp)) {
                        Icon(Icons.Default.Backspace, contentDescription = "Delete digit", modifier = Modifier.size(38.dp))
                    }
                }
                keys.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { (digit, letters) ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(88.dp).combinedClickable { onNumberChange(number + digit) }) {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(digit, style = MaterialTheme.typography.headlineMedium)
                                    if (letters.isNotBlank()) Text(letters, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Surface(shape = CircleShape, color = Color(0xFF16A34A), modifier = Modifier.align(Alignment.BottomCenter)) {
                IconButton(onClick = onCall, modifier = Modifier.size(82.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QrCodeDialog(payload: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(payload) { generateQrBitmap(payload, 720) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share as QR") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(220.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(12.dp)) }
                Text("Scan to import contact data quickly.")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val ok = bitmap?.let { shareBitmap(context, it) } ?: false
                    if (!ok) Toast.makeText(context, "Unable to share QR", Toast.LENGTH_SHORT).show()
                }) { Text("Share") }
                TextButton(onClick = {
                    val ok = bitmap?.let { saveBitmapToGallery(context, it) } ?: false
                    Toast.makeText(context, if (ok) "QR saved to gallery" else "Unable to save QR", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun generateQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val name = "opencontacts_qr_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenContacts")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: return false
        true
    }.getOrDefault(false)
}

private fun shareBitmap(context: android.content.Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val file = File(context.cacheDir, "qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(context, "com.opencontacts.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("QR", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR"))
        true
    }.getOrDefault(false)
}


@Composable
private fun ClassificationPickerDialog(
    title: String,
    value: String,
    existingNames: List<String>,
    label: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true)
                if (existingNames.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existingNames.forEach { name ->
                            AssistChip(onClick = { onValueChange(name) }, label = { Text(name) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}



private fun loadAvatarBitmap(context: android.content.Context, uri: String?): Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
}

@Composable
private fun NameDialog(title: String, value: String, label: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
