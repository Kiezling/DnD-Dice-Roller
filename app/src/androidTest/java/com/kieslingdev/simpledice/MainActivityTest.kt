package com.kieslingdev.simpledice

import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Before fun resetSavedState() {
        DiceStore(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).use {
            it.clearAll(); it.saveSelectedDie(20); it.saveSettings(RollSettings())
        }
    }
    @Test fun choicesRollHistoryAndClearWorkTogether() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val slider = activity.findViewById<SeekBar>(R.id.die_slider)
                val choices = activity.findViewById<LinearLayout>(R.id.dice_choices)
                assertEquals(5, slider.progress)
                val results = mutableListOf<Roll>()
                repeat(14) { index ->
                    val dieIndex = index % DICE.size
                    choices.getChildAt(dieIndex).performClick()
                    assertEquals(dieIndex, slider.progress)
                    assertTrue(choices.getChildAt(dieIndex).isSelected)
                    activity.findViewById<View>(R.id.roll_button).performClick()
                    val value = activity.findViewById<TextView>(R.id.result).text.toString().toInt()
                    assertTrue(value in 1..DICE[dieIndex])
                    results.add(0, Roll(DICE[dieIndex], value))
                }
                val rows = visibleRows(activity).reversed()
                assertEquals(10, rows.size)
                var sum = results.first().value
                rows.forEachIndexed { index, row ->
                    val expected = results[index + 1]
                    sum += expected.value
                    assertEquals("D${expected.sides}", row.findViewById<TextView>(R.id.die).text.toString())
                    assertEquals(expected.value.toString(), row.findViewById<TextView>(R.id.value).text.toString())
                    assertEquals(sum.toString(), row.findViewById<TextView>(R.id.total).text.toString())
                }
                activity.findViewById<View>(R.id.clear_button).performClick()
                assertEquals("", activity.findViewById<TextView>(R.id.result).text.toString())
                assertTrue(visibleRows(activity).isEmpty())
                assertEquals(6, slider.progress)
                activity.findViewById<View>(R.id.roll_button).performClick()
                assertTrue(visibleRows(activity).isEmpty())
            }
        }
    }

    @Test fun recreationPreservesSelectionRollsLabelsAndTotals() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var before = emptyList<String>()
            scenario.onActivity { activity ->
                repeat(12) { index ->
                    activity.findViewById<SeekBar>(R.id.die_slider).progress = index % DICE.size
                    activity.findViewById<View>(R.id.roll_button).performClick()
                }
                before = snapshot(activity)
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(before, snapshot(it)) }
            scenario.onActivity { it.findViewById<View>(R.id.clear_button).performClick() }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(visibleRows(it).isEmpty())
                assertEquals("", it.findViewById<TextView>(R.id.result).text.toString())
                assertEquals(4, it.findViewById<SeekBar>(R.id.die_slider).progress)
            }
        }
    }

    private fun visibleRows(activity: MainActivity): List<View> {
        val container = activity.findViewById<LinearLayout>(R.id.history_rows)
        return (0 until container.childCount).map(container::getChildAt)
            .filter { it.visibility == View.VISIBLE }
    }

    private fun snapshot(activity: MainActivity): List<String> = listOf(
        activity.findViewById<SeekBar>(R.id.die_slider).progress.toString(),
        activity.findViewById<TextView>(R.id.result).text.toString(),
        activity.findViewById<TextView>(R.id.result).currentTextColor.toString()
    ) + visibleRows(activity).map { row ->
        listOf(R.id.die, R.id.value, R.id.total).joinToString("|") {
            row.findViewById<TextView>(it).text.toString()
        }
    }
}
