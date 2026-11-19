package haddad.three.notas.data.sync.transport

import haddad.three.notas.data.drive.model.DriveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Discovers remote encrypted journals published by peer devices in Google Drive `appDataFolder`.
 *
 * Responsibilities:
 * - Exhaustively pages through Drive files using `nextPageToken`.
 * - Excludes journals originating from the local device ID.
 * - Deduplicates redundant file metadata by `journalId`.
 * - Tracks per-device sequence progress and updates [SyncWatermarkEntity].
 * - Detects missing sequence gaps without skipping them.
 */
@Singleton
class JournalDiscoveryService
    @Inject
    constructor(
        private val transport: GoogleDriveSyncTransport,
        private val watermarkDao: SyncWatermarkDao,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Discovers all available remote journals across all Drive pages and updates watermarks.
         *
         * @param localDeviceId Device ID of the running instance to exclude from remote processing.
         * @return Discovered headers grouped by device ID and sorted monotonically by sequence.
         */
        suspend fun discoverRemoteJournals(localDeviceId: String): DiscoveryResult =
            withContext(ioDispatcher) {
                val allDiscovered = mutableListOf<RemoteJournalHeader>()
                var pageToken: String? = null
                var isFirstPage = true

                while (isFirstPage || !pageToken.isNullOrBlank()) {
                    isFirstPage = false
                    val pageResult =
                        transport.listRemoteJournals(
                            excludeDeviceId = localDeviceId,
                            pageSize = 50,
                            pageToken = pageToken,
                        )

                    when (pageResult) {
                        is DriveResult.Success -> {
                            allDiscovered.addAll(pageResult.data.journals)
                            pageToken = pageResult.data.nextPageToken
                        }
                        is DriveResult.Unauthorized -> return@withContext DiscoveryResult.Unauthorized
                        is DriveResult.RateLimited -> return@withContext DiscoveryResult.RateLimited(pageResult.retryAfterSeconds)
                        is DriveResult.Offline -> return@withContext DiscoveryResult.Failure("Network offline during discovery")
                        is DriveResult.Error -> return@withContext DiscoveryResult.Failure(pageResult.message, pageResult.cause)
                    }
                }

                // 1. Filter out malformed headers
                val validDiscovered =
                    allDiscovered.filter { header ->
                        header.deviceId.isNotBlank() &&
                            header.deviceId.length <= GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH &&
                            header.journalId.isNotBlank() &&
                            header.journalId.length <= GoogleDriveSyncTransport.MAX_IDENTIFIER_LENGTH &&
                            header.sequence > 0L &&
                            header.contentSha256.matches(GoogleDriveSyncTransport.SHA256_HEX_REGEX)
                    }

                // 2. Detect sequence collisions: distinct journals claiming the same (deviceId, sequence)
                val collisionsByDevice = mutableMapOf<String, MutableSet<Long>>()
                val groupedByDeviceAndSeq = validDiscovered.groupBy { it.deviceId to it.sequence }

                for ((key, headersForSeq) in groupedByDeviceAndSeq) {
                    val (devId, seq) = key
                    val distinctJournals = headersForSeq.map { it.journalId to it.contentSha256 }.distinct()
                    if (distinctJournals.size > 1) {
                        // Sequence collision detected: multiple differing journals for the same device sequence
                        collisionsByDevice.getOrPut(devId) { mutableSetOf() }.add(seq)
                    }
                }

                // 3. Deduplicate valid non-colliding headers by journalId (preferring latest)
                val sanitizedHeaders =
                    validDiscovered
                        .filter { header ->
                            val devCollisions = collisionsByDevice[header.deviceId]
                            devCollisions == null || !devCollisions.contains(header.sequence)
                        }.associateBy { it.journalId }
                        .values
                        .toList()

                // 4. Group by device ID and sort by sequence ascending
                val groupedByDevice =
                    sanitizedHeaders
                        .groupBy { it.deviceId }
                        .mapValues { (_, headers) -> headers.sortedBy { it.sequence } }

                // 5. Update transport watermarks for each remote device (including devices with only collisions)
                val allDeviceIds = (groupedByDevice.keys + collisionsByDevice.keys).distinct()
                for (remoteDeviceId in allDeviceIds) {
                    val remoteHeaders = groupedByDevice[remoteDeviceId] ?: emptyList()
                    val collisions = collisionsByDevice[remoteDeviceId] ?: emptySet()
                    updateWatermarkForDevice(remoteDeviceId, remoteHeaders, collisions)
                }

                DiscoveryResult.Success(groupedByDevice)
            }

        private suspend fun updateWatermarkForDevice(
            remoteDeviceId: String,
            headers: List<RemoteJournalHeader>,
            collidingSequences: Set<Long> = emptySet(),
        ) {
            val existing =
                watermarkDao.getWatermark(remoteDeviceId)
                    ?: SyncWatermarkEntity(deviceId = remoteDeviceId)

            val previousMissing = parseMissingSequences(existing.missingSequencesJson).toMutableSet()
            val discoveredSequences = headers.map { it.sequence }.toSet()

            val maxDiscoveredInBatch = headers.maxOfOrNull { it.sequence } ?: 0L
            val maxColliding = collidingSequences.maxOfOrNull { it } ?: 0L
            val newHighestDiscovered = max(existing.highestDiscoveredSeq, max(maxDiscoveredInBatch, maxColliding))

            // Any sequence newly discovered without collision is removed from previous missing set
            previousMissing.removeAll(discoveredSequences)

            // Colliding sequences MUST be recorded as unresolved missing gaps
            previousMissing.addAll(collidingSequences)

            // Detect any new sequence gaps up to newHighestDiscovered
            for (seq in 1L..newHighestDiscovered) {
                if (!discoveredSequences.contains(seq) && seq > existing.highestValidatedSeq) {
                    // If sequence is not discovered and not yet validated, record as missing gap
                    previousMissing.add(seq)
                }
            }

            val updatedWatermark =
                existing.copy(
                    highestDiscoveredSeq = newHighestDiscovered,
                    missingSequencesJson = json.encodeToString(previousMissing.sorted()),
                    updatedAt = System.currentTimeMillis(),
                )

            watermarkDao.insertOrUpdate(updatedWatermark)
        }

        private fun parseMissingSequences(jsonString: String): List<Long> {
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()
            return runCatching { json.decodeFromString<List<Long>>(jsonString) }.getOrDefault(emptyList())
        }
    }
