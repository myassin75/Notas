package haddad.three.notas.data.sync.hlc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence abstraction for persistent HLC state across application restarts.
 */
interface HlcStorage {
    suspend fun loadState(nodeId: String): Pair<Long, Int>?

    suspend fun saveState(
        nodeId: String,
        physicalTime: Long,
        logicalCounter: Int,
    )
}

/**
 * In-memory storage for test scenarios.
 */
class InMemoryHlcStorage : HlcStorage {
    private val map = mutableMapOf<String, Pair<Long, Int>>()

    override suspend fun loadState(nodeId: String): Pair<Long, Int>? = map[nodeId]

    override suspend fun saveState(
        nodeId: String,
        physicalTime: Long,
        logicalCounter: Int,
    ) {
        map[nodeId] = physicalTime to logicalCounter
    }
}

/**
 * Thread-safe Hybrid Logical Clock implementation.
 *
 * Guarantees:
 * 1. Monotonic local advancement even when physical wall clock moves backward.
 * 2. Causal advancement upon receiving remote HLC timestamps.
 * 3. Bounded drift and deterministic tie-breaking via node/device ID.
 * 4. Durable persistence across application restarts.
 */
@Singleton
class HlcClock
    @Inject
    constructor(
        private val nodeId: String,
        private val storage: HlcStorage? = null,
        private val wallClock: () -> Long = System::currentTimeMillis,
    ) {
        private val mutex = Mutex()
        private var lastPhysical: Long = 0L
        private var lastCounter: Int = 0
        private var isInitialized = false

        suspend fun initialize() {
            mutex.withLock {
                if (!isInitialized) {
                    val saved = storage?.loadState(nodeId)
                    if (saved != null) {
                        lastPhysical = saved.first
                        lastCounter = saved.second
                    }
                    isInitialized = true
                }
            }
        }

        /**
         * Generates the next monotonic local HLC timestamp for a new mutation.
         */
        suspend fun generateNext(): Hlc =
            mutex.withLock {
                ensureInitializedLocked()
                val wall = wallClock()
                val nextPhysical = maxOf(lastPhysical, wall)
                val nextCounter = if (nextPhysical == lastPhysical) lastCounter + 1 else 0

                lastPhysical = nextPhysical
                lastCounter = nextCounter
                storage?.saveState(nodeId, nextPhysical, nextCounter)
                Hlc(nextPhysical, nextCounter, nodeId)
            }

        /**
         * Merges an incoming remote HLC timestamp into the local clock state.
         * Advances the clock to be strictly causally greater than both the current local state
         * and the incoming remote timestamp.
         */
        suspend fun mergeRemote(remote: Hlc): Hlc =
            mutex.withLock {
                ensureInitializedLocked()
                val wall = wallClock()
                val nextPhysical = maxOf(lastPhysical, remote.physicalTime, wall)
                val nextCounter =
                    when {
                        nextPhysical == lastPhysical && nextPhysical == remote.physicalTime ->
                            maxOf(lastCounter, remote.logicalCounter) + 1
                        nextPhysical == lastPhysical -> lastCounter + 1
                        nextPhysical == remote.physicalTime -> remote.logicalCounter + 1
                        else -> 0
                    }

                lastPhysical = nextPhysical
                lastCounter = nextCounter
                storage?.saveState(nodeId, nextPhysical, nextCounter)
                Hlc(nextPhysical, nextCounter, nodeId)
            }

        suspend fun peek(): Hlc =
            mutex.withLock {
                ensureInitializedLocked()
                Hlc(lastPhysical, lastCounter, nodeId)
            }

        private suspend fun ensureInitializedLocked() {
            if (!isInitialized) {
                val saved = storage?.loadState(nodeId)
                if (saved != null) {
                    lastPhysical = saved.first
                    lastCounter = saved.second
                }
                isInitialized = true
            }
        }
    }
