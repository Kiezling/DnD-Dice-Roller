package com.kieslingdev.simpledice

import kotlin.random.Random
import kotlin.random.asJavaRandom
import org.junit.Assert.*
import org.junit.Test

class DiceStateTest {
    @Test fun `production randomness uses device seeded SecureRandom and valid bounded faces`() {
        assertTrue(diceRandom.asJavaRandom() is java.security.SecureRandom)
        for (sides in DICE) {
            repeat(100) { assertTrue(DiceState(sides).roll().current!!.value in 1..sides) }
        }
    }
    @Test fun `starts with D20 and no results`() {
        val state = DiceState()
        assertEquals(20, state.selectedDie)
        assertNull(state.current)
        assertTrue(state.history.isEmpty())
    }

    @Test fun `every die can roll one and its highest face`() {
        for (sides in DICE) {
            for (value in listOf(1, sides)) {
                val random = object : Random() {
                    override fun nextBits(bitCount: Int): Int = error("Unexpected random call")
                    override fun nextInt(from: Int, until: Int): Int {
                        assertEquals(1, from)
                        assertEquals(sides + 1, until)
                        return value
                    }
                }
                val result = DiceState(sides).roll(random, timestamp = 1234L).current!!
                assertEquals(sides, result.sides)
                assertEquals(value, result.value)
                assertEquals(1234L, result.timestamp)
                assertTrue(result.id > 0L)
            }
        }
    }

    @Test fun `seeded rolls stay within each die's range`() {
        val random = Random(7)
        for (sides in DICE) {
            repeat(100) {
                assertTrue(DiceState(sides).roll(random).current!!.value in 1..sides)
            }
        }
    }

    @Test fun `first roll has no previous history`() {
        val state = DiceState().roll(Random(1))
        assertNotNull(state.current)
        assertTrue(state.history.isEmpty())
    }

    @Test fun `history sums current and newer rolls even across different dice`() {
        val state = DiceState(rolls = listOf(Roll(20, 17), Roll(6, 3), Roll(100, 84)))
        assertEquals(listOf(
            HistoryEntry(Roll(6, 3), 20),
            HistoryEntry(Roll(100, 84), 104)
        ), state.history)
    }

    @Test fun `keeps current result and exactly ten previous rolls`() {
        val previous = (11 downTo 1).map { Roll(100, it) }
        val before = DiceState(selectedDie = 100, rolls = previous)
        val after = before.roll(Random(2))
        assertEquals(11, after.rolls.size)
        assertEquals(previous.take(10), after.history.map { it.roll })
        assertEquals(after.rolls.sumOf { it.value }, after.history.last().total)
        assertEquals(previous, before.rolls)
    }

    @Test fun `changing die does not relabel old rolls`() {
        val previous = listOf(Roll(20, 12), Roll(4, 3))
        val state = DiceState(rolls = previous).copy(selectedDie = 100).roll(Random(3))
        assertEquals(100, state.current!!.sides)
        assertEquals(previous, state.history.map { it.roll })
    }

    @Test fun `clear removes results and totals but preserves selection`() {
        val cleared = DiceState(100, listOf(Roll(100, 50), Roll(6, 4))).clear()
        assertEquals(100, cleared.selectedDie)
        assertTrue(cleared.rolls.isEmpty())
        assertEquals(2, cleared.archive.size)
        assertTrue(cleared.roll(Random(4)).history.isEmpty())
    }

    @Test fun `rolls stay in recent history and archive after more than eleven rolls`() {
        var state = DiceState()
        repeat(15) { index -> state = state.roll(Random(index), timestamp = index.toLong()) }

        assertEquals(11, state.rolls.size)
        assertEquals(15, state.archive.size)
        assertEquals(state.archive.take(11), state.rolls)
        assertEquals(15, state.archive.map { it.id }.toSet().size)
    }

    @Test fun `clear recent preserves archive and clear all deletes both lists`() {
        val filled = DiceState().roll(Random(1), 10L).roll(Random(2), 20L)
        val clearedRecent = filled.clearRecent()

        assertTrue(clearedRecent.rolls.isEmpty())
        assertEquals(filled.archive, clearedRecent.archive)
        assertEquals(emptyList<Roll>(), clearedRecent.clearAll().archive)
    }

    @Test fun `deleting selected recent rolls does not promote older hidden archive entries`() {
        var state = DiceState()
        repeat(13) { index -> state = state.roll(Random(index), timestamp = index.toLong()) }
        val hidden = state.archive.last()
        val delete = state.rolls.first().id

        val deleted = state.deleteRolls(setOf(delete))

        assertEquals(10, deleted.rolls.size)
        assertFalse(deleted.archive.any { it.id == delete })
        assertTrue(deleted.archive.any { it.id == hidden.id })
        assertEquals(state.archive.size - 1, deleted.archive.size)
    }

    @Test fun `roll settings support half second steps from half a second through five seconds`() {
        assertEquals(500L, RollSettings(RollMode.TIMED, 1).durationMillis)
        assertEquals(5000L, RollSettings(RollMode.TIMED, 10).durationMillis)
        assertThrows(IllegalArgumentException::class.java) { RollSettings(durationSteps = 0) }
        assertThrows(IllegalArgumentException::class.java) { RollSettings(durationSteps = 11) }
    }

    @Test fun `every die highlights one and its maximum`() {
        for (sides in DICE) {
            assertTrue(Roll(sides, sides).isCriticalSuccess)
            assertTrue(Roll(sides, 1).isCriticalFailure)
            assertFalse(Roll(sides, 2).isCriticalSuccess)
            assertFalse(Roll(sides, 2).isCriticalFailure)
        }
    }

    @Test fun `invalid rolls and selections cannot enter state`() {
        assertThrows(IllegalArgumentException::class.java) { Roll(20, 0) }
        assertThrows(IllegalArgumentException::class.java) { Roll(20, 21) }
        assertThrows(IllegalArgumentException::class.java) { Roll(3, 1) }
        assertThrows(IllegalArgumentException::class.java) { DiceState(3) }
        assertThrows(IllegalArgumentException::class.java) {
            DiceState(rolls = List(12) { Roll(6, 1) })
        }
    }
}
