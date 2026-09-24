package com.kieslingdev.simpledice

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ThemeAndSelectionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() {
        DiceStore(context).use { store ->
            store.clearAll()
            store.saveSettings(RollSettings())
            var state = DiceState()
            repeat(5) {
                state = state.roll(Random(it))
                store.recordRoll(state.current!!, 20)
            }
        }
    }

    @Test fun menuHasTwoModesCenteredLabelsAndDarkModeSurvivesRelaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.menu_button).performClick()
                val dialog = activity.settingsDialog!!
                assertEquals("Menu", dialog.findViewById<TextView>(R.id.settings_title)!!.text.toString())
                assertEquals("Instant Roll (hold to animate)",
                    (dialog.findViewById<RadioGroup>(R.id.roll_modes)!!.getChildAt(0) as TextView).text.toString())
                assertEquals(2, dialog.findViewById<RadioGroup>(R.id.roll_modes)!!.childCount)
                listOf(R.id.settings_title, R.id.duration_label, R.id.duration_hint).forEach { id ->
                    assertEquals(Gravity.CENTER, dialog.findViewById<TextView>(id)!!.gravity)
                }
                dialog.findViewById<SwitchCompat>(R.id.dark_mode)!!.isChecked = true
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(Configuration.UI_MODE_NIGHT_YES,
                    activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                assertEquals(5, DiceStore(context).use { it.loadState().archive.size })
                assertTrue(DiceStore(context).use { it.loadSettings().darkMode })
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(Configuration.UI_MODE_NIGHT_YES,
                    it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                assertEquals(ContextCompat.getColor(it, R.color.result),
                    it.findViewById<TextView>(R.id.result).currentTextColor)
            }
        }
    }

    @Test fun rangeHasOneOuterFrameAndCanBeClearedByRetappingOrBlankSpace() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val selection = activity.findViewById<SelectionHistoryView>(R.id.history_selection)
                val container = activity.findViewById<LinearLayout>(R.id.history_rows)
                val rows = (0 until container.childCount).map(container::getChildAt).filter { it.visibility == View.VISIBLE }
                rows[0].performClick()
                rows[1].performClick()
                val bitmap = Bitmap.createBitmap(selection.width, selection.height, Bitmap.Config.ARGB_8888)
                selection.draw(Canvas(bitmap))
                val seam = rows[0].bottom
                // Between selected rows there is fill, not another horizontal gold edge.
                assertEquals(ContextCompat.getColor(activity, R.color.selection_fill), bitmap.getPixel(selection.width / 2, seam))
                bitmap.recycle()
                fun tap(x: Float, y: Float) {
                    val time = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(time, time, action, x, y, 0)
                        selection.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
                tap(selection.width / 2f, rows[0].top + rows[0].height / 2f)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.selection_sum).visibility)
                rows[0].performClick()
                tap(selection.width / 2f, 0f)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.selection_sum).visibility)
                rows[0].performClick()
                val time = SystemClock.uptimeMillis()
                val location = IntArray(2)
                activity.findViewById<View>(R.id.menu_button).getLocationOnScreen(location)
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(time, time, action, 2f, location[1].toFloat(), 0)
                    activity.dispatchTouchEvent(event)
                    event.recycle()
                }
                assertEquals(View.GONE, activity.findViewById<View>(R.id.selection_sum).visibility)
            }
        }
    }

    @Test fun legacyHoldPreferenceLoadsAsInstantWithHoldAndRetainsRolls() {
        val database = context.openOrCreateDatabase("dice_history.db", 0, null)
        database.execSQL("UPDATE settings SET value = 'HOLD' WHERE key = 'roll_mode'")
        database.close()
        DiceStore(context).use {
            assertEquals(RollMode.INSTANT, it.loadSettings().mode)
            assertEquals(5, it.loadState().archive.size)
        }
    }
}
