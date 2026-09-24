package com.kieslingdev.simpledice

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.core.content.ContextCompat
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveActivityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearArchive() {
        DiceStore(context).use { it.clearAll() }
    }

    private fun seed(count: Int): List<Roll> = DiceStore(context).use { store ->
        (1..count).map { value ->
            val result = (value - 1) % 20 + 1
            store.recordRoll(Roll(sides = 20, value = result, timestamp = 1_800_000_000_000L + value), 20)
        }
    }

    @Test
    fun selectingRangeShowsItsSumDeletesThoseRowsAndPersistsAfterReopen() {
        val saved = seed(8)
        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                tapResult(list, 0)
                assertEquals(setOf(saved[7].id), list.selectedIds())
                tapResult(list, 0)
                assertTrue(list.selectedIds().isEmpty())
                tapResult(list, 0)
                tapResult(list, 2)
                assertEquals(setOf(saved[5].id, saved[6].id, saved[7].id), list.selectedIds())
                assertEquals("21", activity.findViewById<android.widget.TextView>(R.id.archive_sum).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.archive_footer).visibility)
                val first = list.getChildAt(0) as ArchiveRowView
                val last = list.getChildAt(2)
                val sum = activity.findViewById<View>(R.id.archive_sum)
                val delete = activity.findViewById<View>(R.id.archive_delete)
                assertEquals((first.top + last.bottom) / 2f, sum.y + sum.layoutParams.height / 2f, 2f)
                assertTrue(delete.x + delete.width / 2f < first.left + first.valueStart())
                assertTrue(sum.x + sum.layoutParams.width / 2f > first.left + first.valueEnd())
                activity.findViewById<View>(R.id.archive_delete).performClick()
                assertTrue(list.selectedIds().isEmpty())
            }
        }

        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                assertEquals(5, list.count)
                assertEquals(saved[4].id, list.getItemIdAtPosition(0))
                assertEquals(5, DiceStore(context).use { it.loadState().archive.size })
            }
        }
    }

    @Test
    fun valuesAreNeutralAndCenteredAndOffscreenSelectionUsesFooter() {
        seed(60)
        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                val row = list.getChildAt(0) as ArchiveRowView
                val value = row.getChildAt(1) as TextView
                assertEquals(ContextCompat.getColor(activity, R.color.result), value.currentTextColor)
                assertEquals((row.dieEnd() + row.timestampStart()) / 2f,
                    (row.valueStart() + row.valueEnd()) / 2f, 1f)
                tapResult(list, 0)
            }
            SystemClock.sleep(150)
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                list.setSelection(35)
            }
            SystemClock.sleep(250)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                tapResult(list, list.firstVisiblePosition)
                assertTrue("Selection size=${list.selectedIds().size}, first visible=${list.firstVisiblePosition}", list.selectedIds().size > 30)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.archive_footer).visibility)
                val total = DiceStore(context).use { store ->
                    store.loadState().archive.filter { it.id in list.selectedIds() }.sumOf { it.value }
                }
                assertEquals(total.toString(), activity.findViewById<TextView>(R.id.archive_sum).text.toString())
                val sum = activity.findViewById<View>(R.id.archive_sum)
                val delete = activity.findViewById<View>(R.id.archive_delete)
                val density = activity.resources.displayMetrics.density
                assertEquals(list.width / 2f, sum.x + sum.layoutParams.width / 2f, 1f)
                assertEquals(list.width - 12 * density, delete.x + delete.layoutParams.width, 2f)
                assertTrue(delete.y >= list.height - 64 * density)
                list.clearSelection()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.archive_footer).visibility)
            }
        }
    }

    @Test
    fun rightGapDragSelectsInsteadOfScrolling() {
        seed(40)
        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                val row = list.getChildAt(0) as ArchiveRowView
                val x = row.left + (row.valueEnd() + row.timestampStart()) / 2f
                val down = SystemClock.uptimeMillis()
                fun touch(action: Int, y: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    list.dispatchTouchEvent(event)
                    event.recycle()
                }
                touch(MotionEvent.ACTION_DOWN, row.top + row.height / 2f)
                val end = list.getChildAt(3)
                touch(MotionEvent.ACTION_MOVE, end.top + end.height / 2f)
                touch(MotionEvent.ACTION_UP, end.top + end.height / 2f)
                assertEquals(4, list.selectedIds().size)
                assertEquals(0, list.firstVisiblePosition)
            }
        }
    }

    @Test
    fun timestampColumnSwipeScrollsWithoutSelectingRows() {
        seed(40)
        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<ArchiveListView>(R.id.archive_list)
                tapResult(list, 0)
                val selectedBeforeScroll = list.selectedIds()
                val downTime = SystemClock.uptimeMillis()
                val x = list.width * .82f
                val y = list.height * .78f
                fun motion(action: Int, atY: Float, time: Long) {
                    val event = MotionEvent.obtain(downTime, time, action, x, atY, 0)
                    list.dispatchTouchEvent(event)
                    event.recycle()
                }
                motion(MotionEvent.ACTION_DOWN, y, SystemClock.uptimeMillis())
                motion(MotionEvent.ACTION_MOVE, y - list.height * .6f, SystemClock.uptimeMillis() + 60)
                motion(MotionEvent.ACTION_UP, y - list.height * .6f, SystemClock.uptimeMillis() + 120)
                assertTrue(list.firstVisiblePosition > 0)
                assertEquals(selectedBeforeScroll, list.selectedIds())
            }
        }
    }

    private fun tapResult(list: ArchiveListView, position: Int) {
        val child = list.getChildAt(position - list.firstVisiblePosition)
        val x = list.width * .3f
        val y = child.top + child.height / 2f
        val down = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        list.dispatchTouchEvent(downEvent)
        downEvent.recycle()
        val up = SystemClock.uptimeMillis() + 50
        val upEvent = MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x, y, 0)
        list.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }
}
