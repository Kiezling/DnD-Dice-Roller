package com.kieslingdev.simpledice

import android.os.Bundle
import android.content.Intent
import android.view.MotionEvent
import androidx.appcompat.widget.SwitchCompat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kieslingdev.simpledice.databinding.ActivityMainBinding
import com.kieslingdev.simpledice.databinding.DieChoiceBinding
import com.kieslingdev.simpledice.databinding.HistoryRowBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var choices: List<DieChoiceBinding>
    private lateinit var rows: List<HistoryRowBinding>
    private lateinit var store: DiceStore
    internal var settingsDialog: AlertDialog? = null
        private set
    private var reopenSettings = false
    private var state = DiceState()
    private var settings = RollSettings()
    internal var rollRandom: Random = diceRandom
    private var heldDie: Int? = null
    private var timedRoll = false
    private val finishTimedRoll = Runnable {
        if (timedRoll) {
            stopScrambling()
            commitRoll()
        }
    }
    private val scramble = object : Runnable {
        override fun run() {
            val sides = heldDie ?: return
            binding.result.text = getString(R.string.number, diceRandom.nextInt(1, sides + 1))
            binding.result.postDelayed(this, 65L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeSupport.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeSupport.styleSystemBars(this, binding.root)
        binding.result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        ViewCompat.setTooltipText(binding.clearButton, getString(R.string.clear_history))
        ViewCompat.setTooltipText(binding.menuButton, getString(R.string.menu))
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        store = DiceStore(this)
        state = store.loadState()
        settings = store.loadSettings()
        choices = DICE.mapIndexed { index, sides ->
            DieChoiceBinding.inflate(layoutInflater, binding.diceChoices, true).apply {
                root.text = getString(R.string.die_label, sides)
                root.contentDescription = getString(R.string.select_die, sides)
                root.setOnClickListener {
                    root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    binding.dieSlider.progress = index
                }
            }
        }
        rows = List(HISTORY_SIZE) {
            HistoryRowBinding.inflate(layoutInflater, binding.historyRows, true)
        }.reversed()
        binding.dieSlider.max = DICE.lastIndex
        binding.dieSlider.progress = DICE.indexOf(state.selectedDie)
        binding.dieSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                state = state.copy(selectedDie = DICE[progress])
                store.saveSelectedDie(state.selectedDie)
                if (fromUser) seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                renderSelection()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        binding.rollButton.onHoldChanged = { holding ->
            if (holding) startScrambling() else stopScrambling()
        }
        binding.rollButton.setOnClickListener {
            if (!timedRoll) {
                if (settings.mode == RollMode.TIMED && binding.result.animationsEnabled()) {
                    timedRoll = true
                    startScrambling()
                    binding.rollButton.isEnabled = false
                    binding.result.postDelayed(finishTimedRoll, settings.durationSteps * 500L)
                } else commitRoll()
            }
        }
        binding.clearButton.setOnClickListener {
            cancelRoll()
            store.clearRecent()
            state = state.clear()
            renderRolls()
        }
        binding.historySelection.onDelete = { ids ->
            cancelRoll()
            store.deleteRolls(ids)
            state = state.deleteRolls(ids)
            renderRolls()
        }
        binding.menuButton.setOnClickListener { cancelRoll(); showSettings() }
        binding.historyButton.setOnClickListener {
            cancelRoll()
            startActivity(Intent(this, ArchiveActivity::class.java))
        }
        renderSelection()
        renderRolls()
        if (savedInstanceState?.getBoolean("settingsOpen") == true) binding.root.post { showSettings() }
    }

    private fun commitRoll() {
        binding.result.resetFeedback()
        val next = state.roll(rollRandom)
        store.recordRoll(next.current!!, next.selectedDie)
        state = next
        renderRolls()
        binding.result.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        state.current?.let { binding.result.showFeedback(it) }
    }

    private fun startScrambling() {
        heldDie = state.selectedDie
        binding.result.resetFeedback()
        binding.result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        binding.result.contentDescription = getString(R.string.rolling_die, state.selectedDie)
        binding.result.setTextColor(ContextCompat.getColor(this, R.color.result))
        binding.currentDie.text = getString(R.string.die_label, state.selectedDie)
        choices.forEach { it.root.isEnabled = false }
        binding.dieSlider.isEnabled = false
        if (binding.result.animationsEnabled()) scramble.run() else binding.result.text = ""
    }

    private fun stopScrambling() {
        heldDie = null
        timedRoll = false
        binding.result.removeCallbacks(scramble)
        binding.result.removeCallbacks(finishTimedRoll)
        binding.rollButton.isEnabled = true
        choices.forEach { it.root.isEnabled = true }
        binding.dieSlider.isEnabled = true
        renderRolls()
        binding.result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun cancelRoll() {
        binding.rollButton.cancelGesture()
        stopScrambling()
        binding.result.resetFeedback()
    }

    private fun renderSelection() {
        choices.forEachIndexed { index, choice -> choice.root.isSelected = DICE[index] == state.selectedDie }
        binding.dieSlider.contentDescription = getString(R.string.selected_die, state.selectedDie)
        binding.rollButton.holdEnabled = settings.mode == RollMode.INSTANT
        binding.rollButton.contentDescription = getString(R.string.roll_die, state.selectedDie) +
            if (settings.mode == RollMode.INSTANT) ". " + getString(R.string.hold_to_roll) else ""
    }

    private fun rollColor(roll: Roll?, normal: Int): Int = ContextCompat.getColor(this, when {
        roll?.isCriticalSuccess == true -> R.color.critical_success
        roll?.isCriticalFailure == true -> R.color.critical_failure
        else -> normal
    })

    private fun renderRolls() {
        binding.result.resetFeedback()
        val current = state.current
        binding.result.text = current?.let { getString(R.string.number, it.value) }.orEmpty()
        binding.currentDie.text = current?.let { getString(R.string.die_label, it.sides) }.orEmpty()
        binding.result.contentDescription = current?.let { getString(R.string.roll_result, it.sides, it.value) }
            ?: getString(R.string.no_rolls)
        binding.result.setTextColor(rollColor(current, R.color.result))
        binding.clearButton.isEnabled = current != null
        binding.clearButton.alpha = if (current == null) 0.38f else 1f
        val history = state.history
        rows.forEachIndexed { index, row ->
            val entry = history.getOrNull(index)
            row.root.visibility = if (entry == null) View.GONE else View.VISIBLE
            if (entry != null) {
                row.die.text = getString(R.string.die_label, entry.roll.sides)
                row.value.text = getString(R.string.number, entry.roll.value)
                row.value.setTextColor(ContextCompat.getColor(this, R.color.history_result))
                row.total.text = getString(R.string.number, entry.total)
                row.root.contentDescription = getString(R.string.history_entry, entry.roll.sides, entry.roll.value, entry.total)
            }
        }
        binding.historySelection.bind(rows.take(history.size).reversed().map { it.root }, history.reversed().map { it.roll })
        binding.historyScroll.post { binding.historyScroll.fullScroll(View.FOCUS_DOWN) }
        current?.let { binding.result.showFeedback(it, shake = false) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val modes = RadioGroup(this).apply { id = R.id.roll_modes }
        val labels = listOf(R.string.instant_roll, R.string.timed_roll)
        RollMode.entries.forEachIndexed { index, mode ->
            modes.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(labels[index])
                tag = mode
                minHeight = dp(48)
                isChecked = settings.mode == mode
            })
        }
        val duration = TextView(this).apply { id = R.id.duration_label; textSize = 16f; gravity = Gravity.CENTER }
        val hint = TextView(this).apply { id = R.id.duration_hint; setText(R.string.duration_hint); gravity = Gravity.CENTER }
        val slider = SeekBar(this).apply {
            id = R.id.roll_duration
            max = 9
            progress = settings.durationSteps - 1
            minimumHeight = dp(48)
        }
        fun updateDuration() {
            duration.text = getString(R.string.duration, settings.durationSteps / 2.0)
            slider.contentDescription = duration.text
            slider.isEnabled = settings.mode == RollMode.TIMED
            duration.alpha = if (slider.isEnabled) 1f else .45f
            hint.alpha = duration.alpha
        }
        modes.setOnCheckedChangeListener { group, checkedId ->
            settings = settings.copy(mode = group.findViewById<RadioButton>(checkedId).tag as RollMode)
            store.saveSettings(settings)
            renderSelection()
            updateDuration()
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                settings = settings.copy(durationSteps = progress + 1)
                store.saveSettings(settings)
                updateDuration()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        content.addView(modes)
        content.addView(duration, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        updateDuration()
        val darkMode = SwitchCompat(this).apply {
            id = R.id.dark_mode
            setText(R.string.dark_mode)
            isChecked = settings.darkMode
            minHeight = dp(48)
            setPadding(0, dp(8), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                settings = settings.copy(darkMode = checked)
                store.saveSettings(settings)
                reopenSettings = true
                settingsDialog?.dismiss()
                ThemeSupport.apply(this@MainActivity)
            }
        }
        content.addView(darkMode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val title = TextView(this).apply {
            id = R.id.settings_title
            setText(R.string.roll_settings)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.result))
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }
        settingsDialog = AlertDialog.Builder(this).setCustomTitle(title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null).show()
        settingsDialog?.setOnDismissListener {
            if (!isFinishing && !isDestroyed) state.current?.let { binding.result.showFeedback(it, shake = false) }
        }
        reopenSettings = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::binding.isInitialized && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val location = IntArray(2)
            binding.historyScroll.getLocationOnScreen(location)
            if (event.rawY < location[1]) binding.historySelection.clearSelection()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            state = store.loadState()
            settings = store.loadSettings()
            binding.dieSlider.progress = DICE.indexOf(state.selectedDie)
            renderSelection()
            renderRolls()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settingsOpen", reopenSettings || settingsDialog?.isShowing == true)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        cancelRoll()
        super.onPause()
    }

    override fun onDestroy() {
        settingsDialog?.dismiss()
        store.close()
        super.onDestroy()
    }
}
