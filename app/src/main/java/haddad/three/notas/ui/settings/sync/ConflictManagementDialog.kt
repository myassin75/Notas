package haddad.three.notas.ui.settings.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.data.sync.conflict.ConflictType
import haddad.three.notas.data.sync.conflict.SyncConflictRecordEntity
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberActionLabel
import haddad.three.notas.ui.components.RememberButton
import haddad.three.notas.ui.components.RememberOutlinedButton
import haddad.three.notas.ui.components.RememberTextButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConflictManagementDialog(
    conflict: SyncConflictRecordEntity,
    onDismiss: () -> Unit,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onKeepBoth: () -> Unit,
    onMerge: (mergedTitle: String, mergedBody: String) -> Unit,
    onRestoreDeleted: () -> Unit,
    decryptPayload: (String?, String) -> String?,
    isResolving: Boolean,
) {
    var isMergeMode by remember { mutableStateOf(false) }

    val rawLocal = decryptPayload(conflict.losingValue, conflict.conflictId) ?: conflict.losingValue ?: ""
    val rawRemote = decryptPayload(conflict.winningValue, conflict.conflictId) ?: conflict.winningValue ?: ""

    var mergedTitle by remember { mutableStateOf(if (conflict.fieldName == "title") rawLocal else "") }
    var mergedBody by remember { mutableStateOf(if (conflict.fieldName == "body") "$rawLocal\n\n--- Remote Version ---\n$rawRemote" else "") }

    val conflictType = ConflictType.fromFieldName(conflict.fieldName)
    val isDeleteConflict = conflictType == ConflictType.DELETE_VS_UPDATE

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val detectedDateStr = remember(conflict.detectedAt) { dateFormat.format(Date(conflict.detectedAt)) }

    AlertDialog(
        onDismissRequest = { if (!isResolving) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "warning",
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = if (isMergeMode) stringResource(R.string.cloud_sync_merge) else stringResource(R.string.cloud_sync_conflicts_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Conflict on ${conflict.entityType.lowercase()} field: \"${conflict.fieldName}\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Detected: $detectedDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                HorizontalDivider()

                if (isMergeMode) {
                    Text(
                        text = "Customize the merged content to retain changes from both versions:",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (conflict.fieldName == "title") {
                        OutlinedTextField(
                            value = mergedTitle,
                            onValueChange = { mergedTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = mergedBody,
                        onValueChange = { mergedBody = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                    )
                } else {
                    // Local Version Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Local Version (This Device)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (rawLocal.isNotBlank()) rawLocal else "(Empty or Deleted)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // Remote Version Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Remote Version (Google Drive)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = if (rawRemote.isNotBlank()) rawRemote else "(Empty or Deleted)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                if (isResolving) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Applying resolution...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isMergeMode) {
                RememberButton(
                    onClick = { onMerge(mergedTitle, mergedBody) },
                    enabled = !isResolving,
                ) {
                    RememberActionLabel("Apply Merge")
                }
            } else if (isDeleteConflict) {
                RememberButton(
                    onClick = onRestoreDeleted,
                    enabled = !isResolving,
                ) {
                    RememberActionLabel(stringResource(R.string.cloud_sync_restore_deleted))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RememberOutlinedButton(
                        onClick = onKeepLocal,
                        enabled = !isResolving,
                    ) {
                        RememberActionLabel(stringResource(R.string.cloud_sync_keep_local))
                    }
                    RememberButton(
                        onClick = onKeepRemote,
                        enabled = !isResolving,
                    ) {
                        RememberActionLabel(stringResource(R.string.cloud_sync_keep_remote))
                    }
                }
            }
        },
        dismissButton = {
            if (isMergeMode) {
                RememberTextButton(
                    onClick = { isMergeMode = false },
                    enabled = !isResolving,
                ) {
                    RememberActionLabel("Back")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isDeleteConflict) {
                        RememberTextButton(
                            onClick = onKeepBoth,
                            enabled = !isResolving,
                        ) {
                            RememberActionLabel(stringResource(R.string.cloud_sync_keep_both))
                        }
                        RememberTextButton(
                            onClick = { isMergeMode = true },
                            enabled = !isResolving,
                        ) {
                            RememberActionLabel("Merge")
                        }
                    }
                    RememberTextButton(
                        onClick = onDismiss,
                        enabled = !isResolving,
                    ) {
                        RememberActionLabel("Cancel")
                    }
                }
            }
        },
    )
}

