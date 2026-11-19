package haddad.three.notas.ui.settings.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import haddad.three.notas.R
import haddad.three.notas.data.sync.history.SyncHistoryRecordEntity
import haddad.three.notas.ui.common.AppBottomSheetDragHandle
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberActionLabel
import haddad.three.notas.ui.components.RememberOutlinedButton
import haddad.three.notas.ui.components.RememberTextButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter {
    ALL,
    SUCCESSFUL,
    FAILED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHistoryBottomSheet(
    sheetState: SheetState,
    history: List<SyncHistoryRecordEntity>,
    pendingChangesCount: Int,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val filteredList =
        remember(history, selectedFilter) {
            when (selectedFilter) {
                HistoryFilter.ALL -> history
                HistoryFilter.SUCCESSFUL -> history.filter { it.status == "SUCCESS" || it.status == "PARTIAL" }
                HistoryFilter.FAILED -> history.filter { it.status == "FAILED" }
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { AppBottomSheetDragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.cloud_sync_history_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Pending local queue: $pendingChangesCount changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (history.isNotEmpty()) {
                    RememberTextButton(onClick = onClearHistory) {
                        RememberActionLabel(stringResource(R.string.cloud_sync_clear_history))
                    }
                }
            }

            // Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL,
                    onClick = { selectedFilter = HistoryFilter.ALL },
                    label = { Text("All (${history.size})") },
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.SUCCESSFUL,
                    onClick = { selectedFilter = HistoryFilter.SUCCESSFUL },
                    label = { Text("Successful (${history.count { it.status == "SUCCESS" || it.status == "PARTIAL" }})") },
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.FAILED,
                    onClick = { selectedFilter = HistoryFilter.FAILED },
                    label = { Text("Failed (${history.count { it.status == "FAILED" }})") },
                )
            }

            HorizontalDivider()

            // List
            if (filteredList.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "history",
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = stringResource(R.string.cloud_sync_no_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredList, key = { it.id }) { record ->
                        SyncHistoryItemCard(record = record, dateFormat = dateFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncHistoryItemCard(
    record: SyncHistoryRecordEntity,
    dateFormat: SimpleDateFormat,
) {
    val isSuccess = record.status == "SUCCESS"
    val isPartial = record.status == "PARTIAL"

    val statusColor =
        when {
            isSuccess -> Color(0xFF2E7D32)
            isPartial -> Color(0xFFF57C00)
            else -> MaterialTheme.colorScheme.error
        }

    val statusIcon =
        when {
            isSuccess -> "check_circle"
            isPartial -> "warning"
            else -> "error"
        }

    val formattedDate = remember(record.timestampEpochMillis) { dateFormat.format(Date(record.timestampEpochMillis)) }
    val formattedDuration =
        remember(record.durationMillis) {
            if (record.durationMillis >= 1000) {
                String.format(Locale.US, "%.1fs", record.durationMillis / 1000f)
            } else {
                "${record.durationMillis}ms"
            }
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = record.status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                    Text(
                        text = "•  ${record.triggeredBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Uploaded: ${record.uploadedBatches} batches",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Downloaded: ${record.downloadedBatches} batches",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Applied: ${record.appliedOperations} ops",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!record.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        text = "${record.errorCategory ?: "ERROR"}: ${record.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

