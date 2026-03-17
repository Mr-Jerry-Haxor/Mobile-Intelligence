package com.mobileintelligence.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.IntelligenceConsoleViewModel

/**
 * Intelligence Console — the "brain" dashboard.
 *
 * Shows three core scores as animated rings, detected patterns
 * as alert cards, correlation insights, and engine health status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceConsoleScreen(
    viewModel: IntelligenceConsoleViewModel = viewModel(),
    onNavigateToHeatmap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val addictionIndex by viewModel.addictionIndex.collectAsState()
    val focusScore by viewModel.focusScore.collectAsState()
    val privacyExposure by viewModel.privacyExposure.collectAsState()
    val overallHealth by viewModel.overallHealth.collectAsState()
    val detectedPatterns by viewModel.detectedPatterns.collectAsState()
    val insights by viewModel.correlationInsights.collectAsState()
    val engineStatuses by viewModel.engineStatuses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val engineConnected by viewModel.engineConnected.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intelligence Console", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Overall Health: ${overallHealth.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = scoreColor(overallHealth, inverted = false)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHeatmap) {
                        Icon(Icons.Default.GridView, contentDescription = "Heatmap")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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

            // ── Loading / Connection Status ─────────────────

            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Connecting to Intelligence Engine…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (!engineConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Intelligence Engine not available. Tracking service may not be running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Three Score Rings ────────────────────────────

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreRing(
                    label = "Addiction",
                    subtitle = "Index",
                    value = addictionIndex,
                    ringColor = scoreColor(addictionIndex, inverted = true),
                    icon = Icons.Default.Warning,
                    modifier = Modifier.weight(1f)
                )
                ScoreRing(
                    label = "Focus",
                    subtitle = "Stability",
                    value = focusScore,
                    ringColor = scoreColor(focusScore, inverted = false),
                    icon = Icons.Default.Psychology,
                    modifier = Modifier.weight(1f)
                )
                ScoreRing(
                    label = "Privacy",
                    subtitle = "Exposure",
                    value = privacyExposure,
                    ringColor = scoreColor(privacyExposure, inverted = true),
                    icon = Icons.Default.Shield,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Overall Health Bar ──────────────────────────

            HealthBar(overallHealth)

            Spacer(modifier = Modifier.height(20.dp))

            // ── Detected Patterns ───────────────────────────

            if (detectedPatterns.isNotEmpty()) {
                SectionHeader(text = "Active Alerts", icon = Icons.Default.NotificationsActive)

                detectedPatterns.take(5).forEach { pattern ->
                    PatternAlertCard(pattern)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Correlation Insights ────────────────────────

            if (insights.isNotEmpty()) {
                SectionHeader(text = "Insights", icon = Icons.Default.Lightbulb)

                insights.take(5).forEach { insight ->
                    InsightCard(insight)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Engine Health Status ────────────────────────

            if (engineStatuses.isNotEmpty()) {
                SectionHeader(text = "Engine Status", icon = Icons.Default.Memory)

                engineStatuses.forEach { engine ->
                    EngineStatusCard(engine)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Score Ring ──────────────────────────────────────────────────

@Composable
private fun ScoreRing(
    label: String,
    subtitle: String,
    value: Float,
    ringColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = (value / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1200),
        label = "score-ring"
    )
    val animColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = tween(600),
        label = "ring-color"
    )

    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
                // Background ring
                drawArc(
                    color = animColor.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                // Progress ring
                drawArc(
                    color = animColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animProgress,
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = animColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${value.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = animColor
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Health Bar ──────────────────────────────────────────────────

@Composable
private fun HealthBar(health: Float) {
    val progress by animateFloatAsState(
        targetValue = (health / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "health-bar"
    )
    val color = scoreColor(health, inverted = false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Overall Digital Health",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${health.toInt()}/100",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = healthLabel(health),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Pattern Alert Card ──────────────────────────────────────────

@Composable
private fun PatternAlertCard(alert: IntelligenceConsoleViewModel.PatternAlert) {
    val severityColor = when (alert.severity) {
        "CRITICAL" -> ChartColors.danger
        "HIGH" -> Color(0xFFFF7043)
        "MEDIUM" -> ChartColors.warning
        else -> ChartColors.good
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(severityColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.type.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = severityColor
                )
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = severityColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = alert.severity,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
            }
        }
    }
}

// ── Insight Card ────────────────────────────────────────────────

@Composable
private fun InsightCard(insight: IntelligenceConsoleViewModel.InsightItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall
                )
                if (insight.appPackage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "App: ${insight.appPackage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Engine Status Card ──────────────────────────────────────────

@Composable
private fun EngineStatusCard(status: IntelligenceConsoleViewModel.EngineStatusItem) {
    val stateColor = if (status.isHealthy) ChartColors.good else ChartColors.danger

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatUptime(status.uptimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = stateColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = status.state,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = stateColor
                    )
                }
            }
        }
    }
}

// ── Section Header ──────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────

/**
 * Color from green → yellow → red.
 * [inverted] = true means high value = bad (e.g. addiction, privacy exposure).
 * [inverted] = false means high value = good (e.g. focus, health).
 */
@Composable
private fun scoreColor(value: Float, inverted: Boolean): Color {
    val v = value.coerceIn(0f, 100f)
    val pct = if (inverted) v / 100f else 1f - v / 100f
    return when {
        pct < 0.33f -> ChartColors.good
        pct < 0.66f -> ChartColors.warning
        else -> ChartColors.danger
    }
}

private fun healthLabel(health: Float): String = when {
    health >= 80 -> "Excellent — maintain your healthy digital habits"
    health >= 60 -> "Good — minor improvements possible"
    health >= 40 -> "Fair — some concerning patterns detected"
    health >= 20 -> "Poor — significant changes recommended"
    else -> "Critical — immediate attention needed"
}

private fun formatUptime(ms: Long): String {
    val sec = ms / 1000
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    }
}
