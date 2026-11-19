package haddad.three.notas.data.sync.conflict

import haddad.three.notas.data.sync.change.EntityType
import haddad.three.notas.data.sync.change.OperationType
import haddad.three.notas.data.sync.hlc.Hlc
import haddad.three.notas.data.sync.journal.JournalOperationDto
import haddad.three.notas.data.sync.journal.SyncJournalDto
import haddad.three.notas.data.sync.transport.SyncWatermarkDao
import haddad.three.notas.data.sync.transport.SyncWatermarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Causal relationship classification between an incoming operation and local state.
 */
enum class CausalRelation {
    CAUSALLY_EARLIER,
    CAUSALLY_LATER,
    CONCURRENT,
    DUPLICATE,
    ALREADY_SUPERSEDED,
}

/**
 * Centralized ConflictResolver coordinating causal conflict detection, presentation,
 * safe tombstone garbage collection, and domain-level resolution actions.
 */
@Singleton
class ConflictResolver
    @Inject
    constructor(
        private val fieldConflictResolver: FieldConflictResolver,
        private val resolutionManager: ConflictResolutionManager,
        private val conflictRecordDao: SyncConflictRecordDao,
        private val conflictMetadataDao: SyncConflictMetadataDao,
        private val tombstoneDao: SyncTombstoneDao,
        private val syncWatermarkDao: SyncWatermarkDao,
        private val appliedOperationDao: SyncAppliedOperationDao,
        private val localDeviceIdProvider: (() -> String)? = null,
    ) {
        companion object {
            const val DEFAULT_MIN_TOMBSTONE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L // 30 days
        }

        /**
         * Analyzes an incoming operation against local durable state and classifies its causal relation.
         */
        suspend fun analyzeCausalRelation(
            operation: JournalOperationDto,
            opHlc: Hlc,
            journal: SyncJournalDto,
        ): CausalRelation {
            // 1. Duplicate check
            if (appliedOperationDao.isApplied(operation.changeId)) {
                return CausalRelation.DUPLICATE
            }

            // 2. Tombstone check
            val tombstone = tombstoneDao.getTombstone(operation.entityType, operation.entityId)
            if (tombstone != null) {
                val tombstoneHlc = Hlc.parse(tombstone.hlcTimestamp)
                if (opHlc <= tombstoneHlc) {
                    return CausalRelation.ALREADY_SUPERSEDED
                }
            }

            // 3. Metadata check
            val fields = conflictMetadataDao.getAllFieldsForEntity(operation.entityType, operation.entityId)
            if (fields.isEmpty()) {
                return CausalRelation.CAUSALLY_LATER
            }

            val maxFieldHlc = fields.mapNotNull { runCatching { Hlc.parse(it.hlcTimestamp) }.getOrNull() }.maxOrNull()
            if (maxFieldHlc == null) {
                return CausalRelation.CAUSALLY_LATER
            }

            // 4. Causal dependency check
            val acknowledgedSeq = journal.dependencies[maxFieldHlc.deviceId]
            val localId = localDeviceIdProvider?.invoke()

            return when {
                maxFieldHlc == opHlc -> CausalRelation.DUPLICATE
                opHlc > maxFieldHlc -> {
                    if (acknowledgedSeq != null && acknowledgedSeq > 0L) {
                        CausalRelation.CAUSALLY_LATER
                    } else if (localId != null && maxFieldHlc.deviceId != localId && maxFieldHlc.deviceId == opHlc.deviceId) {
                        CausalRelation.CAUSALLY_LATER
                    } else {
                        // Concurrent edit from another device without acknowledging local's latest sequence
                        CausalRelation.CONCURRENT
                    }
                }
                else -> {
                    // opHlc < maxFieldHlc
                    if (maxFieldHlc.deviceId == opHlc.deviceId) {
                        CausalRelation.ALREADY_SUPERSEDED
                    } else {
                        CausalRelation.CONCURRENT
                    }
                }
            }
        }

        /**
         * Delegates field-level resolution and application during journal ingestion.
         */
        suspend fun resolveAndApply(
            operation: JournalOperationDto,
            operationHlc: Hlc,
            journal: SyncJournalDto,
        ) {
            fieldConflictResolver.resolveAndApply(operation, operationHlc, journal)
        }

        // --- Domain-Level Resolution Actions ---

        suspend fun keepLocal(conflictId: String): ConflictResolutionResult =
            resolutionManager.keepLocal(conflictId)

        suspend fun keepRemote(conflictId: String): ConflictResolutionResult =
            resolutionManager.keepRemote(conflictId)

        suspend fun keepBoth(conflictId: String): ConflictResolutionResult =
            resolutionManager.keepBoth(conflictId)

        suspend fun merge(
            conflictId: String,
            mergedTitle: String,
            mergedBody: String,
        ): ConflictResolutionResult =
            resolutionManager.merge(conflictId, mergedTitle, mergedBody)

        suspend fun restoreDeleted(conflictId: String): ConflictResolutionResult =
            resolutionManager.restoreDeleted(conflictId)

        // --- Presentation Layer Flows ---

        fun getUnresolvedConflictsFlow(): Flow<List<ConflictUiModel>> =
            conflictRecordDao.getUnresolvedConflictsFlow().map { records ->
                records.map { it.toUiModel() }
            }

        fun getUnresolvedCountFlow(): Flow<Int> =
            conflictRecordDao.getUnresolvedConflictCountFlow()

        suspend fun getConflictSummary(): ConflictSummary {
            val unresolved = conflictRecordDao.getUnresolvedConflicts().map { it.toUiModel() }
            val all = conflictRecordDao.getAllConflictRecords().map { it.toUiModel() }
            val resolved = all.filter { it.resolved }.take(20)
            return ConflictSummary(
                unresolvedCount = unresolved.size,
                unresolvedConflicts = unresolved,
                recentResolvedConflicts = resolved,
            )
        }

        // --- Tombstone Safe Retention & Garbage Collection ---

        /**
         * Evaluates whether a tombstone can be safely purged without risking resurrection
         * from any active or temporarily offline device.
         */
        fun canSafelyPruneTombstone(
            tombstone: SyncTombstoneEntity,
            activeWatermarks: List<SyncWatermarkEntity>,
            now: Long = System.currentTimeMillis(),
            minRetentionMillis: Long = DEFAULT_MIN_TOMBSTONE_RETENTION_MILLIS,
        ): Boolean {
            // Rule 1: Must exceed the minimum retention period
            val age = now - tombstone.deletedAt
            if (age < minRetentionMillis) {
                return false
            }

            // Rule 2: Cannot prune if there are no known watermarks (safety first)
            if (activeWatermarks.isEmpty()) {
                return false
            }

            // Rule 3: All active devices must have advanced their applied sequence
            // to a non-zero state acknowledging sync progress
            val allDevicesActive = activeWatermarks.all { it.highestAppliedSeq > 0L }
            return allDevicesActive
        }

        /**
         * Safely prunes eligible tombstones that meet strict retention constraints.
         */
        suspend fun pruneEligibleTombstones(
            now: Long = System.currentTimeMillis(),
            minRetentionMillis: Long = DEFAULT_MIN_TOMBSTONE_RETENTION_MILLIS,
        ): Int {
            val watermarks = syncWatermarkDao.getAllWatermarks()
            val allTombstones = tombstoneDao.getAllTombstones()
            var prunedCount = 0

            for (tombstone in allTombstones) {
                if (canSafelyPruneTombstone(tombstone, watermarks, now, minRetentionMillis)) {
                    tombstoneDao.deleteTombstone(tombstone.entityType, tombstone.entityId)
                    prunedCount++
                }
            }
            return prunedCount
        }

        private fun SyncConflictRecordEntity.toUiModel(): ConflictUiModel =
            ConflictUiModel(
                id = id,
                conflictId = conflictId,
                entityType = entityType,
                entityId = entityId,
                fieldName = fieldName,
                conflictType = ConflictType.fromFieldName(fieldName),
                winningValue = winningValue,
                losingValue = losingValue,
                winningHlc = winningHlc,
                losingHlc = losingHlc,
                winningDeviceId = winningDeviceId,
                losingDeviceId = losingDeviceId,
                detectedAt = detectedAt,
                resolved = resolved,
            )
    }

