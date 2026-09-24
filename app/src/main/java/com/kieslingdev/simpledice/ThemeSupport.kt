package com.kieslingdev.simpledice

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat

object ThemeSupport {
    fun apply(activity: AppCompatActivity) {
        val dark = DiceStore(activity).use { it.loadSettings().darkMode }
        activity.delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }

    fun styleSystemBars(activity: AppCompatActivity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
