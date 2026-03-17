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
import com.mobileintelligence.app.ui.viewmodel.InsightsViewModel
import com.mobileintelligence.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = viewModel()
) {
    val weeklyReport by viewModel.weeklyReport.collectAsState()
    val smartInsights by viewModel.smartInsights.collectAsState()
    val predictedUsage by viewModel.predictedUsage.collectAsState()
    val bingeSessions by viewModel.bingeSessions.collectAsState()
    val monthlyData by viewModel.monthlyData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Weekly Report ──
            weeklyReport?.let { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Weekly Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    DateUtils.formatDurationShort(report.totalScreenTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    DateUtils.formatDurationShort(report.avgDailyScreenTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Daily Avg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val changeText = if (report.screenTimeChange >= 0) "+${report.screenTimeChange.toInt()}%"
                                else "${report.screenTimeChange.toInt()}%"
                                val changeColor = if (report.screenTimeChange > 10) ChartColors.danger
                                else if (report.screenTimeChange < -10) ChartColors.good
                                else MaterialTheme.colorScheme.onSurface
                                Text(
                                    changeText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = changeColor
                                )
                                Text(
                                    "vs Last Week",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Scores
                        ScoreIndicator(label = "Addiction Score", score = report.avgAddictionScore)
                        Spacer(modifier = Modifier.height(8.dp))
                        ScoreIndicator(label = "Focus Score", score = report.avgFocusScore)
                        Spacer(modifier = Modifier.height(8.dp))
                        ScoreIndicator(label = "Sleep Disturbance", score = report.avgSleepDisturbance)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Prediction ──
            if (predictedUsage > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Tomorrow's Prediction",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DateUtils.formatDuration(predictedUsage),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Predicted screen time based on 14-day weighted average",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Monthly Trend ──
            if (monthlyData.size > 3) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    WeeklyBarChart(
                        data = monthlyData.sortedBy { it.date }
                            .takeLast(14)
                            .map { it.date to it.totalScreenTimeMs },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Binge Sessions ──
            if (bingeSessions.isNotEmpty()) {
                Text(
                    "Binge Sessions Detected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ChartColors.warning,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                bingeSessions.take(5).forEach { binge ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ChartColors.warning.copy(alpha = 0.08f)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                binge.date,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                DateUtils.formatDuration(binge.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChartColors.warning
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Smart Insights List ──
            if (smartInsights.isNotEmpty()) {
                Text(
                    "Smart Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                smartInsights.forEach { insight ->
                    InsightCard(
                        message = insight.message,
                        category = insight.category.name,
                        severity = insight.severity.name
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── Weekly Report Insights ──
            weeklyReport?.let { report ->
                if (report.insights.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Weekly Insights",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    report.insights.forEach { insight ->
                        InsightCard(
                            message = insight,
                            category = "WEEKLY",
                            severity = "INFO"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
