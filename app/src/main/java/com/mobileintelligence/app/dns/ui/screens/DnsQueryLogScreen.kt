package com.mobileintelligence.app.dns.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileintelligence.app.dns.data.entity.DnsQueryEntity
import com.mobileintelligence.app.dns.ui.viewmodel.DnsQueryLogViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsQueryLogScreen(
    viewModel: DnsQueryLogViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val queries by viewModel.queries.collectAsState()
    val showBlockedOnly by viewModel.showBlockedOnly.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Top Bar ────────────────────────────────────────────
        TopAppBar(
            title = { Text("Query Log") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.ArrowBack, "Back")
                }
            }
        )

        // ─── Search & Filter ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search domain...") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showBlockedOnly,
                    onClick = { viewModel.setBlockedOnly(false) },
                    label = { Text("All") },
                    leadingIcon = if (!showBlockedOnly) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = showBlockedOnly,
                    onClick = { viewModel.setBlockedOnly(true) },
                    label = { Text("Blocked") },
                    leadingIcon = if (showBlockedOnly) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${queries.size} queries",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Query List ─────────────────────────────────────────
        if (queries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No queries yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Queries will appear here when the VPN is active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(queries, key = { it.id }) { query ->
                    QueryLogItem(
                        query = query,
                        appLabel = viewModel.getAppLabel(query.appPackage)
                    )
                }
            }
        }
    }
}

@Composable
fun QueryLogItem(query: DnsQueryEntity, appLabel: String) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (query.blocked) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (query.blocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = query.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (query.appPackage.isNotEmpty()) {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                    }
                    Text(
                        text = query.recordTypeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (query.blocked && query.blockReason != null) {
                        Text(
                            text = query.blockReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (query.cached) {
                        Text(
                            text = "cached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeFormat.format(Date(query.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (query.responseTimeMs > 0) {
                    Text(
                        text = "${query.responseTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
