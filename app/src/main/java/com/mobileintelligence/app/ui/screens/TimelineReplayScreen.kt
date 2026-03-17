package com.mobileintelligence.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.theme.ChartColors
import com.mobileintelligence.app.ui.viewmodel.TimelineReplayViewModel

/**
 * Timeline Replay screen — minute-by-minute chronological view
 * interleaving screen events, DNS events, and intelligence alerts.
 *
 * Acts as a "DVR for your phone" — scroll through the day's activity
 * with expandable event details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineReplayScreen(
    viewModel: TimelineReplayViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val events by viewModel.timelineEvents.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val eventCounts by viewModel.eventCounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Timeline Replay", fontWeight = FontWeight.Bold)
                        Text(
                            text = selectedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.previousDay() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                    }
                    IconButton(onClick = { viewModel.nextDay() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "Today")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Event Type Filter Chips ──────────────────────

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventFilterChip(
                    label = "Screen",
                    count = eventCounts.screen,
                    color = ChartColors.screenOn,
                    selected = true,
                    onClick = {}
                )
                EventFilterChip(
                    label = "DNS",
                    count = eventCounts.dns,
                    color = ChartColors.good,
                    selected = true,
                    onClick = {}
                )
                EventFilterChip(
                    label = "Alerts",
                    count = eventCounts.alerts,
                    color = ChartColors.danger,
                    selected = true,
                    onClick = {}
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No events for this date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── Event Timeline ──────────────────────────

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        TimelineEventRow(event)
                    }
                }
            }
        }
    }
}

// ── Filter Chip ─────────────────────────────────────────────────

@Composable
private fun EventFilterChip(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ── Timeline Event Row ──────────────────────────────────────────

@Composable
private fun TimelineEventRow(event: TimelineReplayViewModel.TimelineEvent) {
    var expanded by remember { mutableStateOf(false) }

    val eventColor = when (event.category) {
        TimelineReplayViewModel.EventCategory.SCREEN -> ChartColors.screenOn
        TimelineReplayViewModel.EventCategory.DNS -> ChartColors.good
        TimelineReplayViewModel.EventCategory.ALERT -> ChartColors.danger
        TimelineReplayViewModel.EventCategory.SESSION -> ChartColors.primary
        TimelineReplayViewModel.EventCategory.UNLOCK -> ChartColors.warning
    }

    val eventIcon = when (event.category) {
        TimelineReplayViewModel.EventCategory.SCREEN -> Icons.Default.PhoneAndroid
        TimelineReplayViewModel.EventCategory.DNS -> Icons.Default.Dns
        TimelineReplayViewModel.EventCategory.ALERT -> Icons.Default.Warning
        TimelineReplayViewModel.EventCategory.SESSION -> Icons.Default.Timer
        TimelineReplayViewModel.EventCategory.UNLOCK -> Icons.Default.LockOpen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline column (dot + line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Time label
            Text(
                text = event.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(eventColor)
            )
            // Line connector
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (expanded) 80.dp else 24.dp)
                    .background(eventColor.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Event content
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = eventColor.copy(alpha = 0.06f)
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            eventIcon,
                            contentDescription = null,
                            tint = eventColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        if (event.subtitle.isNotEmpty()) {
                            Text(
                                text = event.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        event.details.forEach { (key, value) ->
                            Row(modifier = Modifier.padding(top = 2.dp)) {
                                Text(
                                    text = "$key: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = eventColor
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
