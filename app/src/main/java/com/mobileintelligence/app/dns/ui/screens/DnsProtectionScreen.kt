package com.mobileintelligence.app.dns.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import com.mobileintelligence.app.dns.ui.viewmodel.DnsProtectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsProtectionScreen(
    viewModel: DnsProtectionViewModel = viewModel(),
    onNavigateToQueryLog: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRunning by viewModel.isVpnRunning.collectAsState()
    val todayQueries by viewModel.todayQueries.collectAsState()
    val todayBlocked by viewModel.todayBlocked.collectAsState()
    val todayCached by viewModel.todayCached.collectAsState()
    val avgResponseTime by viewModel.avgResponseTime.collectAsState()
    val blockedPercentage by viewModel.blockedPercentage.collectAsState()
    val topBlockedDomains by viewModel.topBlockedDomains.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val lifetimeQueries by viewModel.totalQueriesLifetime.collectAsState()
    val lifetimeBlocked by viewModel.totalBlockedLifetime.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            LocalDnsVpnService.start(context)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ─── Header ─────────────────────────────────────────────
        item {
            Text(
                text = "Network Protection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // ─── VPN Toggle Card ────────────────────────────────────
        item {
            ProtectionStatusCard(
                isRunning = isRunning,
                onToggle = {
                    if (isRunning) {
                        LocalDnsVpnService.stop(context)
                    } else {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnPermissionLauncher.launch(prepareIntent)
                        } else {
                            LocalDnsVpnService.start(context)
                        }
                    }
                }
            )
        }

        // ─── Today's Stats Grid ────────────────────────────────
        item {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DnsStatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Queries",
                    value = formatNumber(todayQueries),
                    icon = Icons.Outlined.Dns,
                    color = MaterialTheme.colorScheme.primary
                )
                DnsStatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Blocked",
                    value = formatNumber(todayBlocked),
                    icon = Icons.Outlined.Block,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DnsStatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "% Blocked",
                    value = "${blockedPercentage.toInt()}%",
                    icon = Icons.Outlined.PieChart,
                    color = MaterialTheme.colorScheme.tertiary
                )
                DnsStatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Cached",
                    value = formatNumber(todayCached),
                    icon = Icons.Outlined.Speed,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // ─── Avg Response Time ──────────────────────────────────
        item {
            val avgMs = avgResponseTime ?: 0.0
            DnsInfoRow(
                label = "Avg Response Time",
                value = "${String.format("%.1f", avgMs)} ms",
                icon = Icons.Outlined.Timer
            )
        }

        // ─── Top Blocked Domains ────────────────────────────────
        if (topBlockedDomains.isNotEmpty()) {
            item {
                Text(
                    text = "Top Blocked Domains",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(topBlockedDomains.take(5)) { domain ->
                DomainRow(
                    domain = domain.domain,
                    count = domain.cnt,
                    isBlocked = true
                )
            }
        }

        // ─── Top Apps ───────────────────────────────────────────
        if (topApps.isNotEmpty()) {
            item {
                Text(
                    text = "Most Active Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(topApps.take(5)) { app ->
                AppNetworkRow(
                    appLabel = viewModel.getAppLabel(app.appPackage),
                    packageName = app.appPackage,
                    totalQueries = app.cnt,
                    blockedQueries = app.blockedCnt
                )
            }
        }

        // ─── Quick Actions ──────────────────────────────────────
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    label = "Query Log",
                    icon = @Suppress("DEPRECATION") Icons.Outlined.List,
                    onClick = onNavigateToQueryLog
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    label = "Statistics",
                    icon = Icons.Outlined.BarChart,
                    onClick = onNavigateToStats
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    onClick = onNavigateToSettings
                )
            }
        }

        // ─── Lifetime Stats ─────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Lifetime",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Queries", style = MaterialTheme.typography.bodySmall)
                            Text(
                                formatNumber(lifetimeQueries.toInt()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Blocked", style = MaterialTheme.typography.bodySmall)
                            Text(
                                formatNumber(lifetimeBlocked.toInt()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun ProtectionStatusCard(isRunning: Boolean, onToggle: () -> Unit) {
    val containerColor = if (isRunning) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isRunning) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Shield else Icons.Outlined.ShieldMoon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isRunning) "Protection Active" else "Protection Off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = if (isRunning) "DNS filtering is running" else "Tap to enable protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            Switch(
                checked = isRunning,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = contentColor,
                    checkedTrackColor = contentColor.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
fun DnsStatMiniCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DnsInfoRow(label: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DomainRow(domain: String, count: Int, isBlocked: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatNumber(count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppNetworkRow(
    appLabel: String,
    packageName: String,
    totalQueries: Int,
    blockedQueries: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatNumber(totalQueries),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (blockedQueries > 0) {
                    Text(
                        text = "${formatNumber(blockedQueries)} blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private fun formatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> "${String.format("%.1f", num / 1_000_000f)}M"
        num >= 1_000 -> "${String.format("%.1f", num / 1_000f)}K"
        else -> num.toString()
    }
}
