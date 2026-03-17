package com.mobileintelligence.app.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.receiver.AppProtectionAdmin
import com.mobileintelligence.app.ui.components.NumLockDialog
import com.mobileintelligence.app.ui.components.NumLockMode
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val totalRecords by viewModel.totalRecords.collectAsState()
    val oldestDate by viewModel.oldestDate.collectAsState()
    val numLockEnabled by viewModel.isNumLockEnabled.collectAsState()
    val context = LocalContext.current
    var showWipeDialog by remember { mutableStateOf(false) }

    // NumLock dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var showVerifyForWipe by remember { mutableStateOf(false) }
    var showVerifyForTracking by remember { mutableStateOf(false) }

    // Device Admin
    val adminComponent = ComponentName(context, AppProtectionAdmin::class.java)
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    var isDeviceAdmin by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDeviceAdmin = dpm.isAdminActive(adminComponent) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Monitoring ──
            Text(
                "Monitoring",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("Enable Tracking") },
                        supportingContent = {
                            Text(if (isTrackingEnabled) "Monitoring is active" else "Monitoring is paused")
                        },
                        leadingContent = {
                            Icon(
                                if (isTrackingEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (isTrackingEnabled) ChartColors.good else MaterialTheme.colorScheme.error
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isTrackingEnabled,
                                onCheckedChange = { newValue ->
                                    if (!newValue && numLockEnabled) {
                                        // Require PIN to disable tracking
                                        showVerifyForTracking = true
                                    } else {
                                        viewModel.setTrackingEnabled(newValue)
                                    }
                                }
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("Usage Access Permission") },
                        supportingContent = { Text("Required for app tracking") },
                        leadingContent = {
                            Icon(Icons.Default.Security, contentDescription = null)
                        },
                        trailingContent = {
                            FilledTonalButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }) {
                                Text("Grant")
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("Disable Battery Optimization") },
                        supportingContent = { Text("Helps keep monitoring running") },
                        leadingContent = {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                        },
                        trailingContent = {
                            FilledTonalButton(onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }) {
                                Text("Open")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Security ──
            Text(
                "Security",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("NumLock PIN") },
                        supportingContent = {
                            Text(
                                if (numLockEnabled)
                                    "PIN required for destructive actions"
                                else
                                    "Protect data deletion & app uninstall"
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (numLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (numLockEnabled) ChartColors.good else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            if (numLockEnabled) {
                                Row {
                                    FilledTonalButton(onClick = { showChangePinDialog = true }) {
                                        Text("Change")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilledTonalButton(
                                        onClick = { showRemovePinDialog = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            } else {
                                FilledTonalButton(onClick = { showSetPinDialog = true }) {
                                    Text("Set PIN")
                                }
                            }
                        }
                    )

                    if (numLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            headlineContent = { Text("Uninstall Protection") },
                            supportingContent = {
                                Text(
                                    if (isDeviceAdmin)
                                        "App cannot be uninstalled without PIN"
                                    else
                                        "Enable to prevent app removal"
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isDeviceAdmin) ChartColors.good else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = isDeviceAdmin,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                                putExtra(
                                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                    "Enable to prevent unauthorized uninstallation, force-stop, or data clearing of this app."
                                                )
                                            }
                                            adminLauncher.launch(intent)
                                        } else {
                                            dpm.removeActiveAdmin(adminComponent)
                                            isDeviceAdmin = false
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Appearance ──
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    val themes = listOf(
                        "auto" to "Auto (Wallpaper)",
                        "amoled" to "AMOLED Black",
                        "monochrome" to "Monochrome",
                        "pastel" to "Pastel Analytics"
                    )
                    themes.forEachIndexed { index, (key, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = themeMode == key,
                                    onClick = { viewModel.setThemeMode(key) }
                                )
                            },
                            modifier = Modifier.padding(start = 0.dp)
                        )
                        if (index < themes.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Data ──
            Text(
                "Data & Privacy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("Total Records") },
                        supportingContent = {
                            Text("$totalRecords screen sessions recorded")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("Data Since") },
                        supportingContent = {
                            Text(oldestDate ?: "No data yet")
                        },
                        leadingContent = {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("Data Retention") },
                        supportingContent = { Text("3 years — auto purge at midnight") },
                        leadingContent = {
                            Icon(Icons.Default.AutoDelete, contentDescription = null)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = {
                            Text(
                                "Wipe All Data",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = { Text("Permanently delete all tracking data") },
                        leadingContent = {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = {
                                    if (numLockEnabled) {
                                        showVerifyForWipe = true
                                    } else {
                                        showWipeDialog = true
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text("Wipe")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── About ──
            Text(
                "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ListItem(
                        headlineContent = { Text("Mobile Intelligence") },
                        supportingContent = { Text("Version 1.0.0") },
                        leadingContent = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Privacy") },
                        supportingContent = { Text("All data is stored locally. No internet required.") },
                        leadingContent = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Wipe confirmation dialog
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe All Data?") },
            text = { Text("This will permanently delete all screen sessions, app usage data, and analytics. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.wipeAllData()
                        showWipeDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── NumLock Dialogs ──────────────────────────────────────────

    if (showSetPinDialog) {
        NumLockDialog(
            mode = NumLockMode.SET_PIN,
            onDismiss = { showSetPinDialog = false },
            onPinVerified = { pin ->
                viewModel.setNumLockPin(pin)
                showSetPinDialog = false
                Toast.makeText(context, "NumLock PIN set", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showChangePinDialog) {
        NumLockDialog(
            mode = NumLockMode.CHANGE_PIN,
            onDismiss = { showChangePinDialog = false },
            onPinVerified = { pin ->
                viewModel.setNumLockPin(pin)
                showChangePinDialog = false
                Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
            },
            verifyPin = { viewModel.verifyPin(it) }
        )
    }

    if (showRemovePinDialog) {
        NumLockDialog(
            mode = NumLockMode.VERIFY_PIN,
            title = "Verify PIN to Remove",
            onDismiss = { showRemovePinDialog = false },
            onPinVerified = {
                // Disable device admin if active before removing PIN
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent)
                    isDeviceAdmin = false
                }
                viewModel.removeNumLock()
                showRemovePinDialog = false
                Toast.makeText(context, "NumLock PIN removed", Toast.LENGTH_SHORT).show()
            },
            verifyPin = { viewModel.verifyPin(it) }
        )
    }

    if (showVerifyForWipe) {
        NumLockDialog(
            mode = NumLockMode.VERIFY_PIN,
            title = "Enter PIN to Wipe Data",
            onDismiss = { showVerifyForWipe = false },
            onPinVerified = {
                showVerifyForWipe = false
                showWipeDialog = true
            },
            verifyPin = { viewModel.verifyPin(it) }
        )
    }

    if (showVerifyForTracking) {
        NumLockDialog(
            mode = NumLockMode.VERIFY_PIN,
            title = "Enter PIN to Disable Tracking",
            onDismiss = { showVerifyForTracking = false },
            onPinVerified = {
                showVerifyForTracking = false
                viewModel.setTrackingEnabled(false)
            },
            verifyPin = { viewModel.verifyPin(it) }
        )
    }
}
