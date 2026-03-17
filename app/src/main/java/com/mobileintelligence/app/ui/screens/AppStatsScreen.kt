package com.mobileintelligence.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.components.AppUsageBar
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.AppStatsViewModel
import com.mobileintelligence.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStatsScreen(
    viewModel: AppStatsViewModel = viewModel()
) {
    val period by viewModel.period.collectAsState()
    val appRankings by viewModel.appRankings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Statistics", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Period selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("today" to "Today", "week" to "Week", "month" to "Month").forEach { (key, label) ->
                    FilterChip(
                        selected = period == key,
                        onClick = { viewModel.setPeriod(key) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Summary
            if (appRankings.isNotEmpty()) {
                val totalTime = appRankings.sumOf { it.totalTimeMs }
                val totalSessions = appRankings.sumOf { it.sessionCount }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = DateUtils.formatDurationShort(totalTime),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Total Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${appRankings.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalSessions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()
            }

            // App list
            if (appRankings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No app usage data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxTime = appRankings.firstOrNull()?.totalTimeMs ?: 1L

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(appRankings) { index, app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(30.dp)
                            )
                            AppUsageBar(
                                appName = app.appName,
                                timeMs = app.totalTimeMs,
                                maxTimeMs = maxTime,
                                color = ChartColors.appColors[index % ChartColors.appColors.size],
                                sessions = app.sessionCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
