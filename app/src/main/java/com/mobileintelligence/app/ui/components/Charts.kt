package com.mobileintelligence.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.util.DateUtils

// ── Usage Ring ────────────────────────────────────────────────────

@Composable
fun UsageRing(
    currentMs: Long,
    targetMs: Long = 4 * 3600_000L, // 4 hour target
    modifier: Modifier = Modifier,
    label: String = "Screen Time"
) {
    val progress = if (targetMs > 0) (currentMs.toFloat() / targetMs).coerceIn(0f, 1.5f) else 0f
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "ring"
    )

    val ringColor = when {
        progress > 1.2f -> ChartColors.danger
        progress > 0.8f -> ChartColors.warning
        else -> ChartColors.good
    }

    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(width = 16f, cap = StrokeCap.Round)
            val sweepAngle = (animProgress * 360f).coerceAtMost(360f)

            // Background ring
            drawArc(
                color = ringColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )

            // Progress ring
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = DateUtils.formatDurationShort(currentMs),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Stat Card ────────────────────────────────────────────────────

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Hourly Bar Chart ─────────────────────────────────────────────

@Composable
fun HourlyBarChart(
    data: List<Pair<Int, Long>>, // hour to ms
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxMs = data.maxOfOrNull { it.second } ?: 1L

    Column(modifier = modifier) {
        Text(
            text = "Hourly Activity",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (hour in 0..23) {
                val ms = data.find { it.first == hour }?.second ?: 0L
                val fraction = if (maxMs > 0) (ms.toFloat() / maxMs).coerceIn(0f, 1f) else 0f

                val barColor = when {
                    hour in 23..23 || hour in 0..4 -> ChartColors.nightUsage
                    else -> ChartColors.screenOn
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (ms > 0) barColor else barColor.copy(alpha = 0.1f))
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("18", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("24", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Weekly Bar Chart ─────────────────────────────────────────────

@Composable
fun WeeklyBarChart(
    data: List<Pair<String, Long>>, // date to ms
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxMs = data.maxOfOrNull { it.second } ?: 1L

    Column(modifier = modifier) {
        Text(
            text = "This Week",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (date, ms) ->
                val fraction = if (maxMs > 0) (ms.toFloat() / maxMs).coerceIn(0f, 1f) else 0f
                val isToday = date == DateUtils.today()

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (date, _) ->
                Text(
                    text = DateUtils.getDayOfWeek(date).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == DateUtils.today()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Score Indicator ──────────────────────────────────────────────

@Composable
fun ScoreIndicator(
    label: String,
    score: Float,
    maxScore: Float = 100f,
    modifier: Modifier = Modifier
) {
    val fraction = (score / maxScore).coerceIn(0f, 1f)
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800),
        label = "score"
    )

    val color = when {
        score > 70 -> ChartColors.danger
        score > 40 -> ChartColors.warning
        else -> ChartColors.good
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${score.toInt()}/100",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

// ── App Usage Bar ────────────────────────────────────────────────

@Composable
fun AppUsageBar(
    appName: String,
    timeMs: Long,
    maxTimeMs: Long,
    color: Color,
    sessions: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxTimeMs > 0) (timeMs.toFloat() / maxTimeMs).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${DateUtils.formatDurationShort(timeMs)} · $sessions sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

// ── Insight Card ─────────────────────────────────────────────────

@Composable
fun InsightCard(
    message: String,
    category: String,
    severity: String,
    modifier: Modifier = Modifier
) {
    val colors = when (severity) {
        "POSITIVE" -> ChartColors.good
        "WARNING" -> ChartColors.warning
        "ALERT" -> ChartColors.danger
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Session Timeline Item ────────────────────────────────────────

@Composable
fun SessionTimelineItem(
    startTime: Long,
    endTime: Long?,
    duration: Long,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time indicator
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(60.dp)
        ) {
            Text(
                text = DateUtils.formatTime(startTime),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            endTime?.let {
                Text(
                    text = DateUtils.formatTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Timeline dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (endTime == null) ChartColors.good
                    else MaterialTheme.colorScheme.primary
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Session info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DateUtils.formatDuration(duration),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isUnlocked) "Unlocked session" else "Screen on",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
