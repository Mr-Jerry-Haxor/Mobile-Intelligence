package com.mobileintelligence.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.engine.features.FocusMode
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.FocusModeViewModel

/**
 * Focus Mode screen — activate and monitor focused work sessions.
 * Supports manual, scheduled, and Pomodoro modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(
    viewModel: FocusModeViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val isActive by viewModel.isActive.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val durationMs by viewModel.focusDurationMs.collectAsState()
    val interruptions by viewModel.interruptionCount.collectAsState()
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val pomodoroTimeRemaining by viewModel.pomodoroTimeRemainingMs.collectAsState()
    val completedPomodoros by viewModel.completedPomodoros.collectAsState()
    val lastReport by viewModel.lastReport.collectAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(500),
        label = "focus-bg"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Mode", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Status Ring ─────────────────────────────────

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isActive) {
                        val minutes = durationMs / 60_000
                        val seconds = (durationMs / 1000) % 60
                        Text(
                            text = "${minutes}:${"%02d".format(seconds)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Focusing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Interruptions ───────────────────────────────

            if (isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$interruptions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (interruptions > 5) ChartColors.danger else ChartColors.good
                        )
                        Text(
                            "Interruptions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (mode == FocusMode.FocusModeType.POMODORO) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedPomodoros",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = ChartColors.primary
                            )
                            Text(
                                "Pomodoros",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pomodoro state indicator
                if (mode == FocusMode.FocusModeType.POMODORO) {
                    val stateLabel = when (pomodoroState) {
                        FocusMode.PomodoroState.FOCUSING -> "🎯 Focus Phase"
                        FocusMode.PomodoroState.BREAK -> "☕ Short Break"
                        FocusMode.PomodoroState.LONG_BREAK -> "🏖️ Long Break"
                        FocusMode.PomodoroState.IDLE -> "Idle"
                    }
                    val remainingMin = pomodoroTimeRemaining / 60_000
                    val remainingSec = (pomodoroTimeRemaining / 1000) % 60

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stateLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${remainingMin}:${"%02d".format(remainingSec)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Mode Selection / Start Buttons ──────────────

            if (!isActive) {
                Text(
                    text = "Choose Focus Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                FocusModeOption(
                    icon = Icons.Default.TouchApp,
                    title = "Manual Focus",
                    description = "Start and stop manually. Blocks distracting apps and DNS domains.",
                    onClick = { viewModel.startFocus(FocusMode.FocusModeType.MANUAL) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FocusModeOption(
                    icon = Icons.Default.Timer,
                    title = "Pomodoro",
                    description = "25 min focus → 5 min break cycles. Long break after 4 sessions.",
                    onClick = { viewModel.startFocus(FocusMode.FocusModeType.POMODORO) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FocusModeOption(
                    icon = Icons.Default.Schedule,
                    title = "Scheduled",
                    description = "Automatically activates during configured time windows.",
                    onClick = { viewModel.startFocus(FocusMode.FocusModeType.SCHEDULED) }
                )
            } else {
                // Stop button
                Button(
                    onClick = { viewModel.stopFocus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChartColors.danger
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Focus Session", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Last Session Report ─────────────────────────

            lastReport?.let { report ->
                Text(
                    text = "Last Session Report",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val durationMin = report.totalDurationMs / 60_000
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duration", style = MaterialTheme.typography.bodySmall)
                            Text("${durationMin} min", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Interruptions", style = MaterialTheme.typography.bodySmall)
                            Text("${report.interruptionCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Focus Quality", style = MaterialTheme.typography.bodySmall)
                            val quality = report.focusQualityPercent
                            Text(
                                "${quality.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    quality >= 70 -> ChartColors.good
                                    quality >= 40 -> ChartColors.warning
                                    else -> ChartColors.danger
                                }
                            )
                        }
                        if (report.completedPomodoros > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pomodoros", style = MaterialTheme.typography.bodySmall)
                                Text("${report.completedPomodoros}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (report.topInterruptingApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Top Interrupting Apps:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            report.topInterruptingApps.entries.take(3).forEach { (app, count) ->
                                Text(
                                    "  ${app.substringAfterLast('.')} → $count times",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FocusModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
