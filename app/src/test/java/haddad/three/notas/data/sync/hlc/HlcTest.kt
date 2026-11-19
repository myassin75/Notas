package haddad.three.notas.data.sync.hlc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class HlcTest {
    @Test
    fun testCanonicalOrdering() {
        val hlc1 = Hlc(1000L, 0, "device_a")
        val hlc2 = Hlc(1000L, 1, "device_a")
        val hlc3 = Hlc(1000L, 1, "device_b")
        val hlc4 = Hlc(1001L, 0, "device_a")

        assertTrue(hlc1 < hlc2)
        assertTrue(hlc2 < hlc3) // tie-breaker device_a < device_b
        assertTrue(hlc3 < hlc4) // physical takes precedence
        assertEquals(0, hlc1.compareTo(Hlc(1000L, 0, "device_a")))
    }

    @Test
    fun testSerializationAndParsing() {
        val original = Hlc(1710000000123L, 42, "node-pixel-8")
        val serialized = original.toString()
        assertEquals("1710000000123:42:node-pixel-8", serialized)

        val parsed = Hlc.parse(serialized)
        assertEquals(original, parsed)
        assertEquals(original.physicalTime, parsed.physicalTime)
        assertEquals(original.logicalCounter, parsed.logicalCounter)
        assertEquals(original.deviceId, parsed.deviceId)
    }

    @Test
    fun testMultipleOperationsInSameMillisecond(): Unit =
        runBlocking {
            val fixedTime = 5000L
            val clock = HlcClock("device_1", InMemoryHlcStorage()) { fixedTime }

            val h1 = clock.generateNext()
            val h2 = clock.generateNext()
            val h3 = clock.generateNext()

            assertEquals(5000L, h1.physicalTime)
            assertEquals(0, h1.logicalCounter)

            assertEquals(5000L, h2.physicalTime)
            assertEquals(1, h2.logicalCounter)

            assertEquals(5000L, h3.physicalTime)
            assertEquals(2, h3.logicalCounter)

            assertTrue(h1 < h2)
            assertTrue(h2 < h3)
        }

    @Test
    fun testLocalClockMovingBackward(): Unit =
        runBlocking {
            var currentWallTime = 10_000L
            val clock = HlcClock("device_1", InMemoryHlcStorage()) { currentWallTime }

            val h1 = clock.generateNext()
            assertEquals(10_000L, h1.physicalTime)
            assertEquals(0, h1.logicalCounter)

            // Wall clock jumps backward to 8_000L (e.g. NTP adjustment or time sync)
            currentWallTime = 8_000L

            val h2 = clock.generateNext()
            // HLC physical time must NOT move backward
            assertEquals(10_000L, h2.physicalTime)
            assertEquals(1, h2.logicalCounter)
            assertTrue(h1 < h2)

            val h3 = clock.generateNext()
            assertEquals(10_000L, h3.physicalTime)
            assertEquals(2, h3.logicalCounter)
            assertTrue(h2 < h3)

            // Wall clock catches back up to 12_000L
            currentWallTime = 12_000L
            val h4 = clock.generateNext()
            assertEquals(12_000L, h4.physicalTime)
            assertEquals(0, h4.logicalCounter)
            assertTrue(h3 < h4)
        }

    @Test
    fun testRemoteClockAheadOfLocalClock(): Unit =
        runBlocking {
            val localWallTime = 5_000L
            val clock = HlcClock("device_local", InMemoryHlcStorage()) { localWallTime }

            val local1 = clock.generateNext()
            assertEquals(5_000L, local1.physicalTime)

            // Receive remote HLC with physical time far in the future
            val remoteHlc = Hlc(8_000L, 5, "device_remote")
            val merged = clock.mergeRemote(remoteHlc)

            // Local clock must jump to remote physical time and increment counter
            assertEquals(8_000L, merged.physicalTime)
            assertEquals(6, merged.logicalCounter)
            assertEquals("device_local", merged.deviceId)
            assertTrue(local1 < merged)
            assertTrue(remoteHlc < merged)

            // Next local operation advances counter while wall time is still 5_000L
            val local2 = clock.generateNext()
            assertEquals(8_000L, local2.physicalTime)
            assertEquals(7, local2.logicalCounter)
            assertTrue(merged < local2)
        }

    @Test
    fun testIdenticalPhysicalTimestampsMerge(): Unit =
        runBlocking {
            val clock = HlcClock("device_local", InMemoryHlcStorage()) { 10_000L }

            // Local clock at (10000, 3, local)
            clock.generateNext() // counter 0
            clock.generateNext() // counter 1
            clock.generateNext() // counter 2
            val local3 = clock.generateNext() // counter 3
            assertEquals(3, local3.logicalCounter)

            // Remote arrives with same physical 10000 but counter 10
            val remote = Hlc(10_000L, 10, "device_remote")
            val merged = clock.mergeRemote(remote)

            assertEquals(10_000L, merged.physicalTime)
            assertEquals(11, merged.logicalCounter) // max(3, 10) + 1
            assertTrue(remote < merged)
            assertTrue(local3 < merged)
        }

    @Test
    fun testApplicationRestartPersistence(): Unit =
        runBlocking {
            val storage = InMemoryHlcStorage()
            val timeSource = AtomicLong(7_000L)

            // Run clock before restart
            val clockBefore = HlcClock("device_1", storage) { timeSource.get() }
            clockBefore.generateNext()
            clockBefore.generateNext()
            val lastBefore = clockBefore.generateNext()
            assertEquals(7_000L, lastBefore.physicalTime)
            assertEquals(2, lastBefore.logicalCounter)

            // Simulate app restart with wall time rolled back or frozen
            val clockAfter = HlcClock("device_1", storage) { timeSource.get() }
            clockAfter.initialize()

            val firstAfter = clockAfter.generateNext()
            assertEquals(7_000L, firstAfter.physicalTime)
            assertEquals(3, firstAfter.logicalCounter)
            assertTrue(lastBefore < firstAfter)
        }

    @Test
    fun testConcurrentLocalClockUpdates(): Unit =
        runBlocking {
            val clock = HlcClock("device_concurrent", InMemoryHlcStorage()) { 10_000L }
            val threadCount = 20
            val opsPerThread = 50

            val jobs =
                (1..threadCount).map {
                    async(Dispatchers.Default) {
                        val list = mutableListOf<Hlc>()
                        repeat(opsPerThread) {
                            list.add(clock.generateNext())
                        }
                        list
                    }
                }

            val allResults = jobs.awaitAll().flatten()
            assertEquals(threadCount * opsPerThread, allResults.size)

            // All timestamps must be distinct and strictly ordered
            val uniqueHlcs = allResults.toSet()
            assertEquals(allResults.size, uniqueHlcs.size)

            val sorted = allResults.sorted()
            assertEquals(allResults.size, sorted.size)
            // Verify counters are 0..(total - 1) since physical time was fixed
            for (i in 0 until (threadCount * opsPerThread)) {
                assertEquals(10_000L, sorted[i].physicalTime)
                assertEquals(i, sorted[i].logicalCounter)
            }
        }

    @Test
    fun testDeterministicOrderingBetweenDifferentDevices() {
        val hlcA = Hlc(2000L, 5, "device_aaa")
        val hlcB = Hlc(2000L, 5, "device_bbb")
        val hlcC = Hlc(2000L, 5, "device_ccc")

        assertTrue(hlcA < hlcB)
        assertTrue(hlcB < hlcC)
        assertTrue(hlcA < hlcC)

        val list = listOf(hlcC, hlcA, hlcB)
        val sorted = list.sorted()
        assertEquals(listOf(hlcA, hlcB, hlcC), sorted)
    }
}
