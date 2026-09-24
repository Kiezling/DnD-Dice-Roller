package com.kieslingdev.simpledice

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

val DICE = listOf(4, 6, 8, 10, 12, 20, 100)
const val HISTORY_SIZE = 10
const val RECENT_ROLLS_SIZE = HISTORY_SIZE + 1

private val rollIdSequence = AtomicLong(System.currentTimeMillis() * 1_000L)

internal fun seedRollIdSequence(minimumId: Long) {
    if (minimumId <= 0L) return
    while (true) {
        val current = rollIdSequence.get()
        if (current >= minimumId || rollIdSequence.compareAndSet(current, minimumId)) return
    }
}

private fun observeRollIds(rolls: Iterable<Roll>) {
    val maxObservedId = rolls.maxOfOrNull { it.id } ?: return
    seedRollIdSequence(maxObservedId)
}

/** A result with a stable archive identity and the time at which it was rolled. */
data class Roll(
    val sides: Int,
    val value: Int,
    val id: Long = 0L,
    val timestamp: Long = 0L
) {
    init {
        require(sides in DICE)
        require(value in 1..sides)
        require(id >= 0L)
    }

    val isCriticalSuccess: Boolean get() = value == sides
    val isCriticalFailure: Boolean get() = value == 1
}

enum class RollMode { INSTANT, TIMED }

/** Timed duration is stored as half-second steps, from 0.5 to 5 seconds. */
data class RollSettings(
    val mode: RollMode = RollMode.INSTANT,
    val durationSteps: Int = 2,
    val darkMode: Boolean = false
) {
    init {
        require(durationSteps in 1..10)
    }

    val durationMillis: Long get() = durationSteps * 500L
}

data class HistoryEntry(val roll: Roll, val total: Int)

/** Recent results and archive are newest first by stable roll ID; timestamps are display metadata. */
data class DiceState(
    val selectedDie: Int = 20,
    val rolls: List<Roll> = emptyList(),
    val archive: List<Roll> = rolls
) {
    init {
        require(selectedDie in DICE)
        require(rolls.size <= RECENT_ROLLS_SIZE)
        observeRollIds(archive.asSequence().plus(rolls.asSequence()).asIterable())
    }

    val current: Roll? get() = rolls.firstOrNull()

    val history: List<HistoryEntry>
        get() {
            var total = current?.value ?: 0
            return rolls.drop(1).map { roll ->
                total += roll.value
                HistoryEntry(roll, total)
            }
        }

    fun roll(random: Random = diceRandom, timestamp: Long = System.currentTimeMillis()): DiceState {
        val next = Roll(
            sides = selectedDie,
            value = random.nextInt(1, selectedDie + 1),
            id = rollIdSequence.incrementAndGet(),
            timestamp = timestamp
        )
        return copy(
            rolls = (listOf(next) + rolls).take(RECENT_ROLLS_SIZE),
            archive = listOf(next) + archive
        )
    }

    /** Clears the visible recent list while keeping the complete archive. */
    fun clear(): DiceState = clearRecent()

    fun clearRecent(): DiceState = copy(rolls = emptyList())

    fun clearAll(): DiceState = copy(rolls = emptyList(), archive = emptyList())

    /** Removes explicitly selected results from both lists without promoting older archive entries. */
    fun deleteRolls(ids: Set<Long>): DiceState {
        if (ids.isEmpty()) return this
        return copy(
            rolls = rolls.filterNot { it.id in ids },
            archive = archive.filterNot { it.id in ids }
        )
    }
}
