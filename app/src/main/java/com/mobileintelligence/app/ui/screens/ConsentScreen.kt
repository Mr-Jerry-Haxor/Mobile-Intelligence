package com.mobileintelligence.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobileintelligence.app.ui.theme.ChartColors

/**
 * Privacy Consent & Disclosure screen.
 *
 * Shown on first launch. Explains what data is collected, how it's stored
 * (all local, no cloud), and collects explicit user consent for:
 *   1. Screen activity monitoring (UsageStats permission)
 *   2. DNS firewall / VPN service
 *   3. Local data retention
 *
 * No data collection starts until consent is granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    onConsentGranted: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    var screenMonitoring by remember { mutableStateOf(false) }
    var dnsFirewall by remember { mutableStateOf(false) }
    var dataRetention by remember { mutableStateOf(false) }
    var acknowledgedPrivacy by remember { mutableStateOf(false) }

    val allAccepted = screenMonitoring && dnsFirewall && dataRetention && acknowledgedPrivacy

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Privacy & Consent", fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Header ──────────────────────────────────────

            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your Privacy Matters",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mobile Intelligence is a fully local, on-device intelligence engine. " +
                        "No data ever leaves your phone — no cloud, no servers, no third-party " +
                        "analytics. Everything stays under your control.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Data Collection Disclosure ───────────────────

            Text(
                text = "What We Monitor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Screen Monitoring
            ConsentToggleCard(
                icon = Icons.Default.Visibility,
                title = "Screen Activity Monitoring",
                description = "Records screen on/off events, unlock patterns, and app usage " +
                        "sessions. Uses Android's UsageStats API. This enables behavioral " +
                        "insights like Digital Addiction Index and Focus Stability Score.",
                dataStored = "Session timestamps, app names, durations, unlock events",
                retention = "Raw data: 30 days • Hourly aggregates: 180 days • Daily summaries: 3 years",
                checked = screenMonitoring,
                onCheckedChange = { screenMonitoring = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. DNS Firewall
            ConsentToggleCard(
                icon = Icons.Default.Security,
                title = "DNS Firewall & Network Protection",
                description = "Runs a local VPN service to intercept DNS queries on your device. " +
                        "Blocks ads, trackers, and malware domains locally. No network traffic " +
                        "is sent externally — DNS is resolved locally or through your configured " +
                        "upstream resolver.",
                dataStored = "DNS queries, blocked domains, per-app network activity",
                retention = "Raw queries: 14 days • Hourly stats: 90 days • Daily stats: 1 year",
                checked = dnsFirewall,
                onCheckedChange = { dnsFirewall = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Data Retention
            ConsentToggleCard(
                icon = Icons.Default.Storage,
                title = "Local Data Storage & Retention",
                description = "All collected data is stored exclusively in local SQLite databases " +
                        "on your device. Older raw data is automatically rolled up into anonymized " +
                        "aggregates and eventually purged. You can clear all data at any time " +
                        "from Settings.",
                dataStored = "Behavioral scores, pattern detections, correlation insights",
                retention = "Follows tiered policy: raw → hourly → daily. Configurable in Settings.",
                checked = dataRetention,
                onCheckedChange = { dataRetention = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Privacy Acknowledgement ─────────────────────

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = acknowledgedPrivacy,
                        onCheckedChange = { acknowledgedPrivacy = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I understand that all data is stored locally on this device, " +
                                "that I can revoke permissions and delete all data at any time " +
                                "from the app's Settings screen, and that no information is " +
                                "transmitted externally.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Action Buttons ──────────────────────────────

            Button(
                onClick = onConsentGranted,
                enabled = allAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Accept & Continue", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can enable individual features later from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Consent Toggle Card ─────────────────────────────────────────

@Composable
private fun ConsentToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    dataStored: String,
    retention: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) ChartColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Data stored
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.DataUsage,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Data: $dataStored",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Retention
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Retention: $retention",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
