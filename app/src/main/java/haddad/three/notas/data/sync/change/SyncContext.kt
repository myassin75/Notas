package haddad.three.notas.data.sync.change

import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Context controller that isolates local and remote operations.
 * When operations run within [withRemoteSuppression], local change recording
 * is bypassed to prevent synchronization feedback loops.
 */
object SyncContext {
    private val threadLocalSuppressed = ThreadLocal.withInitial { false }

    class RemoteSyncElement : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<RemoteSyncElement>

        override val key: CoroutineContext.Key<*> = Key
    }

    suspend fun <T> withRemoteSuppression(block: suspend () -> T): T {
        val prev = threadLocalSuppressed.get()
        threadLocalSuppressed.set(true)
        return try {
            withContext(RemoteSyncElement()) {
                block()
            }
        } finally {
            threadLocalSuppressed.set(prev)
        }
    }

    suspend fun isSuppressed(): Boolean {
        if (threadLocalSuppressed.get() == true) return true
        return coroutineContext[RemoteSyncElement] != null
    }

    fun isSuppressedBlocking(): Boolean = threadLocalSuppressed.get() == true
}
