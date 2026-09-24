package com.kieslingdev.simpledice

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton

/** Keeps native click, keyboard, and accessibility behavior; a held release clicks once. */
class RollButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatButton(context, attrs) {
    var onHoldChanged: ((Boolean) -> Unit)? = null
    private var holding = false
    private var touchActive = false
    private var gestureCanceled = false

    init {
        // We provide one light haptic when the result commits, not a second long-press pulse.
        isHapticFeedbackEnabled = false
        setOnLongClickListener {
            if (touchActive && isPressed && !gestureCanceled) {
                holding = true
                onHoldChanged?.invoke(true)
            } else if (!touchActive) {
                // Accessibility and keyboard long-clicks have no touch release to await.
                performClick()
            }
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureCanceled = false
                touchActive = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelGesture()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x < 0 || event.x >= width || event.y < 0 || event.y >= height) {
                    cancelGesture()
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
            MotionEvent.ACTION_UP -> {
                touchActive = false
                val commitHold = holding && !gestureCanceled && isPressed
                stopHolding()
                if (gestureCanceled) return true
                val handled = super.onTouchEvent(event)
                if (commitHold) performClick()
                return handled
            }
        }
        return if (gestureCanceled) true else super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelGesture()
        super.onWindowFocusChanged(hasWindowFocus)
    }

    fun cancelGesture() {
        touchActive = false
        gestureCanceled = true
        cancelLongPress()
        isPressed = false
        stopHolding()
    }

    private fun stopHolding() {
        if (holding) {
            holding = false
            onHoldChanged?.invoke(false)
        }
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }
}
