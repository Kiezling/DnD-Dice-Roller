package com.kieslingdev.simpledice

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import java.text.DateFormat
import java.util.Date
import java.util.Calendar

/** Full-screen browser for every persisted roll. */
class ArchiveActivity : AppCompatActivity() {
    private lateinit var store: DiceStore
    private lateinit var archiveList: ArchiveListView
    private lateinit var footer: View
    private lateinit var sum: TextView
    private lateinit var delete: ImageButton
    private lateinit var adapter: ArchiveAdapter
    private var archive: List<Roll> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeSupport.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)
        val root = findViewById<View>(R.id.archive_root)
        ThemeSupport.styleSystemBars(this, root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        store = DiceStore(this)
        archive = store.loadState().archive
        archiveList = findViewById(R.id.archive_list)
        archiveList.emptyView = findViewById(R.id.archive_empty)
        adapter = ArchiveAdapter()
        archiveList.adapter = adapter
        archiveList.onSelectedIdsChanged = { ids -> renderFooter(ids) }
        archiveList.onBlankTap = { archiveList.clearSelection() }
        archiveList.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: android.widget.AbsListView?, state: Int) = Unit
            override fun onScroll(view: android.widget.AbsListView?, first: Int, visible: Int, total: Int) {
                renderFooter(archiveList.selectedIds())
            }
        })

        findViewById<TextView>(R.id.archive_title).apply {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP
            )
            setOnClickListener { archiveList.clearSelection() }
        }
        findViewById<TextView>(R.id.archive_hint).setOnClickListener { archiveList.clearSelection() }
        findViewById<View>(R.id.archive_back).setOnClickListener { finish() }
        findViewById<View>(R.id.clear_archive).apply {
            isEnabled = archive.isNotEmpty()
            setOnClickListener { confirmClearAll() }
        }
        footer = findViewById(R.id.archive_footer)
        sum = findViewById(R.id.archive_sum)
        delete = findViewById(R.id.archive_delete)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(sum, 10, 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        sum.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        findViewById<View>(R.id.archive_content).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            renderFooter(archiveList.selectedIds())
        }
        delete.setOnClickListener { deleteSelection() }
        renderFooter(emptySet())
    }

    private fun renderFooter(ids: Set<Long>) {
        if (!::footer.isInitialized) return
        if (ids.isEmpty()) {
            footer.visibility = View.GONE
            sum.visibility = View.GONE
            delete.visibility = View.GONE
            archiveList.setPadding(archiveList.paddingLeft, 0, archiveList.paddingRight, 0)
            return
        }
        val total = archive.asSequence().filter { it.id in ids }.sumOf { it.value }
        sum.text = getString(R.string.number, total)
        sum.contentDescription = getString(R.string.selection_total, sum.text)
        val positions = archive.indices.filter { archive[it].id in ids }
        val first = archiveList.getChildAt(positions.first() - archiveList.firstVisiblePosition) as? ArchiveRowView
        val last = archiveList.getChildAt(positions.last() - archiveList.firstVisiblePosition) as? ArchiveRowView
        val fullyVisible = first != null && last != null && first.top >= 0 && last.bottom <= archiveList.height
        val visibleRows = (0 until archiveList.childCount).mapNotNull { archiveList.getChildAt(it) as? ArchiveRowView }
            .filter { row -> row.top >= (first?.top ?: 0) && row.bottom <= (last?.bottom ?: archiveList.height) }
        val leftStart = visibleRows.maxOfOrNull { it.left + it.dieEnd() } ?: 0f
        val leftEnd = visibleRows.minOfOrNull { it.left + it.valueStart() } ?: 0f
        val rightStart = visibleRows.maxOfOrNull { it.left + it.valueEnd() } ?: 0f
        val rightEnd = visibleRows.minOfOrNull { it.left + it.timestampStart() } ?: 0f
        // The footer also provides a readable fallback when large text leaves no room for controls.
        val inline = fullyVisible && leftEnd - leftStart >= dp(48) && rightEnd - rightStart >= dp(32)
        footer.visibility = if (inline) View.GONE else View.VISIBLE
        val bottomPadding = if (inline) 0 else dp(64)
        if (archiveList.paddingBottom != bottomPadding) {
            archiveList.setPadding(archiveList.paddingLeft, 0, archiveList.paddingRight, bottomPadding)
        }
        val labelWidth = if (inline) (rightEnd - rightStart - dp(4)).toInt().coerceIn(dp(28), dp(96)) else dp(120)
        if (sum.layoutParams.width != labelWidth) sum.layoutParams = sum.layoutParams.apply { width = labelWidth }
        val centerY = if (inline) (first!!.top + last!!.bottom) / 2f else archiveList.height - dp(32).toFloat()
        sum.x = if (inline) (rightStart + rightEnd - labelWidth) / 2f else archiveList.width / 2f - labelWidth / 2f
        delete.x = if (inline) (leftStart + leftEnd - dp(48)) / 2f else archiveList.width - dp(60).toFloat()
        sum.y = centerY - dp(24)
        delete.y = centerY - dp(24)
        sum.visibility = View.VISIBLE
        delete.visibility = View.VISIBLE
    }

    private fun deleteSelection() {
        val ids = archiveList.selectedIds()
        if (ids.isEmpty()) return
        val oldPosition = archiveList.firstVisiblePosition
        val oldTop = archiveList.getChildAt(0)?.top ?: 0
        store.deleteRolls(ids)
        archive = store.loadState().archive
        archiveList.clearSelection()
        adapter.notifyDataSetChanged()
        findViewById<View>(R.id.clear_archive).isEnabled = archive.isNotEmpty()
        if (archive.isNotEmpty()) {
            archiveList.setSelectionFromTop(oldPosition.coerceAtMost(archive.lastIndex), oldTop)
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_archive)
            .setMessage(R.string.confirm_clear_archive)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                store.clearAll()
                archive = store.loadState().archive
                archiveList.clearSelection()
                adapter.notifyDataSetChanged()
                findViewById<View>(R.id.clear_archive).isEnabled = archive.isNotEmpty()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && ::archiveList.isInitialized) {
            val location = IntArray(2)
            archiveList.getLocationOnScreen(location)
            if (event.rawY < location[1]) archiveList.clearSelection()
        }
        return super.dispatchTouchEvent(event)
    }

    private inner class ArchiveAdapter : BaseAdapter() {
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        // Include two-digit dates, every month and every localized hour/day-period.
        private val timestampSamples = (0..11).flatMap { month ->
            (0..23).map { hour ->
                val calendar = Calendar.getInstance().apply {
                    clear()
                    set(2088, month, 28, hour, 58, 58)
                }
                dateFormat.format(calendar.time)
            }
        }
        override fun getCount(): Int = archive.size
        override fun getItem(position: Int): Roll = archive[position]
        override fun getItemId(position: Int): Long = archive[position].id
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? ArchiveRowView) ?: ArchiveRowView(this@ArchiveActivity, timestampSamples)
            val roll = getItem(position)
            val date = dateFormat.format(Date(roll.timestamp))
            row.bind(
                die = getString(R.string.die_label, roll.sides),
                value = getString(R.string.number, roll.value),
                timestamp = date,
                description = getString(R.string.archive_row_description, roll.sides, roll.value, date),
                selected = archiveList.isPositionSelected(position),
                firstSelected = archiveList.isFirstSelected(position),
                lastSelected = archiveList.isLastSelected(position)
            )
            return row
        }
    }

    override fun onDestroy() {
        if (::store.isInitialized) store.close()
        super.onDestroy()
    }
}

/** Compact row whose selected-range fill is continuous and has only an outside border. */
internal class ArchiveRowView @JvmOverloads constructor(context: android.content.Context, timestampSamples: List<String> = emptyList()) : LinearLayout(context) {
    private val die = TextView(context)
    private val value = TextView(context)
    private val timestamp = TextView(context)
    private var selected = false
    private var firstSelected = false
    private var lastSelected = false
    private val fill = ContextCompat.getColor(context, R.color.selection_fill)
    private val border = ContextCompat.getColor(context, R.color.selection_border)
    private val density = resources.displayMetrics.density
    private val reservedTimestampWidth: Float

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(46)
        setPadding(dp(12), 0, dp(12), 0)
        isFocusable = false
        isClickable = false
        die.setTextColor(ContextCompat.getColor(context, R.color.secondary_text))
        die.textSize = 14f
        die.setTypeface(null, android.graphics.Typeface.BOLD)
        value.setTextColor(ContextCompat.getColor(context, R.color.result))
        value.textSize = 18f
        value.gravity = Gravity.CENTER
        value.setTypeface(null, android.graphics.Typeface.BOLD)
        timestamp.setTextColor(ContextCompat.getColor(context, R.color.secondary_text))
        timestamp.textSize = 12f
        timestamp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        // Keep the full timestamp readable. Compact screens / large fonts may wrap it.
        timestamp.isSingleLine = false
        val digits = timestampSamples.flatMap { it.filter(Char::isDigit).toList() }.distinct()
        val widestDigit = digits.maxByOrNull { timestamp.paint.measureText(it.toString()) } ?: '8'
        reservedTimestampWidth = timestampSamples.maxOfOrNull { sample ->
            timestamp.paint.measureText(sample.map { if (it.isDigit()) widestDigit else it }.joinToString(""))
        } ?: 0f
        addView(die, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(value, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(timestamp, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (value * density).toInt()

    fun bind(die: String, value: String, timestamp: String, description: String,
             selected: Boolean, firstSelected: Boolean, lastSelected: Boolean) {
        this.die.text = die
        this.value.text = value
        this.timestamp.text = timestamp
        contentDescription = description
        this.selected = selected
        this.firstSelected = firstSelected
        this.lastSelected = lastSelected
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val natural = maxOf(reservedTimestampWidth, timestamp.paint.measureText(timestamp.text.toString()))
        val dieWidth = kotlin.math.ceil(die.paint.measureText(die.text.toString())).toInt()
        val valueWidth = kotlin.math.ceil(value.paint.measureText("100")).toInt() + dp(24)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - dieWidth - valueWidth
        timestamp.layoutParams.width = minOf(kotlin.math.ceil(natural).toInt() + dp(8), available.coerceAtLeast(1))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun dieEnd(): Float = die.right.toFloat()
    fun valueStart(): Float = value.left + (value.width - value.paint.measureText(value.text.toString())) / 2f
    fun valueEnd(): Float = value.left + (value.width + value.paint.measureText(value.text.toString())) / 2f
    fun timestampStart(): Float = timestamp.left.toFloat()

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        if (selected) {
            val stroke = dp(1).toFloat()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = fill
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = border
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = stroke
            val half = stroke / 2f
            if (firstSelected) canvas.drawLine(half, half, width - half, half, paint)
            if (lastSelected) canvas.drawLine(half, height - half, width - half, height - half, paint)
            canvas.drawLine(half, if (firstSelected) half else 0f, half,
                if (lastSelected) height - half else height.toFloat(), paint)
            canvas.drawLine(width - half, if (firstSelected) half else 0f, width - half,
                if (lastSelected) height - half else height.toFloat(), paint)
        }
        super.dispatchDraw(canvas)
    }
}
