package com.opencontacts.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceRoute(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()

    var folderEditor by remember { mutableStateOf<FolderEditorState?>(null) }
    var tagEditor by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedContactIds by remember { mutableStateOf(setOf<String>()) }
    var selectedFolderNames by remember { mutableStateOf(setOf<String>()) }
    var selectedTagNames by remember { mutableStateOf(setOf<String>()) }
    var addContactsDialog by remember { mutableStateOf<AddContactsState?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        folderEditor = folderEditor?.copy(imageUri = uri?.toString())
    }

    val filteredContacts = remember(contacts, selectedTag, selectedFolder) {
        when {
            selectedTag != null -> contacts.filter { selectedTag in it.tags }
            selectedFolder != null -> contacts.filter { it.folderName == selectedFolder }
            else -> emptyList()
        }
    }

    val insideContainer = selectedFolder != null || selectedTag != null
    val contactSelectionMode = selectedContactIds.isNotEmpty()
    val folderSelectionMode = selectedFolderNames.isNotEmpty()
    val tagSelectionMode = selectedTagNames.isNotEmpty()

    fun requestDelete(req: DeleteRequest, direct: () -> Unit) {
        if (confirmDelete) deleteRequest = req else direct()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!insideContainer) {
                SectionHeaderRow(
                    title = "Folders",
                    action = {
                        IconButton(onClick = { folderEditor = FolderEditorState() }) {
                            Icon(Icons.Default.Add, contentDescription = "Add folder")
                        }
                    },
                )

                if (folderSelectionMode) {
                    SelectionToolbar(
                        count = selectedFolderNames.size,
                        onDelete = {
                            val names = selectedFolderNames.toList()
                            requestDelete(
                                DeleteRequest(
                                    title = "Delete folder${if (names.size > 1) "s" else ""}",
                                    message = "Delete ${names.size} folder(s)? Contacts will remain but folder assignment will be removed after you reclassify them.",
                                    onConfirm = {
                                        names.forEach(viewModel::deleteFolder)
                                        selectedFolderNames = emptySet()
                                    },
                                )
                            ) { names.forEach(viewModel::deleteFolder); selectedFolderNames = emptySet() }
                        },
                        onClear = { selectedFolderNames = emptySet() },
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 12.dp),
                ) {
                    items(folders, key = { it.name }) { folder ->
                        FolderCard(
                            folder = folder,
                            selected = folder.name in selectedFolderNames,
                            selectionMode = folderSelectionMode,
                            onClick = {
                                if (folderSelectionMode) {
                                    selectedFolderNames = selectedFolderNames.toggle(folder.name)
                                } else {
                                    selectedFolder = folder.name
                                    selectedTag = null
                                    selectedContactIds = emptySet()
                                }
                            },
                            onLongPress = {
                                selectedFolderNames = selectedFolderNames.toggle(folder.name)
                            },
                            onEdit = {
                                folderEditor = FolderEditorState(
                                    originalName = folder.name,
                                    displayName = folder.name,
                                    imageUri = folder.imageUri,
                                )
                            },
                        )
                    }
                }

                SectionHeaderRow(
                    title = "Tags",
                    action = {
                        IconButton(onClick = { tagEditor = "" }) {
                            Icon(Icons.Default.Add, contentDescription = "Add tag")
                        }
                    },
                )

                if (tagSelectionMode) {
                    SelectionToolbar(
                        count = selectedTagNames.size,
                        onDelete = {
                            val names = selectedTagNames.toList()
                            requestDelete(
                                DeleteRequest(
                                    title = "Delete tag${if (names.size > 1) "s" else ""}",
                                    message = "Delete ${names.size} tag(s)? Contacts will remain but tag assignment will be removed.",
                                    onConfirm = {
                                        names.forEach(viewModel::deleteTag)
                                        selectedTagNames = emptySet()
                                    },
                                )
                            ) { names.forEach(viewModel::deleteTag); selectedTagNames = emptySet() }
                        },
                        onClear = { selectedTagNames = emptySet() },
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.name in selectedTagNames,
                            onClick = {
                                if (tagSelectionMode) {
                                    selectedTagNames = selectedTagNames.toggle(tag.name)
                                } else {
                                    selectedTag = tag.name
                                    selectedFolder = null
                                    selectedContactIds = emptySet()
                                }
                            },
                            label = { Text(tag.name) },
                            leadingIcon = { Icon(Icons.Default.Label, null) },
                            trailingIcon = if (tagSelectionMode) null else ({ Icon(Icons.Default.Edit, null) }),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (tagSelectionMode) selectedTagNames = selectedTagNames.toggle(tag.name)
                                    else {
                                        selectedTag = tag.name
                                        selectedFolder = null
                                        selectedContactIds = emptySet()
                                    }
                                },
                                onLongClick = { selectedTagNames = selectedTagNames.toggle(tag.name) },
                            ),
                        )
                    }
                }
            } else {
                val title = selectedFolder ?: selectedTag ?: ""
                SectionHeaderRow(
                    title = title,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                addContactsDialog = AddContactsState(
                                    title = if (selectedFolder != null) "Add contacts to folder" else "Add contacts to tag",
                                    mode = if (selectedFolder != null) AddMode.FOLDER else AddMode.TAG,
                                )
                            }) { Icon(Icons.Default.Add, contentDescription = "Add contacts") }
                            if (selectedFolder != null) {
                                IconButton(onClick = {
                                    folderEditor = FolderEditorState(
                                        originalName = selectedFolder,
                                        displayName = selectedFolder.orEmpty(),
                                        imageUri = folders.firstOrNull { it.name == selectedFolder }?.imageUri,
                                    )
                                }) { Icon(Icons.Default.Edit, contentDescription = "Edit folder") }
                            }
                            if (selectedTag != null) {
                                IconButton(onClick = { tagEditor = selectedTag }) { Icon(Icons.Default.Edit, contentDescription = "Edit tag") }
                            }
                            TextButton(onClick = {
                                selectedFolder = null
                                selectedTag = null
                                selectedContactIds = emptySet()
                            }) { Text("Close") }
                        }
                    },
                )

                if (contactSelectionMode) {
                    SelectionToolbar(
                        count = selectedContactIds.size,
                        deleteLabel = if (selectedFolder != null) "Remove" else "Remove",
                        onDelete = {
                            if (selectedFolder != null) {
                                viewModel.removeFolderFromContacts(selectedContactIds)
                            } else if (selectedTag != null) {
                                viewModel.removeTagFromContacts(selectedContactIds, selectedTag!!)
                            }
                            selectedContactIds = emptySet()
                        },
                        onClear = { selectedContactIds = emptySet() },
                    )
                }

                if (filteredContacts.isEmpty()) {
                    EmptyStateCard("No contacts here yet", "Use Add contacts to classify items inside this folder or tag.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            ContactMiniCard(
                                contact = contact,
                                selected = contact.id in selectedContactIds,
                                selectionMode = contactSelectionMode,
                                onOpen = {
                                    if (contactSelectionMode) selectedContactIds = selectedContactIds.toggle(contact.id)
                                    else onOpenDetails(contact.id)
                                },
                                onLongPress = { selectedContactIds = selectedContactIds.toggle(contact.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    folderEditor?.let { editor ->
        FolderEditorDialog(
            state = editor,
            onStateChange = { folderEditor = it },
            onPickImage = { imagePicker.launch("image/*") },
            onClearImage = { folderEditor = folderEditor?.copy(imageUri = null) },
            onDismiss = { folderEditor = null },
            onConfirm = {
                val clean = editor.displayName.trim()
                if (clean.isNotBlank()) {
                    if (editor.originalName != null && editor.originalName != clean) {
                        viewModel.renameFolder(editor.originalName, clean, editor.imageUri)
                        if (selectedFolder == editor.originalName) selectedFolder = clean
                    } else {
                        viewModel.saveFolder(clean, editor.imageUri)
                    }
                }
                folderEditor = null
            },
        )
    }

    tagEditor?.let { value ->
        NameDialog(
            title = if (tags.any { it.name == value }) "Edit tag" else "New tag",
            value = value,
            label = "Tag name",
            onValueChange = { tagEditor = it },
            onDismiss = { tagEditor = null },
            onConfirm = {
                val clean = value.trim()
                if (clean.isNotBlank()) {
                    if (selectedTag != null && selectedTag != clean) viewModel.renameTag(selectedTag!!, clean)
                    else viewModel.saveTag(clean)
                    if (selectedTag != null) selectedTag = clean
                }
                tagEditor = null
            },
        )
    }

    addContactsDialog?.let { state ->
        AddContactsDialog(
            title = state.title,
            contacts = contacts,
            existingFolders = folders.map { it.name },
            existingTags = tags.map { it.name },
            alreadyIncludedIds = filteredContacts.map { it.id }.toSet(),
            onDismiss = { addContactsDialog = null },
            onConfirm = { ids, newName, existingName ->
                val chosen = (newName.ifBlank { existingName }).trim()
                if (ids.isNotEmpty() && chosen.isNotBlank()) {
                    if (state.mode == AddMode.FOLDER) {
                        viewModel.saveFolder(chosen, null)
                        viewModel.assignFolderToContacts(ids, chosen)
                        selectedFolder = chosen
                    } else {
                        viewModel.saveTag(chosen)
                        viewModel.assignTagToContacts(ids, chosen)
                        selectedTag = chosen
                    }
                }
                addContactsDialog = null
            },
            mode = state.mode,
        )
    }

    deleteRequest?.let { req ->
        DeleteConfirmationDialog(
            title = req.title,
            message = req.message,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                req.onConfirm()
                deleteRequest = null
            },
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        action()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FolderSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = CardDefaults.elevatedShape,
        tonalElevation = if (selected) 4.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (folder.imageUri != null) {
                    AsyncImage(
                        model = folder.imageUri,
                        contentDescription = folder.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                if (!selectionMode) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        IconButton(modifier = Modifier.size(26.dp), onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit folder", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactMiniCard(
    contact: ContactSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onOpen() })
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodyMedium)
                if (contact.tags.isNotEmpty()) Text(contact.tags.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    deleteLabel: String = "Delete",
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selected", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text(deleteLabel) }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle)
        }
    }
}

@Composable
private fun AddContactsDialog(
    title: String,
    contacts: List<ContactSummary>,
    existingFolders: List<String>,
    existingTags: List<String>,
    alreadyIncludedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>, String, String) -> Unit,
    mode: AddMode,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var newName by remember { mutableStateOf("") }
    var selectedExisting by remember { mutableStateOf("") }
    val candidates = if (mode == AddMode.FOLDER) existingFolders else existingTags

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(if (mode == AddMode.FOLDER) "New folder name" else "New tag name") },
                    singleLine = true,
                )
                if (candidates.isNotEmpty()) {
                    Text("Or choose existing", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        candidates.forEach { name ->
                            FilterChip(selected = selectedExisting == name, onClick = { selectedExisting = if (selectedExisting == name) "" else name }, label = { Text(name) })
                        }
                    }
                }
                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts.filterNot { it.id in alreadyIncludedIds }, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = contact.id in selected,
                                onCheckedChange = {
                                    selected = selected.toggle(contact.id)
                                },
                            )
                            Column {
                                Text(contact.displayName)
                                Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected, newName, selectedExisting) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderEditorDialog(
    state: FolderEditorState,
    onStateChange: (FolderEditorState) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.originalName == null) "New folder" else "Edit folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { onStateChange(state.copy(displayName = it)) },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.imageUri != null) {
                        AsyncImage(
                            model = state.imageUri,
                            contentDescription = "Folder image",
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPickImage) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Text("Choose image")
                        }
                        if (state.imageUri != null) {
                            TextButton(onClick = onClearImage) { Text("Remove image") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
    appLockRepository: AppLockRepository,
) : ViewModel() {
    val confirmDelete = appLockRepository.settings
        .map { it.confirmDelete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val tags = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertTag(vaultId, TagSummary(name = name)) }
    }

    fun saveFolder(name: String, imageUri: String?) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertFolder(vaultId, FolderSummary(name = name, imageUri = imageUri)) }
    }

    fun deleteTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteTag(vaultId, name) }
    }

    fun deleteFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteFolder(vaultId, name) }
    }

    fun renameTag(oldName: String, newName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName) {
                contactRepository.upsertTag(vaultId, TagSummary(name = newName))
                contacts.value.filter { oldName in it.tags }.forEach { current ->
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags.map { if (it == oldName) newName else it }, isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri))
                }
                contactRepository.deleteTag(vaultId, oldName)
            }
        }
    }

    fun renameFolder(oldName: String, newName: String, imageUri: String?) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName || imageUri != folders.value.firstOrNull { it.name == oldName }?.imageUri) {
                contactRepository.upsertFolder(vaultId, FolderSummary(name = newName, imageUri = imageUri))
                contacts.value.filter { it.folderName == oldName }.forEach { current ->
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = newName, photoUri = current.photoUri))
                }
                if (oldName != newName) contactRepository.deleteFolder(vaultId, oldName)
            }
        }
    }

    fun removeTagFromContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags.filterNot { it == tag }, isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri))
            }
        }
    }

    fun removeFolderFromContacts(contactIds: Set<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = null, photoUri = current.photoUri))
            }
        }
    }

    fun assignFolderToContacts(contactIds: Set<String>, folder: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = folder, photoUri = current.photoUri))
            }
        }
    }

    fun assignTagToContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = (current.tags + tag).distinct(), isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri))
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private data class DeleteRequest(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

private data class FolderEditorState(
    val originalName: String? = null,
    val displayName: String = "",
    val imageUri: String? = null,
)

private data class AddContactsState(
    val title: String,
    val mode: AddMode,
)

private enum class AddMode { FOLDER, TAG }
