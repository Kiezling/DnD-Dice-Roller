package com.kieslingdev.simpledice

import kotlin.random.Random

val DICE = listOf(4, 6, 8, 10, 12, 20, 100)
const val HISTORY_SIZE = 10

data class Roll(val sides: Int, val value: Int) {
    init {
        require(sides in DICE)
        require(value in 1..sides)
    }

    val isCriticalSuccess: Boolean get() = value == sides
    val isCriticalFailure: Boolean get() = value == 1
}

data class HistoryEntry(val roll: Roll, val total: Int)

/** Newest roll first; the current result is separate from the ten history rows. */
data class DiceState(val selectedDie: Int = 20, val rolls: List<Roll> = emptyList()) {
    init {
        require(selectedDie in DICE)
        require(rolls.size <= HISTORY_SIZE + 1)
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

    fun roll(random: Random = Random.Default): DiceState {
        val next = Roll(selectedDie, random.nextInt(1, selectedDie + 1))
        return copy(rolls = (listOf(next) + rolls).take(HISTORY_SIZE + 1))
    }

    fun clear(): DiceState = copy(rolls = emptyList())
}
