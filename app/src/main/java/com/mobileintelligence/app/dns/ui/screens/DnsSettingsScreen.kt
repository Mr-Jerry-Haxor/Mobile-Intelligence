package com.mobileintelligence.app.dns.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.dns.ui.viewmodel.DnsSettingsViewModel
import com.mobileintelligence.app.ui.components.NumLockDialog
import com.mobileintelligence.app.ui.components.NumLockMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    viewModel: DnsSettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToBlocklists: () -> Unit = {}
) {
    val dnsProvider by viewModel.dnsProvider.collectAsState()
    val customDnsPrimary by viewModel.customDnsPrimary.collectAsState()
    val customDnsSecondary by viewModel.customDnsSecondary.collectAsState()
    val blockMode by viewModel.blockMode.collectAsState()
    val blockAds by viewModel.blockAds.collectAsState()
    val blockTrackers by viewModel.blockTrackers.collectAsState()
    val blockMalware by viewModel.blockMalware.collectAsState()
    val autoStart by viewModel.vpnAutoStart.collectAsState()
    val logEnabled by viewModel.loggingEnabled.collectAsState()
    val retentionDays by viewModel.logRetentionDays.collectAsState()

    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val numLockEnabled by prefs.isNumLockEnabled.collectAsState(initial = false)

    var showDnsProviderDialog by remember { mutableStateOf(false) }
    var showBlockModeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }
    var showVerifyForClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("DNS Settings") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ─── DNS Provider ───────────────────────────────────
            item { SettingsSectionHeader("DNS Provider") }

            item {
                SettingsClickableRow(
                    title = "Upstream DNS",
                    subtitle = dnsProvider.replaceFirstChar { it.uppercase() },
                    icon = Icons.Filled.Dns,
                    onClick = { showDnsProviderDialog = true }
                )
            }

            if (dnsProvider == "custom") {
                item {
                    SettingsClickableRow(
                        title = "Custom DNS Servers",
                        subtitle = "$customDnsPrimary / $customDnsSecondary",
                        icon = Icons.Filled.Edit,
                        onClick = { showCustomDnsDialog = true }
                    )
                }
            }

            // ─── Block Mode ─────────────────────────────────────
            item { SettingsSectionHeader("Blocking") }

            item {
                SettingsClickableRow(
                    title = "Block Mode",
                    subtitle = when (blockMode) {
                        "nxdomain" -> "NXDOMAIN (recommended)"
                        "zero_ip" -> "0.0.0.0 / ::"
                        "localhost" -> "127.0.0.1"
                        else -> blockMode
                    },
                    icon = Icons.Filled.Block,
                    onClick = { showBlockModeDialog = true }
                )
            }

            // ─── Filter Categories ──────────────────────────────
            item { SettingsSectionHeader("Filter Categories") }

            item {
                SettingsSwitchRow(
                    title = "Block Ads",
                    subtitle = "Ad-serving and advertising domains",
                    icon = Icons.Filled.AdsClick,
                    checked = blockAds,
                    onCheckedChange = { viewModel.setBlockAds(it) }
                )
            }

            item {
                SettingsSwitchRow(
                    title = "Block Trackers",
                    subtitle = "Analytics and tracking domains",
                    icon = Icons.Filled.Visibility,
                    checked = blockTrackers,
                    onCheckedChange = { viewModel.setBlockTrackers(it) }
                )
            }

            item {
                SettingsSwitchRow(
                    title = "Block Malware",
                    subtitle = "Malicious and phishing domains",
                    icon = Icons.Filled.Security,
                    checked = blockMalware,
                    onCheckedChange = { viewModel.setBlockMalware(it) }
                )
            }

            // ─── Service Settings ───────────────────────────────
            item { SettingsSectionHeader("Service") }

            item {
                SettingsSwitchRow(
                    title = "Auto-start on Boot",
                    subtitle = "Restart DNS protection after device reboot",
                    icon = Icons.Filled.PowerSettingsNew,
                    checked = autoStart,
                    onCheckedChange = { viewModel.setVpnAutoStart(it) }
                )
            }

            // ─── Logging & Data ─────────────────────────────────
            item { SettingsSectionHeader("Logging & Data") }

            item {
                SettingsSwitchRow(
                    title = "Query Logging",
                    subtitle = "Log all DNS queries to local database",
                    icon = Icons.Filled.History,
                    checked = logEnabled,
                    onCheckedChange = { viewModel.setLoggingEnabled(it) }
                )
            }

            item {
                SettingsClickableRow(
                    title = "Data Retention",
                    subtitle = "$retentionDays days",
                    icon = Icons.Filled.Storage,
                    onClick = { viewModel.setLogRetentionDays(if (retentionDays >= 90) 7 else retentionDays + 7) }
                )
            }

            item {
                SettingsClickableRow(
                    title = "Update Blocklists",
                    subtitle = "Download latest blocklist updates",
                    icon = Icons.Filled.CloudDownload,
                    onClick = { viewModel.updateBlocklists() }
                )
            }

            item {
                @Suppress("DEPRECATION")
                SettingsClickableRow(
                    title = "Manage Blocklists",
                    subtitle = "Add custom lists, enable community blocklists",
                    icon = Icons.Filled.PlaylistAdd,
                    onClick = onNavigateToBlocklists
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (numLockEnabled) {
                                showVerifyForClear = true
                            } else {
                                showClearDataDialog = true
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Clear All DNS Data",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Remove all query logs and statistics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ─── Dialogs ────────────────────────────────────────────────

    if (showDnsProviderDialog) {
        val providers = listOf(
            "cloudflare" to "Cloudflare (1.1.1.1)",
            "google" to "Google (8.8.8.8)",
            "quad9" to "Quad9 (9.9.9.9)",
            "custom" to "Custom"
        )
        AlertDialog(
            onDismissRequest = { showDnsProviderDialog = false },
            title = { Text("DNS Provider") },
            text = {
                Column {
                    providers.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDnsProvider(key)
                                    showDnsProviderDialog = false
                                    if (key == "custom") showCustomDnsDialog = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dnsProvider == key,
                                onClick = {
                                    viewModel.setDnsProvider(key)
                                    showDnsProviderDialog = false
                                    if (key == "custom") showCustomDnsDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsProviderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockModeDialog) {
        val modes = listOf(
            "nxdomain" to "NXDOMAIN — Domain does not exist (recommended)",
            "zero_ip" to "0.0.0.0 / :: — Null route",
            "localhost" to "127.0.0.1 — Loopback"
        )
        AlertDialog(
            onDismissRequest = { showBlockModeDialog = false },
            title = { Text("Block Mode") },
            text = {
                Column {
                    modes.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setBlockMode(key)
                                    showBlockModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = blockMode == key,
                                onClick = {
                                    viewModel.setBlockMode(key)
                                    showBlockModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockModeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomDnsDialog) {
        var primary by remember { mutableStateOf(customDnsPrimary) }
        var secondary by remember { mutableStateOf(customDnsSecondary) }
        AlertDialog(
            onDismissRequest = { showCustomDnsDialog = false },
            title = { Text("Custom DNS Servers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = primary,
                        onValueChange = { primary = it },
                        label = { Text("Primary DNS") },
                        placeholder = { Text("e.g. 1.1.1.1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text("Secondary DNS") },
                        placeholder = { Text("e.g. 1.0.0.1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomDns(primary.trim(), secondary.trim())
                    showCustomDnsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDnsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Clear all DNS data?") },
            text = {
                Text("This will permanently delete all query logs, statistics, and cached data. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVerifyForClear) {
        NumLockDialog(
            mode = NumLockMode.VERIFY_PIN,
            title = "Enter PIN to Clear DNS Data",
            onDismiss = { showVerifyForClear = false },
            onPinVerified = {
                showVerifyForClear = false
                showClearDataDialog = true
            },
            verifyPin = { pin -> prefs.verifyPin(pin) }
        )
    }
}

// ─── Reusable setting composables ───────────────────────────────────

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsClickableRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
