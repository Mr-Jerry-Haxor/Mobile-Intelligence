package com.mobileintelligence.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.HeatmapViewModel
import com.mobileintelligence.app.util.DateUtils

/**
 * Heatmap screen — visualizes a 7-day × 24-hour grid of usage
 * intensity with switchable overlays (Screen Time, DNS, Privacy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    viewModel: HeatmapViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val screenHeatmap by viewModel.screenHeatmap.collectAsState()
    val dnsHeatmap by viewModel.dnsHeatmap.collectAsState()
    val overlayMode by viewModel.overlayMode.collectAsState()
    val dayLabels by viewModel.dayLabels.collectAsState()
    val peakHour by viewModel.peakHour.collectAsState()
    val weeklyTotal by viewModel.weeklyTotal.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val activeGrid = when (overlayMode) {
        HeatmapViewModel.HeatmapOverlay.SCREEN_TIME -> screenHeatmap
        HeatmapViewModel.HeatmapOverlay.DNS_QUERIES -> dnsHeatmap
        HeatmapViewModel.HeatmapOverlay.PRIVACY_EXPOSURE -> dnsHeatmap // reuse until enriched
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Heatmap", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHeatmapData() }) {
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Loading Indicator ───────────────────────────

            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Loading heatmap data…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Overlay Selector ────────────────────────────

            OverlaySelector(
                current = overlayMode,
                onSelect = { viewModel.setOverlay(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Summary Stats ───────────────────────────────

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(
                    label = "Weekly Total",
                    value = DateUtils.formatDurationShort(weeklyTotal),
                    icon = Icons.Default.AccessTime,
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "Peak Hour",
                    value = if (peakHour >= 0) "${peakHour}:00" else "—",
                    icon = @Suppress("DEPRECATION") Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Heatmap Grid ────────────────────────────────

            Text(
                text = overlayTitle(overlayMode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HeatmapGrid(
                grid = activeGrid,
                dayLabels = dayLabels,
                colorScheme = overlayColorScheme(overlayMode)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            HeatmapLegend(colorScheme = overlayColorScheme(overlayMode))

            Spacer(modifier = Modifier.height(20.dp))

            // ── Hour breakdown for the busiest day ─────────

            val busiestDayIdx = activeGrid.indices.maxByOrNull { d ->
                activeGrid[d].sum()
            } ?: 0
            if (activeGrid.isNotEmpty()) {
                val dayLabel = dayLabels.getOrElse(busiestDayIdx) { "Day" }
                Text(
                    text = "Hour-by-Hour ($dayLabel — busiest day)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HourlyBarChart(
                    hours = activeGrid.getOrElse(busiestDayIdx) { List(24) { 0f } },
                    colorScheme = overlayColorScheme(overlayMode)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Overlay Selector ────────────────────────────────────────────

@Composable
private fun OverlaySelector(
    current: HeatmapViewModel.HeatmapOverlay,
    onSelect: (HeatmapViewModel.HeatmapOverlay) -> Unit
) {
    val modes = HeatmapViewModel.HeatmapOverlay.values()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { mode ->
            val selected = mode == current
            FilterChip(
                selected = selected,
                onClick = { onSelect(mode) },
                label = { Text(overlayLabel(mode), fontSize = 12.sp) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Heatmap Grid Canvas ─────────────────────────────────────────

@Composable
private fun HeatmapGrid(
    grid: List<List<Float>>,
    dayLabels: List<String>,
    colorScheme: HeatmapColorScheme
) {
    if (grid.isEmpty() || grid[0].isEmpty()) return

    val maxValue = (grid.flatten().maxOrNull() ?: 1f).coerceAtLeast(1f)
    val rows = grid.size  // 7 days
    val cols = grid[0].size  // 24 hours

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Hour labels across top
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(32.dp)) // day label space
                for (h in 0 until cols step 3) {
                    Text(
                        text = "${h}",
                        modifier = Modifier.weight(3f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Start,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grid rows
            for (dayIdx in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day label
                    Text(
                        text = dayLabels.getOrElse(dayIdx) { "" },
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Cells
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 1.dp)
                    ) {
                        val cellWidth = size.width / cols
                        val cellHeight = size.height

                        for (hourIdx in 0 until cols) {
                            val intensity = (grid[dayIdx][hourIdx] / maxValue).coerceIn(0f, 1f)
                            val color = interpolateColor(colorScheme, intensity)
                            drawRect(
                                color = color,
                                topLeft = Offset(hourIdx * cellWidth, 0f),
                                size = Size(cellWidth - 1f, cellHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Hourly Bar Chart ────────────────────────────────────────────

@Composable
private fun HourlyBarChart(
    hours: List<Float>,
    colorScheme: HeatmapColorScheme
) {
    val maxVal = (hours.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val barWidth = size.width / hours.size
                hours.forEachIndexed { idx, value ->
                    val h = (value / maxVal) * size.height
                    val intensity = (value / maxVal).coerceIn(0f, 1f)
                    drawRect(
                        color = interpolateColor(colorScheme, intensity),
                        topLeft = Offset(idx * barWidth, size.height - h),
                        size = Size(barWidth - 2f, h)
                    )
                }
            }
        }
    }
}

// ── Summary Chip ────────────────────────────────────────────────

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Legend ───────────────────────────────────────────────────────

@Composable
private fun HeatmapLegend(colorScheme: HeatmapColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Low", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(4.dp))
        for (i in 0..4) {
            val intensity = i / 4f
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(interpolateColor(colorScheme, intensity))
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("High", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}

// ── Color Helpers ───────────────────────────────────────────────

private data class HeatmapColorScheme(
    val low: Color,
    val mid: Color,
    val high: Color
)

private fun overlayColorScheme(mode: HeatmapViewModel.HeatmapOverlay): HeatmapColorScheme = when (mode) {
    HeatmapViewModel.HeatmapOverlay.SCREEN_TIME -> HeatmapColorScheme(
        low = Color(0xFF1B3A4B),
        mid = Color(0xFF42A5F5),
        high = Color(0xFFE3F2FD)
    )
    HeatmapViewModel.HeatmapOverlay.DNS_QUERIES -> HeatmapColorScheme(
        low = Color(0xFF1B2E1B),
        mid = Color(0xFF66BB6A),
        high = Color(0xFFE8F5E9)
    )
    HeatmapViewModel.HeatmapOverlay.PRIVACY_EXPOSURE -> HeatmapColorScheme(
        low = Color(0xFF2D1B1B),
        mid = Color(0xFFEF5350),
        high = Color(0xFFFFEBEE)
    )
}

private fun interpolateColor(scheme: HeatmapColorScheme, intensity: Float): Color {
    val i = intensity.coerceIn(0f, 1f)
    return if (i < 0.5f) {
        lerpColor(scheme.low, scheme.mid, i * 2f)
    } else {
        lerpColor(scheme.mid, scheme.high, (i - 0.5f) * 2f)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = a.alpha + (b.alpha - a.alpha) * clamped
    )
}

private fun overlayLabel(mode: HeatmapViewModel.HeatmapOverlay): String = when (mode) {
    HeatmapViewModel.HeatmapOverlay.SCREEN_TIME -> "Screen Time"
    HeatmapViewModel.HeatmapOverlay.DNS_QUERIES -> "DNS Queries"
    HeatmapViewModel.HeatmapOverlay.PRIVACY_EXPOSURE -> "Privacy"
}

private fun overlayTitle(mode: HeatmapViewModel.HeatmapOverlay): String = when (mode) {
    HeatmapViewModel.HeatmapOverlay.SCREEN_TIME -> "Screen Time Intensity (minutes/hour)"
    HeatmapViewModel.HeatmapOverlay.DNS_QUERIES -> "DNS Query Volume (queries/hour)"
    HeatmapViewModel.HeatmapOverlay.PRIVACY_EXPOSURE -> "Privacy Exposure Level"
}
