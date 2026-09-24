package com.kieslingdev.simpledice

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ListView

/** A recycling list with result-column range selection and ordinary timestamp-column scrolling. */
class ArchiveListView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ListView(context, attrs) {
    private var anchorId: Long? = null
    private var endpointId: Long? = null
    private var draggingRange = false
    private var movedDuringGesture = false
    private var downPosition = INVALID_POSITION
    private var previousAnchorId: Long? = null
    private var previousEndpointId: Long? = null
    private var downY = 0f
    private var fingerScreenY = 0f
    var onSelectedIdsChanged: ((Set<Long>) -> Unit)? = null
    var onBlankTap: (() -> Unit)? = null

    init {
        divider = null
        dividerHeight = 0
        isVerticalScrollBarEnabled = true
        onItemClickListener = OnItemClickListener { _, _, position, _ -> selectByTap(position) }
    }

    private fun positionForId(id: Long?): Int = if (id == null) INVALID_POSITION else
        (0 until count).firstOrNull { getItemIdAtPosition(it) == id } ?: INVALID_POSITION

    fun selectedIds(): Set<Long> {
        val first = positionForId(anchorId)
        val last = positionForId(endpointId)
        if (first == INVALID_POSITION || last == INVALID_POSITION) return emptySet()
        return (minOf(first, last)..maxOf(first, last)).mapTo(linkedSetOf(), ::getItemIdAtPosition)
    }

    fun isPositionSelected(position: Int): Boolean {
        val first = positionForId(anchorId)
        val last = positionForId(endpointId)
        return first != INVALID_POSITION && last != INVALID_POSITION && position in minOf(first, last)..maxOf(first, last)
    }

    fun isFirstSelected(position: Int): Boolean {
        val first = positionForId(anchorId)
        val last = positionForId(endpointId)
        return first != INVALID_POSITION && last != INVALID_POSITION && position == minOf(first, last)
    }

    fun isLastSelected(position: Int): Boolean {
        val first = positionForId(anchorId)
        val last = positionForId(endpointId)
        return first != INVALID_POSITION && last != INVALID_POSITION && position == maxOf(first, last)
    }

    fun clearSelection() {
        anchorId = null
        endpointId = null
        draggingRange = false
        downPosition = INVALID_POSITION
        removeCallbacks(autoScroll)
        invalidateViews()
        onSelectedIdsChanged?.invoke(emptySet())
    }

    private fun selectByTap(position: Int) {
        if (position !in 0 until count) return
        val id = getItemIdAtPosition(position)
        if (isPositionSelected(position)) {
            clearSelection()
        } else {
            if (anchorId == null || positionForId(anchorId) == INVALID_POSITION) anchorId = id
            endpointId = id
            invalidateViews()
            onSelectedIdsChanged?.invoke(selectedIds())
        }
    }

    private fun startRangeDrag() {
        if (downPosition !in 0 until count) return
        anchorId = getItemIdAtPosition(downPosition)
        endpointId = anchorId
        draggingRange = true
        invalidateViews()
        onSelectedIdsChanged?.invoke(selectedIds())
        parent?.requestDisallowInterceptTouchEvent(true)
        postDelayed(autoScroll, 32L)
    }

    private fun moveEndpoint(y: Float) {
        if (count == 0) return
        val position = pointToPosition(width / 4, y.toInt())
        val endpoint = when {
            position != INVALID_POSITION -> position
            y < paddingTop -> firstVisiblePosition.coerceAtLeast(0)
            else -> (firstVisiblePosition + childCount - 1).coerceIn(0, count - 1)
        }
        endpointId = getItemIdAtPosition(endpoint)
        invalidateViews()
        onSelectedIdsChanged?.invoke(selectedIds())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val position = pointToPosition(event.x.toInt(), event.y.toInt())
                val row = getChildAt(position - firstVisiblePosition) as? ArchiveRowView
                val resultColumnRight = row?.let { it.left + it.timestampStart() } ?: width.toFloat()
                if (position == INVALID_POSITION) {
                    onBlankTap?.invoke()
                    return super.dispatchTouchEvent(event)
                }
                if (event.x <= resultColumnRight) {
                    downPosition = position
                    previousAnchorId = anchorId
                    previousEndpointId = endpointId
                    movedDuringGesture = false
                    downY = event.y
                    fingerScreenY = event.rawY
                    draggingRange = false
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (downPosition != INVALID_POSITION) {
                fingerScreenY = event.rawY
                if (!movedDuringGesture && kotlin.math.abs(event.y - downY) > touchSlop) {
                    movedDuringGesture = true
                    startRangeDrag()
                }
                if (draggingRange) moveEndpoint(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> if (downPosition != INVALID_POSITION) {
                if (draggingRange) {
                    moveEndpoint(event.y)
                    draggingRange = false
                    removeCallbacks(autoScroll)
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    selectByTap(downPosition)
                }
                downPosition = INVALID_POSITION
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> if (downPosition != INVALID_POSITION) {
                if (draggingRange) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    anchorId = previousAnchorId
                    endpointId = previousEndpointId
                    invalidateViews()
                    onSelectedIdsChanged?.invoke(selectedIds())
                }
                draggingRange = false
                downPosition = INVALID_POSITION
                removeCallbacks(autoScroll)
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun onDetachedFromWindow() {
        removeCallbacks(autoScroll)
        draggingRange = false
        downPosition = INVALID_POSITION
        super.onDetachedFromWindow()
    }

    private val autoScroll = object : Runnable {
        override fun run() {
            if (!draggingRange || count == 0) return
            val location = IntArray(2)
            getLocationOnScreen(location)
            val margin = (32 * resources.displayMetrics.density).toInt()
            val delta = when {
                fingerScreenY < location[1] + margin -> -maxOf(1, margin / 3)
                fingerScreenY > location[1] + height - margin -> maxOf(1, margin / 3)
                else -> 0
            }
            if (delta != 0) {
                scrollListBy(delta)
                moveEndpoint(fingerScreenY - location[1])
            }
            postDelayed(this, 32L)
        }
    }
}
