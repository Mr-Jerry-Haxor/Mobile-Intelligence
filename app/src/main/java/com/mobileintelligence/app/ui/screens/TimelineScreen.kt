package com.mobileintelligence.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.ui.components.SessionTimelineItem
import com.mobileintelligence.app.ui.viewmodel.TimelineViewModel
import com.mobileintelligence.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val screenSessions by viewModel.screenSessions.collectAsState()
    val appSessions by viewModel.appSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    // Navigate to previous day
                    val current = selectedDate
                    val parts = current.split("-")
                    if (parts.size == 3) {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                        viewModel.setDate(DateUtils.formatDate(cal.timeInMillis))
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, "Previous day")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selectedDate == DateUtils.today()) "Today"
                        else DateUtils.getDayOfWeek(selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val current = selectedDate
                        val parts = current.split("-")
                        if (parts.size == 3) {
                            val cal = java.util.Calendar.getInstance()
                            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            val newDate = DateUtils.formatDate(cal.timeInMillis)
                            if (newDate <= DateUtils.today()) {
                                viewModel.setDate(newDate)
                            }
                        }
                    },
                    enabled = selectedDate != DateUtils.today()
                ) {
                    Icon(Icons.Default.ChevronRight, "Next day")
                }
            }

            HorizontalDivider()

            // Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${screenSessions.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DateUtils.formatDurationShort(
                            screenSessions.sumOf { it.durationMs }
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${appSessions.map { it.packageName }.distinct().size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Session list
            if (screenSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sessions recorded for this date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(screenSessions) { session ->
                        SessionTimelineItem(
                            startTime = session.screenOnTime,
                            endTime = session.screenOffTime,
                            duration = session.durationMs,
                            isUnlocked = session.wasUnlocked
                        )
                        if (session != screenSessions.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
