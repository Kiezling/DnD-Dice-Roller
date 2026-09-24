package com.kieslingdev.simpledice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiceStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val openStores = mutableListOf<DiceStore>()

    @Before
    fun clearDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun closeStoresAndClearDatabase() {
        openStores.forEach(DiceStore::close)
        openStores.clear()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun closeAndReopenPreservesArchiveRecentSelectionAndSettings() {
        var state = DiceState(selectedDie = 100)
        val expectedIds = mutableListOf<Long>()
        val firstStore = newStore()
        repeat(15) { index ->
            state = state.roll(Random(index), timestamp = 42L)
            val saved = firstStore.recordRoll(state.current!!, state.selectedDie)
            expectedIds += saved.id
        }
        firstStore.saveSettings(RollSettings(RollMode.TIMED, durationSteps = 7))
        firstStore.close()

        val reopened = newStore()
        val restored = reopened.loadState()
        assertEquals(100, restored.selectedDie)
        assertEquals(expectedIds.reversed(), restored.archive.map { it.id })
        assertEquals(expectedIds.takeLast(RECENT_ROLLS_SIZE).reversed(), restored.rolls.map { it.id })
        assertEquals(RollSettings(RollMode.TIMED, durationSteps = 7), reopened.loadSettings())

        val deletedId = restored.rolls.first().id
        reopened.deleteRolls(setOf(deletedId))
        val afterDelete = reopened.loadState()
        assertEquals(14, afterDelete.archive.size)
        assertEquals(10, afterDelete.rolls.size)
        assertFalse(afterDelete.archive.any { it.id == deletedId })

        reopened.clearRecent()
        val afterClearRecent = reopened.loadState()
        assertTrue(afterClearRecent.rolls.isEmpty())
        assertEquals(14, afterClearRecent.archive.size)

        var nextState = afterClearRecent.roll(Random(99), timestamp = 42L)
        reopened.recordRoll(nextState.current!!, nextState.selectedDie)
        nextState = reopened.loadState()
        assertEquals(1, nextState.rolls.size)
        assertEquals(15, nextState.archive.size)

        reopened.clearAll()
        val afterClearAll = reopened.loadState()
        assertTrue(afterClearAll.rolls.isEmpty())
        assertTrue(afterClearAll.archive.isEmpty())
        assertEquals(RollSettings(RollMode.TIMED, durationSteps = 7), reopened.loadSettings())

        val lastIdBeforeClear = nextState.archive.maxOf { it.id }
        reopened.close()
        val afterRestart = newStore()
        val nextAfterClear = afterRestart.loadState().roll(Random(100), timestamp = 0L).current!!
        assertTrue(nextAfterClear.id > lastIdBeforeClear)
    }

    @Test
    fun idsControlNewestOrderAndNextIdAfterClockRollback() {
        val store = newStore()
        val older = Roll(sides = 20, value = 3, id = 7_000_000_000_000_000L, timestamp = 900L)
        store.recordRoll(older, selectedDie = 20)

        // Loading the persisted archive seeds the process ID generator above its largest known ID.
        val restored = store.loadState()
        val newerWithEarlierClock = restored.roll(Random(4), timestamp = 1L).current!!
        assertTrue(newerWithEarlierClock.id > older.id)
        assertTrue(newerWithEarlierClock.timestamp < older.timestamp)
        store.recordRoll(newerWithEarlierClock, selectedDie = 20)

        val afterClockRollback = store.loadState()
        assertEquals(listOf(newerWithEarlierClock.id, older.id), afterClockRollback.archive.map { it.id })
        assertEquals(afterClockRollback.archive, afterClockRollback.rolls)
    }

    private fun newStore(): DiceStore = DiceStore(context).also(openStores::add)

    companion object {
        private const val DATABASE_NAME = "dice_history.db"
    }
}
