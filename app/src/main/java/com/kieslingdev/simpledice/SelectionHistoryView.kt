package com.kieslingdev.simpledice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/** A direct touch chooses a contiguous range; reversing the drag contracts it. */
class SelectionHistoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private var rows = emptyList<View>()
    private var rolls = emptyList<Roll>()
    private var anchor = -1
    private var endpoint = -1
    private var dragging = false
    private var fingerScreenY = 0f
    private var downY = 0f
    private var tapToClear = false
    private var moved = false
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var onDelete: ((Set<Long>) -> Unit)? = null
    private val sum = TextView(context).apply {
        id = R.id.selection_sum
        textSize = 18f
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.selection_label)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        setTextColor(ContextCompat.getColor(context, R.color.result))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }
    private val delete = ImageButton(context).apply {
        id = R.id.delete_selection
        setImageResource(R.drawable.ic_delete_history)
        imageTintList = ContextCompat.getColorStateList(context, R.color.trash)
        background = android.graphics.drawable.InsetDrawable(ContextCompat.getDrawable(context, R.drawable.selection_label), dp(12))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = context.getString(R.string.delete_selection)
        visibility = GONE
        setOnClickListener { onDelete?.invoke(selectedIndices().map { rolls[it].id }.toSet()) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onFinishInflate() {
        super.onFinishInflate()
        addView(sum, LayoutParams(dp(72), dp(48)))
        addView(delete, LayoutParams(dp(48), dp(48)))
    }

    fun bind(views: List<View>, values: List<Roll>) {
        clearSelection()
        rows = views
        rolls = values
        rows.forEachIndexed { index, row ->
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener {
                // Keyboard / TalkBack taps extend the same contiguous range.
                if (index in selectedIndices()) clearSelection()
                else {
                    if (anchor < 0) anchor = index
                    endpoint = index
                    renderSelection()
                }
            }
        }
    }

    private fun selectedIndices(): IntRange = if (anchor < 0) IntRange.EMPTY
        else minOf(anchor, endpoint)..maxOf(anchor, endpoint)

    fun clearSelection() {
        dragging = false
        removeCallbacks(autoScroll)
        anchor = -1
        endpoint = -1
        rows.forEach { it.background = null; it.isSelected = false }
        sum.visibility = GONE
        delete.visibility = GONE
        invalidate()
    }

    private fun rowAt(y: Float): Int = rows.indexOfFirst { y >= it.top && y <= it.bottom }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (delete.visibility == VISIBLE && event.x >= delete.x && event.x <= delete.x + delete.width &&
                event.y >= delete.y && event.y <= delete.y + delete.height) return super.dispatchTouchEvent(event)
            val index = rowAt(event.y)
            if (index < 0) {
                clearSelection()
                return super.dispatchTouchEvent(event)
            }
            tapToClear = index in selectedIndices()
            if (!tapToClear) {
                anchor = index
                endpoint = index
            }
            downY = event.y
            moved = false
            dragging = true
            fingerScreenY = event.rawY
            parent.requestDisallowInterceptTouchEvent(true)
            renderSelection()
            postDelayed(autoScroll, 32)
            return true
        }
        if (!dragging) return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                fingerScreenY = event.rawY
                if (!moved && kotlin.math.abs(event.y - downY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    moved = true
                    if (tapToClear) anchor = rowAt(downY)
                }
                if (moved) updateEndpoint(event.y)
            }
            MotionEvent.ACTION_UP -> {
                if (tapToClear && !moved) clearSelection() else updateEndpoint(event.y)
                dragging = false
                removeCallbacks(autoScroll)
                parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                clearSelection()
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun updateEndpoint(y: Float) {
        if (rows.isEmpty()) return
        endpoint = rowAt(y).takeIf { it >= 0 } ?: if (y < rows.first().top) 0 else rows.lastIndex
        renderSelection()
    }

    private val autoScroll = object : Runnable {
        override fun run() {
            if (!dragging) return
            val scroll = parent as? ScrollView ?: return
            val location = IntArray(2)
            scroll.getLocationOnScreen(location)
            val delta = when {
                fingerScreenY < location[1] + dp(40) -> -dp(8)
                fingerScreenY > location[1] + scroll.height - dp(40) -> dp(8)
                else -> 0
            }
            if (delta != 0 && moved) {
                scroll.scrollBy(0, delta)
                getLocationOnScreen(location)
                updateEndpoint(fingerScreenY - location[1])
            }
            postDelayed(this, 32)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (anchor >= 0) positionControls()
    }

    private fun renderSelection() {
        val selected = selectedIndices()
        rows.forEachIndexed { index, row ->
            row.isSelected = index in selected
            row.background = null
        }
        sum.text = context.getString(R.string.number, selected.sumOf { rolls[it].value })
        sum.contentDescription = context.getString(R.string.selection_total, sum.text)
        sum.visibility = VISIBLE
        delete.visibility = VISIBLE
        positionControls()
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val range = selectedIndices()
        if (!range.isEmpty()) {
            // One continuous frame, aligned to the Roll button's small outer inset.
            val border = resources.displayMetrics.density
            val bounds = RectF(dp(4).toFloat(), rows[range.first].top + border / 2,
                width - dp(4).toFloat(), rows[range.last].bottom - border / 2)
            selectionPaint.style = Paint.Style.FILL
            selectionPaint.color = ContextCompat.getColor(context, R.color.selection_fill)
            canvas.drawRoundRect(bounds, dp(4).toFloat(), dp(4).toFloat(), selectionPaint)
            selectionPaint.style = Paint.Style.STROKE
            selectionPaint.strokeWidth = border
            selectionPaint.color = ContextCompat.getColor(context, R.color.selection_border)
            canvas.drawRoundRect(bounds, dp(4).toFloat(), dp(4).toFloat(), selectionPaint)
        }
        super.dispatchDraw(canvas)
    }

    private fun positionControls() {
        val range = selectedIndices()
        if (range.isEmpty()) return
        val first = rows[range.first]
        val last = rows[range.last]
        val center = (first.top + first.height / 2f + last.top + last.height / 2f) / 2f
        // Measure the actual numerals so the controls fit the blank columns on narrow screens too.
        var leftStart = 0f
        var leftEnd = width.toFloat()
        var rightStart = 0f
        var rightEnd = width.toFloat()
        range.forEach { index ->
            val row = rows[index]
            val die = row.findViewById<TextView>(R.id.die)
            val value = row.findViewById<TextView>(R.id.value)
            val total = row.findViewById<TextView>(R.id.total)
            val valueCenter = row.left + value.left + value.width / 2f
            val halfValue = value.paint.measureText(value.text.toString()) / 2f
            leftStart = maxOf(leftStart, row.left + die.left + die.paint.measureText(die.text.toString()))
            leftEnd = minOf(leftEnd, valueCenter - halfValue)
            rightStart = maxOf(rightStart, valueCenter + halfValue)
            rightEnd = minOf(rightEnd, row.left + total.right - total.paint.measureText(total.text.toString()))
        }
        val labelWidth = (rightEnd - rightStart - dp(6)).toInt().coerceIn(dp(24), dp(72))
        if (sum.layoutParams.width != labelWidth) {
            sum.layoutParams = sum.layoutParams.apply { width = labelWidth }
        }
        sum.x = (rightStart + rightEnd) / 2f - sum.width / 2f
        delete.x = (leftStart + leftEnd) / 2f - delete.width / 2f
        sum.y = center - sum.height / 2f
        delete.y = center - delete.height / 2f
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(autoScroll)
        super.onDetachedFromWindow()
    }
}
