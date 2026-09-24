package com.kieslingdev.simpledice

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kieslingdev.simpledice.databinding.ActivityMainBinding
import com.kieslingdev.simpledice.databinding.DieChoiceBinding
import com.kieslingdev.simpledice.databinding.HistoryRowBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var choices: List<DieChoiceBinding>
    private lateinit var rows: List<HistoryRowBinding>
    private var state = DiceState()
    internal var rollRandom: Random = Random.Default
    private var heldDie: Int? = null
    private val scramble = object : Runnable {
        override fun run() {
            val sides = heldDie ?: return
            binding.result.text = getString(R.string.number, Random.nextInt(1, sides + 1))
            binding.result.postDelayed(this, 65L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        binding.result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        ViewCompat.setTooltipText(binding.clearButton, getString(R.string.clear_history))
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        state = savedInstanceState?.let(::restoreState) ?: DiceState()
        choices = DICE.mapIndexed { index, sides ->
            DieChoiceBinding.inflate(layoutInflater, binding.diceChoices, true).apply {
                root.text = getString(R.string.die_label, sides)
                root.contentDescription = getString(R.string.select_die, sides)
                root.setOnClickListener { binding.dieSlider.progress = index }
            }
        }
        // Add oldest at the top so recent rolls stay next to the current result.
        rows = List(HISTORY_SIZE) {
            HistoryRowBinding.inflate(layoutInflater, binding.historyRows, true)
        }.reversed()
        binding.dieSlider.max = DICE.lastIndex
        binding.dieSlider.progress = DICE.indexOf(state.selectedDie)
        binding.dieSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                state = state.copy(selectedDie = DICE[progress])
                renderSelection()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        binding.rollButton.onHoldChanged = { holding ->
            if (holding) startScrambling() else stopScrambling()
        }
        binding.rollButton.setOnClickListener {
            binding.result.resetFeedback()
            state = state.roll(rollRandom)
            renderRolls()
            // The View API respects the user's system haptic preference, with no permission.
            binding.result.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            state.current?.let(binding.result::showFeedback)
        }
        binding.clearButton.setOnClickListener {
            binding.rollButton.cancelGesture()
            binding.result.resetFeedback()
            state = state.clear()
            renderRolls()
        }
        renderSelection()
        renderRolls()
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
        binding.result.removeCallbacks(scramble)
        choices.forEach { it.root.isEnabled = true }
        binding.dieSlider.isEnabled = true
        renderRolls()
        binding.result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun renderSelection() {
        choices.forEachIndexed { index, choice ->
            choice.root.isSelected = DICE[index] == state.selectedDie
        }
        binding.dieSlider.contentDescription = getString(R.string.selected_die, state.selectedDie)
        binding.rollButton.contentDescription = getString(R.string.roll_die, state.selectedDie) + ". " + getString(R.string.hold_to_roll)
    }

    private fun rollColor(roll: Roll?, normal: Int): Int = ContextCompat.getColor(this, when {
        roll?.isCriticalSuccess == true -> R.color.critical_success
        roll?.isCriticalFailure == true -> R.color.critical_failure
        else -> normal
    })

    private fun renderRolls() {
        val current = state.current
        binding.result.text = current?.let { getString(R.string.number, it.value) }.orEmpty()
        binding.currentDie.text = current?.let { getString(R.string.die_label, it.sides) }.orEmpty()
        binding.result.contentDescription = current?.let {
            getString(R.string.roll_result, it.sides, it.value)
        } ?: getString(R.string.no_rolls)
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
                row.value.setTextColor(rollColor(entry.roll, R.color.history_result))
                row.total.text = getString(R.string.number, entry.total)
                row.root.contentDescription = getString(
                    R.string.history_entry, entry.roll.sides, entry.roll.value, entry.total
                )
            }
        }
        binding.historyScroll.post { binding.historyScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onPause() {
        binding.rollButton.cancelGesture()
        binding.result.resetFeedback()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selectedDie", state.selectedDie)
        outState.putIntArray("sides", state.rolls.map { it.sides }.toIntArray())
        outState.putIntArray("values", state.rolls.map { it.value }.toIntArray())
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(saved: Bundle): DiceState {
        val sides = saved.getIntArray("sides") ?: intArrayOf()
        val values = saved.getIntArray("values") ?: intArrayOf()
        return DiceState(
            selectedDie = saved.getInt("selectedDie", 20),
            rolls = sides.zip(values).map { (die, value) -> Roll(die, value) }
        )
    }
}
