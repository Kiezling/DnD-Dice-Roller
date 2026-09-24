package com.kieslingdev.simpledice

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class RollInteractionTest {
    private class FixedRandom(var value: Int = 1) : Random() {
        var calls = 0
        override fun nextBits(bitCount: Int): Int = error("Unexpected random call")
        override fun nextInt(from: Int, until: Int): Int {
            calls++
            require(value in from until until)
            return value
        }
    }

    @Test fun everyDieKeepsBoundaryColorsAndCommittedLabelThroughSelectionAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom()
            for ((index, sides) in DICE.withIndex()) {
                scenario.onActivity { activity ->
                    activity.rollRandom = random
                    activity.findViewById<View>(R.id.clear_button).performClick()
                    activity.findViewById<SeekBar>(R.id.die_slider).progress = index
                    val button = activity.findViewById<View>(R.id.roll_button)
                    random.value = 1
                    button.performClick()
                    assertResult(activity, "1", R.color.critical_failure)
                    random.value = sides
                    button.performClick()
                    assertResult(activity, "$sides", R.color.critical_success)
                    random.value = 2
                    button.performClick()
                    assertResult(activity, "2", R.color.result)
                    activity.findViewById<SeekBar>(R.id.die_slider).progress = (index + 1) % DICE.size
                    assertEquals("D$sides", activity.findViewById<TextView>(R.id.current_die).text.toString())
                    assertHistoryColors(activity)
                }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals("D$sides", it.findViewById<TextView>(R.id.current_die).text.toString())
                    assertHistoryColors(it)
                }
            }
        }
    }

    @Test fun holdPreviewsWithoutHistoryAndReleaseCommitsExactlyOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom(6)
            val down = SystemClock.uptimeMillis()
            scenario.onActivity {
                it.rollRandom = random
                touch(it, down, MotionEvent.ACTION_DOWN)
            }
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 180)
            scenario.onActivity {
                assertEquals(0, random.calls)
                assertEquals(0, visibleRows(it).size)
                assertTrue(it.findViewById<TextView>(R.id.result).text.toString().toInt() in 1..20)
                assertFalse(it.findViewById<View>(R.id.die_slider).isEnabled)
                touch(it, down, MotionEvent.ACTION_UP)
            }
            SystemClock.sleep(150)
            scenario.onActivity {
                assertEquals(1, random.calls)
                assertResult(it, "6", R.color.result)
                assertEquals(0, visibleRows(it).size)
                assertTrue(it.findViewById<View>(R.id.die_slider).isEnabled)
            }
        }
    }

    @Test fun cancelOrDragOutsideHeldButtonDoesNotRollAndRestoresCommittedResult() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom(1)
            scenario.onActivity {
                it.rollRandom = random
                it.findViewById<View>(R.id.roll_button).performClick()
            }
            for (action in listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_MOVE)) {
                val down = SystemClock.uptimeMillis()
                scenario.onActivity { touch(it, down, MotionEvent.ACTION_DOWN) }
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
                scenario.onActivity {
                    touch(it, down, action, outside = true)
                    touch(it, down, MotionEvent.ACTION_UP)
                    assertEquals(1, random.calls)
                    assertResult(it, "1", R.color.critical_failure)
                    assertTrue(it.findViewById<View>(R.id.die_slider).isEnabled)
                    assertEquals(0, visibleRows(it).size)
                }
            }
        }
    }

    @Test fun recreationDuringHoldCancelsPreviewAndDoesNotCommit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom(20)
            scenario.onActivity {
                it.rollRandom = random
                it.findViewById<View>(R.id.roll_button).performClick()
                touch(it, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN)
            }
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
            scenario.recreate()
            scenario.onActivity {
                assertEquals(1, random.calls)
                assertResult(it, "20", R.color.critical_success)
                assertEquals("D20", it.findViewById<TextView>(R.id.current_die).text.toString())
                assertTrue(it.findViewById<View>(R.id.die_slider).isEnabled)
                assertEquals(0, visibleRows(it).size)
            }
        }
    }

    @Test fun shortTouchCommitsOnceAndClearResetsLabelColorsAndFeedback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom(1)
            val down = SystemClock.uptimeMillis()
            scenario.onActivity {
                it.rollRandom = random
                touch(it, down, MotionEvent.ACTION_DOWN)
                touch(it, down, MotionEvent.ACTION_UP)
            }
            SystemClock.sleep(150)
            scenario.onActivity {
                assertEquals(1, random.calls)
                assertResult(it, "1", R.color.critical_failure)
                it.findViewById<View>(R.id.clear_button).performClick()
                assertResult(it, "", R.color.result)
                assertEquals("", it.findViewById<TextView>(R.id.current_die).text.toString())
                assertEquals(0f, it.findViewById<View>(R.id.result).translationX)
            }
        }
    }

    @Test fun accessibilityActionsEachCommitOneRollWithoutWaitingForTouchRelease() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val random = FixedRandom(4)
            scenario.onActivity {
                it.rollRandom = random
                val button = it.findViewById<View>(R.id.roll_button)
                assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                assertEquals(1, random.calls)
                assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                assertEquals(2, random.calls)
                assertResult(it, "4", R.color.result)
                assertTrue(it.findViewById<View>(R.id.die_slider).isEnabled)
            }
        }
    }

    @Test fun disabledAnimationsStillAllowHeldReleaseWithoutScramblingOrShake() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val setting = Settings.Global.ANIMATOR_DURATION_SCALE
        val previous = Settings.Global.getString(instrumentation.targetContext.contentResolver, setting)
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
        }
        try {
            shell("settings put global $setting 0")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val random = FixedRandom(1)
                val down = SystemClock.uptimeMillis()
                scenario.onActivity {
                    it.rollRandom = random
                    touch(it, down, MotionEvent.ACTION_DOWN)
                }
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
                scenario.onActivity {
                    assertEquals(0, random.calls)
                    assertEquals("", it.findViewById<TextView>(R.id.result).text.toString())
                    touch(it, down, MotionEvent.ACTION_UP)
                    assertEquals(1, random.calls)
                    assertResult(it, "1", R.color.critical_failure)
                    assertEquals(0f, it.findViewById<View>(R.id.result).translationX)
                }
            }
        } finally {
            shell("settings put global $setting ${previous ?: "1"}")
            SystemClock.sleep(200)
        }
    }
    @Test fun maximumGlintsInsideTheNumeralThenReturnsToUnchangedGold() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var baseline: Bitmap
            lateinit var highlighted: Bitmap
            lateinit var settled: Bitmap
            fun capture(activity: MainActivity): Bitmap {
                val view = activity.findViewById<RollResultView>(R.id.result)
                return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }
            }
            scenario.onActivity {
                it.rollRandom = FixedRandom(20)
                it.findViewById<View>(R.id.roll_button).performClick()
            }
            SystemClock.sleep(650)
            scenario.onActivity {
                baseline = capture(it)
                it.findViewById<View>(R.id.roll_button).performClick()
            }
            SystemClock.sleep(240)
            scenario.onActivity { highlighted = capture(it) }
            SystemClock.sleep(400)
            scenario.onActivity {
                settled = capture(it)
                assertResult(it, "20", R.color.critical_success)
            }
            assertFalse("Glint must visibly cross the text", baseline.sameAs(highlighted))
            assertTrue("The settled gold must be unchanged", baseline.sameAs(settled))
            baseline.recycle()
            highlighted.recycle()
            settled.recycle()
        }
    }
    private fun touch(activity: MainActivity, down: Long, action: Int, outside: Boolean = false) {
        val button = activity.findViewById<View>(R.id.roll_button)
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
            if (outside) -20f else button.width / 2f, button.height / 2f, 0)
        button.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun assertResult(activity: MainActivity, value: String, color: Int) {
        val result = activity.findViewById<TextView>(R.id.result)
        assertEquals(value, result.text.toString())
        assertEquals(ContextCompat.getColor(activity, color), result.currentTextColor)
    }

    private fun assertHistoryColors(activity: MainActivity) {
        val rows = visibleRows(activity)
        assertEquals(2, rows.size)
        assertEquals(ContextCompat.getColor(activity, R.color.critical_failure),
            rows[0].findViewById<TextView>(R.id.value).currentTextColor)
        assertEquals(ContextCompat.getColor(activity, R.color.critical_success),
            rows[1].findViewById<TextView>(R.id.value).currentTextColor)
    }

    private fun visibleRows(activity: MainActivity): List<View> {
        val rows = activity.findViewById<LinearLayout>(R.id.history_rows)
        return (0 until rows.childCount).map(rows::getChildAt).filter { it.visibility == View.VISIBLE }
    }
}
