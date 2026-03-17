package com.mobileintelligence.app.dns.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.dns.ui.viewmodel.DnsStatsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsStatsScreen(
    viewModel: DnsStatsViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val dailyStats by viewModel.dailyStats.collectAsState()
    val topBlockedDomains by viewModel.topBlockedDomains.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val blocklistInfo by viewModel.blocklistInfo.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("DNS Statistics") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Daily Trend ────────────────────────────────────
            if (dailyStats.isNotEmpty()) {
                item {
                    Text(
                        "Daily Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dailyStats.takeLast(7).forEach { stat ->
                                DailyStatRow(
                                    date = stat.dateStr,
                                    totalQueries = stat.totalQueries,
                                    blockedQueries = stat.blockedQueries,
                                    maxQueries = dailyStats.maxOfOrNull { it.totalQueries } ?: 1
                                )
                            }
                        }
                    }
                }
            }

            // ─── Top Trackers ───────────────────────────────────
            item {
                Text(
                    "Top Blocked Trackers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (topBlockedDomains.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No blocked domains today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(topBlockedDomains.take(15)) { domain ->
                    TrackerRow(
                        rank = topBlockedDomains.indexOf(domain) + 1,
                        domain = domain.domain,
                        count = domain.cnt
                    )
                }
            }

            // ─── App Network Behavior ───────────────────────────
            item {
                Text(
                    "App Network Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (topApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No app data yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(topApps.take(10)) { app ->
                    AppBehaviorRow(
                        appLabel = viewModel.getAppLabel(app.appPackage),
                        totalQueries = app.cnt,
                        blockedQueries = app.blockedCnt,
                        maxQueries = topApps.maxOfOrNull { it.cnt } ?: 1
                    )
                }
            }

            // ─── Blocklist Info ─────────────────────────────────
            item {
                Text(
                    "Blocklists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(blocklistInfo) { info ->
                val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                BlocklistInfoRow(
                    category = info.category.replaceFirstChar { it.uppercase() },
                    domainCount = info.domainCount,
                    lastUpdated = if (info.lastUpdated > 0) dateFormat.format(Date(info.lastUpdated)) else "Never"
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DailyStatRow(
    date: String,
    totalQueries: Int,
    blockedQueries: Int,
    maxQueries: Int
) {
    val fraction = if (maxQueries > 0) totalQueries.toFloat() / maxQueries else 0f
    val blockedFraction = if (totalQueries > 0) blockedQueries.toFloat() / totalQueries else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date.takeLast(5), // "MM-dd"
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            LinearProgressIndicator(
                progress = { fraction * blockedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$totalQueries",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
fun TrackerRow(rank: Int, domain: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(32.dp)
            )
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun AppBehaviorRow(
    appLabel: String,
    totalQueries: Int,
    blockedQueries: Int,
    maxQueries: Int
) {
    val fraction = if (maxQueries > 0) totalQueries.toFloat() / maxQueries else 0f
    val blockedFraction = if (totalQueries > 0) blockedQueries.toFloat() / totalQueries else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$totalQueries queries",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (blockedFraction > 0.5f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (blockedQueries > 0) {
                Text(
                    text = "$blockedQueries blocked (${(blockedFraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun BlocklistInfoRow(category: String, domainCount: Int, lastUpdated: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Updated: $lastUpdated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$domainCount domains",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
