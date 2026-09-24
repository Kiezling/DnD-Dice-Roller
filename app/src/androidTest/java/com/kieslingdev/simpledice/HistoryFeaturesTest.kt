package com.kieslingdev.simpledice

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class HistoryFeaturesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class FixedRandom : Random() {
        var calls = 0
        override fun nextBits(bitCount: Int): Int = error("Unexpected call")
        override fun nextInt(from: Int, until: Int): Int { calls++; return 4 }
    }
    @Before fun reset() {
        DiceStore(context).use { it.clearAll(); it.saveSelectedDie(20); it.saveSettings(RollSettings()) }
    }
    private fun seed(count: Int = 6) {
        DiceStore(context).use { store ->
            var state = DiceState()
            repeat(count) {
                state = state.roll(FixedRandom(), 1_800_000_000_000L + it)
                store.recordRoll(state.current!!, 20)
            }
        }
    }
    @Test fun dragContractsRangeCentersSumAndDeletesOnlyChosenRolls() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val selection = activity.findViewById<SelectionHistoryView>(R.id.history_selection)
                val container = activity.findViewById<LinearLayout>(R.id.history_rows)
                val rows = (0 until container.childCount).map(container::getChildAt).filter { it.visibility == View.VISIBLE }
                val down = SystemClock.uptimeMillis()
                fun touch(action: Int, index: Int) {
                    val row = rows[index]
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        selection.width / 2f, row.top + row.height / 2f, 0)
                    selection.dispatchTouchEvent(event)
                    event.recycle()
                }
                touch(MotionEvent.ACTION_DOWN, 0)
                touch(MotionEvent.ACTION_MOVE, 3)
                val sum = activity.findViewById<TextView>(R.id.selection_sum)
                assertEquals("16", sum.text.toString())
                touch(MotionEvent.ACTION_MOVE, 1)
                touch(MotionEvent.ACTION_UP, 1)
                assertEquals("8", sum.text.toString())
                assertEquals(2, rows.count { it.isSelected })
                assertEquals((rows[0].top + rows[0].height / 2f + rows[1].top + rows[1].height / 2f) / 2f,
                    sum.y + sum.height / 2f, 1f)
                activity.findViewById<View>(R.id.delete_selection).performClick()
                assertEquals(View.GONE, sum.visibility)
                DiceStore(context).use {
                    assertEquals(4, it.loadState().archive.size)
                    assertEquals(4, it.loadState().rolls.size)
                }
            }
        }
    }
    @Test fun clearingRecentSurvivesNewActivityButArchiveRemains() {
        seed(15)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.clear_button).performClick() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals("", it.findViewById<TextView>(R.id.result).text.toString())
                DiceStore(context).use { store -> assertEquals(15, store.loadState().archive.size) }
                it.findViewById<View>(R.id.roll_button).performClick()
                DiceStore(context).use { store ->
                    assertEquals(16, store.loadState().archive.size)
                    assertEquals(1, store.loadState().rolls.size)
                }
            }
        }
    }
    @Test fun timedRollCommitsOnceAfterDelayAndIgnoresRepeatedClicks() {
        DiceStore(context).use { it.saveSettings(RollSettings(RollMode.TIMED, 1)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom()
            scenario.onActivity {
                it.rollRandom = random
                val button = it.findViewById<View>(R.id.roll_button)
                button.performClick()
                button.performClick()
                assertEquals(0, random.calls)
                assertFalse(button.isEnabled)
                DiceStore(context).use { store -> assertTrue(store.loadState().archive.isEmpty()) }
            }
            SystemClock.sleep(750)
            scenario.onActivity {
                assertEquals(1, random.calls)
                assertEquals("4", it.findViewById<TextView>(R.id.result).text.toString())
                assertTrue(it.findViewById<View>(R.id.roll_button).isEnabled)
                DiceStore(context).use { store -> assertEquals(1, store.loadState().archive.size) }
            }
        }
    }
    @Test fun recreationCancelsPendingTimedRollWithoutSavingPreview() {
        DiceStore(context).use { it.saveSettings(RollSettings(RollMode.TIMED, 2)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.roll_button).performClick() }
            scenario.recreate()
            SystemClock.sleep(1150)
            scenario.onActivity {
                assertEquals("", it.findViewById<TextView>(R.id.result).text.toString())
                assertTrue(it.findViewById<View>(R.id.roll_button).isEnabled)
                DiceStore(context).use { store -> assertTrue(store.loadState().archive.isEmpty()) }
            }
        }
    }
    @Test fun instantModeCommitsImmediatelyAndAllowsHoldPreview() {
        DiceStore(context).use { it.saveSettings(RollSettings(RollMode.INSTANT)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val random = FixedRandom()
                it.rollRandom = random
                val button = it.findViewById<RollButton>(R.id.roll_button)
                assertTrue(button.holdEnabled)
                button.performClick()
                assertEquals(1, random.calls)
                DiceStore(context).use { store ->
                    assertEquals(1, store.loadState().archive.size)
                    assertTrue(store.loadState().current!!.timestamp > 0)
                }
            }
        }
    }
}
