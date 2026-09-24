package com.kieslingdev.simpledice

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
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
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat

/** Quiet repeating feedback, clipped to the glyphs and stopped when the result leaves view. */
class RollResultView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {
    private var feedback: Animator? = null
    private var glint: Float? = null
    private var glintShader: LinearGradient? = null
    private val glintMatrix = Matrix()
    private var glyphWidth = 0f
    private var bandWidth = 0f
    private var pulseColor: Int? = null
    private var pulseShader: LinearGradient? = null

    fun animationsEnabled(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    fun showFeedback(roll: Roll, shake: Boolean = true) {
        resetFeedback()
        if (!animationsEnabled()) return
        feedback = when {
            roll.isCriticalFailure -> {
                val pulse = ValueAnimator.ofObject(ArgbEvaluator(), currentTextColor,
                    ContextCompat.getColor(context, R.color.critical_failure_deep)).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener {
                        setPulseColor(it.animatedValue as Int)
                    }
                }
                if (shake) AnimatorSet().apply {
                    val flashes = ValueAnimator.ofObject(ArgbEvaluator(), currentTextColor,
                        ContextCompat.getColor(context, R.color.critical_failure_flash), currentTextColor).apply {
                        duration = 250
                        repeatCount = 1
                        interpolator = LinearInterpolator()
                        addUpdateListener { setPulseColor(it.animatedValue as Int) }
                    }
                    val intro = AnimatorSet().apply { playTogether(flashes, ObjectAnimator.ofFloat(
                        this@RollResultView, TRANSLATION_X, 0f, -5f * resources.displayMetrics.density,
                        5f * resources.displayMetrics.density, -3f * resources.displayMetrics.density,
                        3f * resources.displayMetrics.density, 0f
                    ).apply { duration = 280 }) }
                    playSequentially(intro, pulse)
                } else pulse
            }
            roll.isCriticalSuccess -> {
                glyphWidth = paint.measureText(text.toString())
                bandWidth = textSize * 0.7f
                glintShader = LinearGradient(
                    -bandWidth, 0f, bandWidth, height.toFloat(),
                    intArrayOf(currentTextColor, 0xFFFFF4CC.toInt(), currentTextColor),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                val repeating = ValueAnimator.ofFloat(0f, 3f).apply {
                    duration = 3600
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        glint = (it.animatedValue as Float).takeIf { progress -> progress <= 1f }
                        invalidate()
                    }
                }
                if (shake) AnimatorSet().apply {
                    val intro = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 250
                        repeatCount = 1
                        interpolator = LinearInterpolator()
                        addUpdateListener {
                            glint = it.animatedValue as Float
                            invalidate()
                        }
                    }
                    playSequentially(intro, repeating)
                } else repeating
            }
            else -> null
        }
        feedback?.start()
    }

    private fun setPulseColor(color: Int) {
        if (color == pulseColor) return
        pulseColor = color
        pulseShader = LinearGradient(0f, 0f, 1f, 0f, color, color, Shader.TileMode.CLAMP)
        invalidate()
    }

    fun resetFeedback() {
        feedback?.cancel()
        feedback = null
        glint = null
        glintShader = null
        pulseColor = null
        pulseShader = null
        translationX = 0f
        paint.shader = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // TextView resets paint.color during its own draw; shade only the rendered glyphs.
        paint.shader = pulseShader
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
