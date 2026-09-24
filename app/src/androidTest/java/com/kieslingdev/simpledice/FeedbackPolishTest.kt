package com.kieslingdev.simpledice

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FeedbackPolishTest {
    @Test fun criticalIntrosRenderTwiceWithinHalfSecondThenUseCorrectRepeatSpeed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DiceStore(context).use { it.saveSettings(RollSettings()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = activity.findViewById<RollResultView>(R.id.result)
                fun frame() = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                val feedbackField = RollResultView::class.java.getDeclaredField("feedback").apply { isAccessible = true }
                for (face in listOf(1, 20)) {
                    view.text = face.toString()
                    view.setTextColor(ContextCompat.getColor(activity, if (face == 1) R.color.critical_failure else R.color.critical_success))
                    view.resetFeedback()
                    val baseline = frame()
                    view.showFeedback(Roll(20, face))
                    val sequence = feedbackField.get(view) as AnimatorSet
                    val intro = if (face == 1) (sequence.childAnimations[0] as AnimatorSet).childAnimations[0] as ValueAnimator
                        else sequence.childAnimations[0] as ValueAnimator
                    assertEquals(250L, intro.duration)
                    assertEquals(1, intro.repeatCount)
                    intro.currentPlayTime = 125L
                    val first = frame()
                    intro.currentPlayTime = 375L
                    val second = frame()
                    assertFalse("Intro must visibly change glyphs for $face", baseline.sameAs(first))
                    assertTrue("Both intro peaks should match for $face", first.sameAs(second))
                    val repeating = sequence.childAnimations[1] as ValueAnimator
                    assertEquals(if (face == 1) 900L else 3600L, repeating.duration)
                    assertEquals(ValueAnimator.INFINITE, repeating.repeatCount)
                    view.resetFeedback()
                    val reset = frame()
                    assertTrue(baseline.sameAs(reset))
                    listOf(baseline, first, second, reset).forEach(Bitmap::recycle)
                    view.showFeedback(Roll(20, face), shake = false)
                    assertTrue("Resuming skips the intro", feedbackField.get(view) is ValueAnimator)
                    view.resetFeedback()
                }
            }
        }
    }

    @Test fun fullDatesAndTimesFitEveryMonthOnCompactAndLargeTextRows() {
        ActivityScenario.launch(ArchiveActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY)) {
                    for (scale in listOf(1f, 2f)) {
                        val config = Configuration(activity.resources.configuration).apply { fontScale = scale; setLocale(locale) }
                        val context = activity.createConfigurationContext(config)
                        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale)
                        val samples = (0..11).flatMap { month -> (0..23).map { hour ->
                            formatter.format(Calendar.getInstance().apply { set(2088, month, 28, hour, 58, 58) }.time)
                        } }
                        val row = ArchiveRowView(context, samples)
                        for (date in samples) {
                            row.bind("D100", "100", date, date, false, false, false)
                            val width = (320 * context.resources.displayMetrics.density).toInt()
                            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            row.layout(0, 0, width, row.measuredHeight)
                            val timestamp = row.getChildAt(2) as TextView
                            val layout = timestamp.layout
                            assertEquals("$locale $scale $date", date.length, layout.getLineEnd(layout.lineCount - 1))
                            for (line in 0 until layout.lineCount) {
                                assertEquals(0, layout.getEllipsisCount(line))
                                assertTrue("Clipped $locale $scale $date", layout.getLineWidth(line) <= timestamp.width + 1)
                            }
                            assertTrue(timestamp.right <= row.width - row.paddingRight)
                            val value = row.getChildAt(1) as TextView
                            assertTrue(value.width >= value.paint.measureText("100"))
                        }
                    }
                }
            }
        }
    }

    @Test fun allTrashActionsAreWhiteInDarkMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DiceStore(context).use { it.saveSettings(RollSettings(darkMode = true)) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                assertEquals(Color.WHITE, activity.findViewById<ImageButton>(R.id.clear_button).imageTintList!!.defaultColor)
                assertEquals(Color.WHITE, activity.findViewById<ImageButton>(R.id.delete_selection).imageTintList!!.defaultColor)
            } }
            ActivityScenario.launch(ArchiveActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                for (id in listOf(R.id.clear_archive, R.id.archive_delete)) {
                    assertEquals(Color.WHITE, activity.findViewById<ImageButton>(id).imageTintList!!.defaultColor)
                }
            } }
        } finally {
            DiceStore(context).use { it.saveSettings(RollSettings()) }
        }
    }
}
