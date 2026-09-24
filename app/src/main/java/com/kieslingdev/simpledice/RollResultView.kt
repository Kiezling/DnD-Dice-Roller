package com.kieslingdev.simpledice

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** A short sweep through the glyphs, leaving the result's original gold intact. */
class RollResultView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    private var feedback: Animator? = null
    private var glint: Float? = null
    private var glintShader: LinearGradient? = null
    private val glintMatrix = Matrix()
    private var glyphWidth = 0f
    private var bandWidth = 0f

    fun animationsEnabled(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    fun showFeedback(roll: Roll) {
        resetFeedback()
        if (!animationsEnabled()) return
        feedback = when {
            roll.isCriticalFailure -> ObjectAnimator.ofFloat(
                this, TRANSLATION_X, 0f, -5f * resources.displayMetrics.density,
                5f * resources.displayMetrics.density, -3f * resources.displayMetrics.density,
                3f * resources.displayMetrics.density, 0f
            ).apply { duration = 280 }
            roll.isCriticalSuccess -> {
                glyphWidth = paint.measureText(text.toString())
                bandWidth = textSize * 0.7f
                glintShader = LinearGradient(
                    -bandWidth, 0f, bandWidth, height.toFloat(),
                    intArrayOf(currentTextColor, 0xFFFFF4CC.toInt(), currentTextColor),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 480
                    addUpdateListener {
                        glint = it.animatedValue as Float
                        invalidate()
                    }
                }
            }
            else -> null
        }
        feedback?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                glint = null
                glintShader = null
                translationX = 0f
                invalidate()
            }
        })
        feedback?.start()
    }

    fun resetFeedback() {
        feedback?.cancel()
        feedback = null
        glint = null
        glintShader = null
        translationX = 0f
        paint.shader = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        glint?.let { progress ->
            val contentWidth = width - compoundPaddingLeft - compoundPaddingRight
            val left = (contentWidth - glyphWidth) / 2f - bandWidth
            glintMatrix.setTranslate(left + (glyphWidth + 2 * bandWidth) * progress, 0f)
            glintShader?.setLocalMatrix(glintMatrix)
            paint.shader = glintShader
        }
        super.onDraw(canvas)
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        resetFeedback()
        super.onDetachedFromWindow()
    }
}
