package com.mobileintelligence.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.components.*
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.DashboardViewModel
import com.mobileintelligence.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val todaySummary by viewModel.todaySummary.collectAsState()
    val todayScreenTime by viewModel.todayScreenTime.collectAsState()
    val todayUnlocks by viewModel.todayUnlocks.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val weeklyData by viewModel.weeklyData.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val predictedTomorrow by viewModel.predictedTomorrow.collectAsState()
    val hourlyData by viewModel.hourlyData.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mobile Intelligence", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isServiceRunning) "Monitoring active" else "Monitoring paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isServiceRunning) ChartColors.good
                            else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Usage Ring ──────
            UsageRing(
                currentMs = todayScreenTime,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // ── Quick Stats ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Unlocks",
                    value = "$todayUnlocks",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Sessions",
                    value = "$todaySessions",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Avg Session",
                    value = DateUtils.formatDurationShort(
                        todaySummary?.averageSessionMs ?: 0
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Behavioral Scores ──
            todaySummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Behavioral Scores",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ScoreIndicator(
                            label = "Addiction Score",
                            score = summary.addictionScore
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScoreIndicator(
                            label = "Focus Score",
                            score = summary.focusScore
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScoreIndicator(
                            label = "Sleep Disturbance",
                            score = summary.sleepDisturbanceIndex
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Hourly Chart ──
            if (hourlyData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    HourlyBarChart(
                        data = hourlyData.map { it.hour to it.screenTimeMs },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Weekly Chart ──
            if (weeklyData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    WeeklyBarChart(
                        data = weeklyData.sortedBy { it.date }
                            .map { it.date to it.totalScreenTimeMs },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Top Apps ──
            if (topApps.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Top Apps Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val maxTime = topApps.firstOrNull()?.totalTimeMs ?: 1L
                        topApps.take(5).forEachIndexed { index, app ->
                            AppUsageBar(
                                appName = app.appName,
                                timeMs = app.totalTimeMs,
                                maxTimeMs = maxTime,
                                color = ChartColors.appColors[index % ChartColors.appColors.size],
                                sessions = app.sessionCount
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Prediction ──
            if (predictedTomorrow > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Predicted Tomorrow",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = DateUtils.formatDuration(predictedTomorrow),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Based on your recent patterns",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Insights ──
            if (insights.isNotEmpty()) {
                Text(
                    "Smart Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                insights.forEach { insight ->
                    InsightCard(
                        message = insight.message,
                        category = insight.category.name,
                        severity = insight.severity.name
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
