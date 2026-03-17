package com.mobileintelligence.app.dns.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.dns.data.entity.BlocklistEntity
import com.mobileintelligence.app.dns.ui.viewmodel.BlocklistViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistManagementScreen(
    viewModel: BlocklistViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val blocklists by viewModel.filteredBlocklists.collectAsState()
    val enabledCount by viewModel.enabledCount.collectAsState()
    val totalDomains by viewModel.totalEnabledDomains.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val editingBlocklist by viewModel.editingBlocklist.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()

    // Toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Blocklist Management") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                // Refresh all
                IconButton(
                    onClick = { viewModel.refreshAllEnabled() },
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, "Refresh All")
                    }
                }
                // Add custom
                IconButton(onClick = { viewModel.showAddDialog() }) {
                    Icon(Icons.Filled.Add, "Add Blocklist")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Summary Card ───────────────────────────────────
            item {
                SummaryCard(enabledCount = enabledCount, totalDomains = totalDomains)
            }

            // ─── Category Filter Chips ──────────────────────────
            item {
                CategoryFilterRow(
                    selectedCategory = filterCategory,
                    onCategorySelected = { viewModel.setFilterCategory(it) }
                )
            }

            // ─── Pre-configured lists section ───────────────────
            val builtIn = blocklists.filter { it.isBuiltIn }
            val custom = blocklists.filter { !it.isBuiltIn }

            if (builtIn.isNotEmpty()) {
                item {
                    Text(
                        "Community Blocklists",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(builtIn, key = { it.id }) { blocklist ->
                    BlocklistCard(
                        blocklist = blocklist,
                        isDownloading = downloadingIds.contains(blocklist.id),
                        onToggle = { viewModel.toggleBlocklist(blocklist.id, it) },
                        onDownload = { viewModel.downloadBlocklist(blocklist.id) },
                        onEdit = null, // Built-in can't be edited
                        onDelete = null // Built-in can't be deleted
                    )
                }
            }

            if (custom.isNotEmpty()) {
                item {
                    Text(
                        "Custom Blocklists",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                items(custom, key = { it.id }) { blocklist ->
                    BlocklistCard(
                        blocklist = blocklist,
                        isDownloading = downloadingIds.contains(blocklist.id),
                        onToggle = { viewModel.toggleBlocklist(blocklist.id, it) },
                        onDownload = { viewModel.downloadBlocklist(blocklist.id) },
                        onEdit = { viewModel.startEditing(blocklist) },
                        onDelete = { viewModel.deleteBlocklist(blocklist.id) }
                    )
                }
            }

            if (blocklists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No blocklists in this category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ─── Dialogs ────────────────────────────────────────────────

    if (showAddDialog) {
        AddBlocklistDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onAddUrl = { name, url, desc, cat ->
                viewModel.addCustomBlocklist(name, url, desc, cat)
            },
            onAddFile = { name, uri, desc, cat ->
                viewModel.addFromFile(name, uri, desc, cat)
            }
        )
    }

    editingBlocklist?.let { blocklist ->
        EditBlocklistDialog(
            blocklist = blocklist,
            onDismiss = { viewModel.stopEditing() },
            onSave = { name, url, desc, cat ->
                viewModel.updateBlocklist(blocklist.id, name, url, desc, cat)
            }
        )
    }
}

// ─── Summary Card ───────────────────────────────────────────────

@Composable
private fun SummaryCard(enabledCount: Int, totalDomains: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$enabledCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Active Lists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDomainCount(totalDomains),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Blocked Domains",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─── Category Filter ────────────────────────────────────────────

@Composable
private fun CategoryFilterRow(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    val categories = listOf(
        null to "All",
        "ads" to "Ads",
        "trackers" to "Trackers",
        "malware" to "Malware",
        "privacy" to "Privacy",
        "gambling" to "Gambling",
        "crypto" to "Crypto",
        "custom" to "Custom"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        items(categories) { (category, label) ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(label) },
                leadingIcon = if (selectedCategory == category) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

// ─── Blocklist Card ─────────────────────────────────────────────

@Composable
private fun BlocklistCard(
    blocklist: BlocklistEntity,
    isDownloading: Boolean,
    onToggle: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (blocklist.isEnabled)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                CategoryBadge(blocklist.category)

                Spacer(modifier = Modifier.width(8.dp))

                // Name and description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        blocklist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (blocklist.description.isNotEmpty()) {
                        Text(
                            blocklist.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Enable/disable toggle
                Switch(
                    checked = blocklist.isEnabled,
                    onCheckedChange = { onToggle(it) }
                )
            }

            // Status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: domain count + status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (blocklist.domainCount > 0) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${formatDomainCount(blocklist.domainCount)} domains",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (blocklist.lastUpdated > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Updated ${formatDate(blocklist.lastUpdated)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (blocklist.status == "error" && blocklist.errorMessage != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            blocklist.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right: action buttons
                Row {
                    // Download / refresh button
                    if (!blocklist.url.startsWith("local://")) {
                        IconButton(
                            onClick = onDownload,
                            enabled = !isDownloading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Edit (custom only)
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Delete (custom only)
                    if (onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Blocklist") },
            text = { Text("Are you sure you want to delete \"${blocklist.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Category Badge ─────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: String) {
    val color = when (category) {
        "ads" -> MaterialTheme.colorScheme.error
        "trackers" -> MaterialTheme.colorScheme.tertiary
        "malware" -> MaterialTheme.colorScheme.error
        "privacy" -> MaterialTheme.colorScheme.secondary
        "gambling" -> MaterialTheme.colorScheme.primary
        "crypto" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(end = 0.dp)
    ) {
        Text(
            text = category.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Add Blocklist Dialog ───────────────────────────────────────

@Composable
private fun AddBlocklistDialog(
    onDismiss: () -> Unit,
    onAddUrl: (name: String, url: String, description: String, category: String) -> Unit,
    onAddFile: (name: String, uri: android.net.Uri, description: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("custom") }
    var isFileMode by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedUri = uri
        if (uri != null && name.isEmpty()) {
            name = "Imported List"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Blocklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Source toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = !isFileMode,
                        onClick = { isFileMode = false },
                        label = { Text("URL") },
                        leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(16.dp)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isFileMode,
                        onClick = { isFileMode = true },
                        label = { Text("File") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, null, Modifier.size(16.dp)) }
                    )
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My Custom Blocklist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isFileMode) {
                    // URL input
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                } else {
                    // File picker
                    OutlinedButton(
                        onClick = { fileLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedUri != null) "File Selected" else "Choose .txt File")
                    }
                }

                // Description (optional)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category dropdown
                CategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isFileMode && selectedUri != null) {
                        onAddFile(name, selectedUri!!, description, category)
                    } else if (!isFileMode && url.isNotBlank()) {
                        onAddUrl(name.ifBlank { "Custom List" }, url.trim(), description, category)
                    }
                },
                enabled = name.isNotBlank() && ((!isFileMode && url.isNotBlank()) || (isFileMode && selectedUri != null))
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Edit Blocklist Dialog ──────────────────────────────────────

@Composable
private fun EditBlocklistDialog(
    blocklist: BlocklistEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, description: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf(blocklist.name) }
    var url by remember { mutableStateOf(blocklist.url) }
    var description by remember { mutableStateOf(blocklist.description) }
    var category by remember { mutableStateOf(blocklist.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Blocklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!blocklist.url.startsWith("local://")) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, url, description, category) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Category Dropdown ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("custom", "ads", "trackers", "malware", "privacy", "gambling", "crypto")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedCategory.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun formatDomainCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> "$count"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
