package haddad.three.notas.data.sync.hlc

import kotlinx.serialization.Serializable

/**
 * Hybrid Logical Clock (HLC) timestamp providing strict monotonic total ordering
 * across distributed devices without relying on synchronized wall clocks.
 *
 * Canonical Ordering:
 * (physicalTime, logicalCounter, deviceId)
 */
@Serializable
data class Hlc(
    val physicalTime: Long,
    val logicalCounter: Int,
    val deviceId: String,
) : Comparable<Hlc> {
    init {
        require(physicalTime >= 0L) { "Physical time must be non-negative: $physicalTime" }
        require(logicalCounter >= 0) { "Logical counter must be non-negative: $logicalCounter" }
        require(deviceId.isNotBlank()) { "Device ID must not be blank" }
    }

    override fun compareTo(other: Hlc): Int {
        if (physicalTime != other.physicalTime) return physicalTime.compareTo(other.physicalTime)
        if (logicalCounter != other.logicalCounter) return logicalCounter.compareTo(other.logicalCounter)
        return deviceId.compareTo(other.deviceId)
    }

    override fun toString(): String = "$physicalTime:$logicalCounter:$deviceId"

    companion object {
        val ZERO = Hlc(0L, 0, "origin")

        /**
         * Parses a canonical string representation "physicalTime:logicalCounter:deviceId".
         */
        fun parse(serialized: String): Hlc {
            val parts = serialized.split(":")
            require(parts.size == 3) {
                "Invalid HLC string format: '$serialized'. Expected 'physicalTime:counter:deviceId'"
            }
            val physical =
                parts[0].toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid physical time component in HLC: '${parts[0]}'")
            val counter =
                parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid logical counter component in HLC: '${parts[1]}'")
            val node = parts[2]
            require(node.isNotBlank()) { "Device ID in HLC string cannot be blank: '$serialized'" }
            return Hlc(physical, counter, node)
        }

        fun parseOrNull(serialized: String?): Hlc? {
            if (serialized.isNullOrBlank()) return null
            return runCatching { parse(serialized) }.getOrNull()
        }
    }
}
