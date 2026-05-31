package com.sheetforge.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity(), SpreadsheetView.Listener {
    private val workbook = Workbook.sample()
    private val history = UndoStack()
    private lateinit var grid: SpreadsheetView
    private lateinit var nameBox: TextView
    private lateinit var formulaBar: EditText
    private lateinit var tabs: LinearLayout
    private lateinit var status: TextView
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshAll()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.canvas)
        }

        root.addView(topBar())
        root.addView(ribbon())
        root.addView(formulaRow())

        grid = SpreadsheetView(this, workbook).apply { listener = this@MainActivity }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        status = TextView(this).apply {
            setTextColor(Palette.textMuted)
            textSize = 12f
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        root.addView(status)
        setContentView(root)
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setBackgroundColor(Palette.excel)
        addView(TextView(context).apply {
            text = "SheetForge"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(action("Undo") { undo() })
        addView(action("Redo") { redo() })
        addView(action("Chart") { showChart() })
        addView(action("CSV") { showCsvDialog() })
    }

    private fun ribbon(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Palette.ribbon)
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(bar)
        }
        bar.addView(action("B") { mutate("Bold") { it.style = it.style.copy(bold = !it.style.bold) } })
        bar.addView(action("I") { mutate("Italic") { it.style = it.style.copy(italic = !it.style.italic) } })
        bar.addView(action("$") { mutate("Currency") { it.style = it.style.copy(numberFormat = NumberFormat.Currency) } })
        bar.addView(action("%") { mutate("Percent") { it.style = it.style.copy(numberFormat = NumberFormat.Percent) } })
        bar.addView(action("123") { mutate("Number") { it.style = it.style.copy(numberFormat = NumberFormat.General) } })
        bar.addView(action("Fill") { cycleFill() })
        bar.addView(action("Left") { mutate("Left") { it.style = it.style.copy(align = CellAlign.Left) } })
        bar.addView(action("Center") { mutate("Center") { it.style = it.style.copy(align = CellAlign.Center) } })
        bar.addView(action("Right") { mutate("Right") { it.style = it.style.copy(align = CellAlign.Right) } })
        bar.addView(action("Sort") { sortSelection() })
        bar.addView(action("Filter") { filterSelection() })
        bar.addView(action("+ Sheet") { addSheet() })
        return scroll
    }

    private fun formulaRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setBackgroundColor(Color.WHITE)

        nameBox = TextView(context).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Palette.text)
            setBackgroundColor(Palette.cellHeader)
        }
        addView(nameBox, LinearLayout.LayoutParams(dp(72), dp(42)))

        formulaBar = EditText(context).apply {
            setSingleLine(true)
            textSize = 15f
            setTextColor(Palette.text)
            setHintTextColor(Palette.textMuted)
            hint = "fx"
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitFormula()
                    true
                } else false
            }
        }
        addView(formulaBar, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(action("OK") { commitFormula() })
    }

    private fun action(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(if (label == "B" || label == "I") Palette.text else Color.WHITE)
        setPadding(dp(12), 0, dp(12), 0)
        minWidth = dp(48)
        setBackgroundColor(if (label == "B" || label == "I") Color.WHITE else Palette.excel)
        setOnClickListener { onClick() }
    }

    override fun onSelectionChanged(cell: CellRef) {
        val selected = workbook.activeSheet.cell(cell)
        nameBox.text = cell.label()
        formulaBar.setText(selected.raw)
        status.text = workbook.selectionSummary()
    }

    override fun onCellEdited(cell: CellRef, oldRaw: String, newRaw: String) {
        history.push(EditCommand(workbook.activeSheetIndex, cell, oldRaw, newRaw))
        refreshAll()
    }

    private fun commitFormula() {
        val cell = grid.selectedCell
        val sheet = workbook.activeSheet
        val old = sheet.cell(cell).raw
        val next = formulaBar.text.toString()
        if (old != next) {
            sheet.cell(cell).raw = next
            history.push(EditCommand(workbook.activeSheetIndex, cell, old, next))
            workbook.recalculate()
            grid.invalidate()
        }
        onSelectionChanged(cell)
    }

    private fun mutate(label: String, block: (Cell) -> Unit) {
        history.snapshot(workbook)
        workbook.activeSheet.cellsIn(grid.selection).forEach(block)
        workbook.recalculate()
        refreshAll()
        toast(label)
    }

    private fun cycleFill() {
        val colors = listOf(Color.WHITE, 0xFFE9F6EF.toInt(), 0xFFFFF4CC.toInt(), 0xFFE9F0FF.toInt(), 0xFFFFEAEA.toInt())
        mutate("Fill") { cell ->
            val index = colors.indexOf(cell.style.fill).let { if (it < 0) 0 else it }
            cell.style = cell.style.copy(fill = colors[(index + 1) % colors.size])
        }
    }

    private fun sortSelection() {
        val range = grid.selection.normalized()
        if (range.height < 2) return toast("Select two or more rows")
        history.snapshot(workbook)
        workbook.activeSheet.sortRange(range)
        refreshAll()
        toast("Sorted")
    }

    private fun filterSelection() {
        val range = grid.selection.normalized()
        val sheet = workbook.activeSheet
        val values = (range.top + 1..range.bottom)
            .mapNotNull { row -> sheet.cell(CellRef(row, range.left)).display.takeIf { it.isNotBlank() } }
            .distinct()
        if (values.isEmpty()) return toast("No values to filter")
        AlertDialog.Builder(this)
            .setTitle("Filter ${CellRef(range.top, range.left).label()}")
            .setItems(values.toTypedArray()) { _, which ->
                sheet.hiddenRows.clear()
                val keep = values[which]
                (range.top + 1..range.bottom).forEach { row ->
                    if (sheet.cell(CellRef(row, range.left)).display != keep) sheet.hiddenRows.add(row)
                }
                grid.invalidate()
                toast("Filtered by $keep")
            }
            .setNegativeButton("Clear") { _, _ ->
                sheet.hiddenRows.clear()
                grid.invalidate()
            }
            .show()
    }

    private fun showChart() {
        val chart = ChartView(this, workbook.activeSheet, grid.selection.normalized())
        AlertDialog.Builder(this)
            .setTitle("Quick chart")
            .setView(chart)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showCsvDialog() {
        val exportText = workbook.activeSheet.toCsv(grid.selection.normalized())
        val input = EditText(this).apply {
            setMinLines(8)
            gravity = Gravity.TOP
            setText(exportText)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("CSV import/export")
            .setView(ScrollView(this).apply { addView(input) })
            .setPositiveButton("Import at selection") { _, _ ->
                history.snapshot(workbook)
                workbook.activeSheet.importCsv(grid.selectedCell, input.text.toString())
                refreshAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addSheet() {
        val sheet = Sheet("Sheet ${workbook.sheets.size + 1}")
        workbook.sheets.add(sheet)
        workbook.activeSheetIndex = workbook.sheets.lastIndex
        refreshAll()
    }

    private fun undo() {
        if (history.undo(workbook)) refreshAll() else toast("Nothing to undo")
    }

    private fun redo() {
        if (history.redo(workbook)) refreshAll() else toast("Nothing to redo")
    }

    private fun refreshAll() {
        workbook.recalculate()
        refreshTabs()
        if (::grid.isInitialized) grid.invalidate()
        if (::formulaBar.isInitialized) onSelectionChanged(grid.selectedCell)
    }

    private fun refreshTabs() {
        tabs.removeAllViews()
        workbook.sheets.forEachIndexed { index, sheet ->
            tabs.addView(TextView(this).apply {
                text = sheet.name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                setTextColor(if (index == workbook.activeSheetIndex) Color.WHITE else Palette.text)
                setBackgroundColor(if (index == workbook.activeSheetIndex) Palette.excel else Color.TRANSPARENT)
                setOnClickListener {
                    workbook.activeSheetIndex = index
                    grid.resetScroll()
                    refreshAll()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

class SpreadsheetView(
    context: android.content.Context,
    private val workbook: Workbook
) : View(context) {
    interface Listener {
        fun onSelectionChanged(cell: CellRef)
        fun onCellEdited(cell: CellRef, oldRaw: String, newRaw: String)
    }

    var listener: Listener? = null
    var selectedCell = CellRef(0, 0)
        private set
    var selection = CellRange(selectedCell, selectedCell)
        private set

    private val rowHeader = dp(54)
    private val colHeader = dp(42)
    private val cellW = dp(104)
    private val cellH = dp(42)
    private var scrollXCells = 0
    private var scrollYCells = 0
    private var dragStart: CellRef? = null
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.grid; strokeWidth = 1f }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(14); color = Palette.text }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.cellHeader }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.excel
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sheet = workbook.activeSheet
        canvas.drawColor(Color.WHITE)
        drawHeaders(canvas)
        val rows = ((height - colHeader) / cellH) + 2
        val cols = ((width - rowHeader) / cellW) + 2
        var visualRow = 0
        var modelRow = scrollYCells
        while (visualRow < rows && modelRow < sheet.rowCount) {
            if (sheet.hiddenRows.contains(modelRow)) {
                modelRow++
                continue
            }
            drawRowHeader(canvas, visualRow, modelRow)
            for (c in 0 until cols) {
                val modelCol = c + scrollXCells
                if (modelCol >= sheet.colCount) continue
                drawCell(canvas, sheet, visualRow, modelRow, c, modelCol)
            }
            visualRow++
            modelRow++
        }
        drawSelection(canvas)
    }

    private fun drawHeaders(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), colHeader.toFloat(), headerPaint)
        canvas.drawRect(0f, 0f, rowHeader.toFloat(), height.toFloat(), headerPaint)
        for (c in 0..((width - rowHeader) / cellW) + 1) {
            val modelCol = c + scrollXCells
            val left = rowHeader + c * cellW
            canvas.drawRect(left.toFloat(), 0f, (left + cellW).toFloat(), colHeader.toFloat(), headerPaint)
            drawCentered(canvas, columnName(modelCol), left.toFloat(), 0f, cellW.toFloat(), colHeader.toFloat(), true)
        }
    }

    private fun drawRowHeader(canvas: Canvas, visualRow: Int, modelRow: Int) {
        val top = colHeader + visualRow * cellH
        canvas.drawRect(0f, top.toFloat(), rowHeader.toFloat(), (top + cellH).toFloat(), headerPaint)
        drawCentered(canvas, "${modelRow + 1}", 0f, top.toFloat(), rowHeader.toFloat(), cellH.toFloat(), true)
        canvas.drawLine(0f, top.toFloat(), width.toFloat(), top.toFloat(), gridPaint)
    }

    private fun drawCell(canvas: Canvas, sheet: Sheet, visualRow: Int, modelRow: Int, visualCol: Int, modelCol: Int) {
        val cell = sheet.cell(CellRef(modelRow, modelCol))
        val left = rowHeader + visualCol * cellW
        val top = colHeader + visualRow * cellH
        fillPaint.color = cell.style.fill
        fillPaint.style = Paint.Style.FILL
        canvas.drawRect(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat(), fillPaint)
        canvas.drawRect(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat(), gridPaint)

        textPaint.color = if (cell.error != null) Color.rgb(185, 28, 28) else Palette.text
        textPaint.textSize = sp(14)
        textPaint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            when {
                cell.style.bold && cell.style.italic -> android.graphics.Typeface.BOLD_ITALIC
                cell.style.bold -> android.graphics.Typeface.BOLD
                cell.style.italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
        )
        val value = cell.formattedDisplay()
        val x = when (cell.style.align) {
            CellAlign.Left -> left + dp(8).toFloat()
            CellAlign.Center -> left + cellW / 2f - textPaint.measureText(value) / 2f
            CellAlign.Right -> left + cellW - dp(8).toFloat() - textPaint.measureText(value)
        }
        val y = top + cellH / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.save()
        canvas.clipRect(left + dp(4), top, left + cellW - dp(4), top + cellH)
        canvas.drawText(value, x, y, textPaint)
        canvas.restore()
    }

    private fun drawSelection(canvas: Canvas) {
        val range = selection.normalized()
        val firstVisibleRow = visibleIndexOf(range.top) ?: return
        val lastVisibleRow = visibleIndexOf(range.bottom) ?: firstVisibleRow
        val left = rowHeader + (range.left - scrollXCells) * cellW
        val top = colHeader + firstVisibleRow * cellH
        val right = rowHeader + (range.right - scrollXCells + 1) * cellW
        val bottom = colHeader + (lastVisibleRow + 1) * cellH
        if (right <= rowHeader || bottom <= colHeader || left >= width || top >= height) return
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), selectedPaint)
    }

    private fun drawCentered(canvas: Canvas, label: String, x: Float, y: Float, w: Float, h: Float, bold: Boolean) {
        textPaint.color = Palette.textMuted
        textPaint.textSize = sp(12)
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        canvas.drawText(label, x + w / 2f - textPaint.measureText(label) / 2f, y + h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                panning = event.pointerCount > 1
                dragStart = hitCell(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                panning = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (panning) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) > cellW / 2) {
                        scrollXCells = (scrollXCells - dx.sign()).coerceIn(0, workbook.activeSheet.colCount - 1)
                        lastX = event.x
                    }
                    if (abs(dy) > cellH / 2) {
                        scrollYCells = (scrollYCells - dy.sign()).coerceIn(0, workbook.activeSheet.rowCount - 1)
                        lastY = event.y
                    }
                    invalidate()
                } else {
                    val end = hitCell(event.x, event.y)
                    val start = dragStart
                    if (start != null && end != null) {
                        selection = CellRange(start, end)
                        selectedCell = end
                        listener?.onSelectionChanged(end)
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = hitCell(event.x, event.y)
                if (!panning && hit != null && hit == dragStart) {
                    selectedCell = hit
                    selection = CellRange(hit, hit)
                    listener?.onSelectionChanged(hit)
                    showEditor(hit)
                }
                panning = false
                return true
            }
        }
        return true
    }

    private fun showEditor(cellRef: CellRef) {
        val cell = workbook.activeSheet.cell(cellRef)
        val input = EditText(context).apply {
            setText(cell.raw)
            setSelection(text.length)
            setSingleLine(true)
        }
        AlertDialog.Builder(context)
            .setTitle(cellRef.label())
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val old = cell.raw
                val next = input.text.toString()
                if (old != next) {
                    cell.raw = next
                    workbook.recalculate()
                    listener?.onCellEdited(cellRef, old, next)
                    invalidate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hitCell(x: Float, y: Float): CellRef? {
        if (x < rowHeader || y < colHeader) return null
        val col = ((x - rowHeader) / cellW).toInt() + scrollXCells
        val visualRow = ((y - colHeader) / cellH).toInt()
        val row = modelRowAtVisual(visualRow) ?: return null
        return CellRef(row, col)
    }

    private fun modelRowAtVisual(visual: Int): Int? {
        var seen = 0
        for (row in scrollYCells until workbook.activeSheet.rowCount) {
            if (workbook.activeSheet.hiddenRows.contains(row)) continue
            if (seen == visual) return row
            seen++
        }
        return null
    }

    private fun visibleIndexOf(modelRow: Int): Int? {
        var seen = 0
        for (row in scrollYCells until workbook.activeSheet.rowCount) {
            if (workbook.activeSheet.hiddenRows.contains(row)) continue
            if (row == modelRow) return seen
            seen++
        }
        return null
    }

    fun resetScroll() {
        scrollXCells = 0
        scrollYCells = 0
        selectedCell = CellRef(0, 0)
        selection = CellRange(selectedCell, selectedCell)
    }

    private fun Float.sign(): Int = if (this > 0) 1 else -1
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun sp(value: Int): Float = value * resources.displayMetrics.scaledDensity
}

class ChartView(
    context: android.content.Context,
    private val sheet: Sheet,
    private val range: CellRange
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.text; textSize = 32f }

    init {
        minimumHeight = (260 * resources.displayMetrics.density).toInt()
        setPadding(20, 20, 20, 20)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = range.normalized().cells()
            .mapNotNull { sheet.cell(it).numericOrNull() }
            .take(24)
        if (values.isEmpty()) {
            canvas.drawText("Select numeric cells to chart", 32f, height / 2f, text)
            return
        }
        val maxValue = values.maxOrNull()?.takeIf { it != 0.0 } ?: 1.0
        val gap = width / (values.size * 1.35f)
        val barW = gap * 0.8f
        values.forEachIndexed { index, value ->
            val left = 32f + index * gap
            val top = height - 48f - ((height - 92f) * (value / maxValue)).toFloat()
            paint.color = if (index % 2 == 0) Palette.excel else Palette.blue
            canvas.drawRoundRect(RectF(left, top, left + barW, height - 48f), 8f, 8f, paint)
        }
    }
}

data class Workbook(
    val sheets: MutableList<Sheet>,
    var activeSheetIndex: Int = 0
) {
    val activeSheet: Sheet get() = sheets[activeSheetIndex]

    fun recalculate() = sheets.forEach { it.recalculate(this) }

    fun selectionSummary(): String {
        val numbers = activeSheet.cells.values.mapNotNull { it.numericOrNull() }
        return if (numbers.isEmpty()) "Ready" else "Ready  Sum ${numbers.sum().short()}  Avg ${numbers.average().short()}"
    }

    fun deepCopy(): Workbook = Workbook(sheets.map { it.deepCopy() }.toMutableList(), activeSheetIndex)

    companion object {
        fun sample(): Workbook {
            val sheet = Sheet("Sheet 1")
            val rows = listOf(
                listOf("Region", "Q1", "Q2", "Q3", "Q4", "Total"),
                listOf("North", "12400", "15100", "13800", "16700", "=SUM(B2:E2)"),
                listOf("South", "9800", "11100", "12500", "13000", "=SUM(B3:E3)"),
                listOf("West", "17400", "16800", "18100", "19300", "=SUM(B4:E4)"),
                listOf("East", "11200", "14300", "15400", "16100", "=SUM(B5:E5)"),
                listOf("Average", "=AVG(B2:B5)", "=AVG(C2:C5)", "=AVG(D2:D5)", "=AVG(E2:E5)", "=SUM(F2:F5)")
            )
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, value ->
                    sheet.cell(CellRef(r, c)).raw = value
                    if (r == 0) sheet.cell(CellRef(r, c)).style = sheet.cell(CellRef(r, c)).style.copy(bold = true, fill = 0xFFE9F6EF.toInt())
                    if (c > 0 && r > 0) sheet.cell(CellRef(r, c)).style = sheet.cell(CellRef(r, c)).style.copy(numberFormat = NumberFormat.Currency, align = CellAlign.Right)
                }
            }
            return Workbook(mutableListOf(sheet)).also { it.recalculate() }
        }
    }
}

data class Sheet(
    val name: String,
    val rowCount: Int = 200,
    val colCount: Int = 52,
    val cells: MutableMap<CellRef, Cell> = mutableMapOf(),
    val hiddenRows: MutableSet<Int> = mutableSetOf()
) {
    fun cell(ref: CellRef): Cell = cells.getOrPut(ref) { Cell() }

    fun cellsIn(range: CellRange): List<Cell> = range.normalized().cells().map { cell(it) }

    fun recalculate(workbook: Workbook) {
        cells.values.forEach {
            it.display = ""
            it.error = null
        }
        cells.forEach { (ref, cell) ->
            if (cell.raw.trim().startsWith("=")) {
                when (val result = FormulaEngine(this).evaluate(ref, cell.raw.drop(1))) {
                    is FormulaValue.Success -> cell.display = result.value.short()
                    is FormulaValue.Failure -> {
                        cell.error = result.message
                        cell.display = "#ERR"
                    }
                }
            } else {
                cell.display = cell.raw
            }
        }
    }

    fun sortRange(range: CellRange) {
        val r = range.normalized()
        val rows = (r.top..r.bottom).map { row ->
            (r.left..r.right).map { col -> cell(CellRef(row, col)).copy() }
        }.sortedBy { row -> row.firstOrNull()?.display?.lowercase(Locale.US) ?: "" }
        rows.forEachIndexed { rowOffset, row ->
            row.forEachIndexed { colOffset, value ->
                cells[CellRef(r.top + rowOffset, r.left + colOffset)] = value
            }
        }
    }

    fun toCsv(range: CellRange): String = range.cells().groupBy { it.row }.toSortedMap().values.joinToString("\n") { refs ->
        refs.sortedBy { it.col }.joinToString(",") { cell(it).raw.csvEscape() }
    }

    fun importCsv(start: CellRef, csv: String) {
        csv.lines().filter { it.isNotBlank() }.forEachIndexed { r, line ->
            parseCsvLine(line).forEachIndexed { c, value ->
                cell(CellRef(start.row + r, start.col + c)).raw = value
            }
        }
    }

    fun deepCopy(): Sheet = copy(
        cells = cells.mapValues { it.value.copy(style = it.value.style.copy()) }.toMutableMap(),
        hiddenRows = hiddenRows.toMutableSet()
    )
}

data class Cell(
    var raw: String = "",
    var display: String = "",
    var error: String? = null,
    var style: CellStyle = CellStyle()
) {
    fun formattedDisplay(): String {
        val n = numericOrNull() ?: return display
        return when (style.numberFormat) {
            NumberFormat.General -> display
            NumberFormat.Currency -> "$" + "%,.2f".format(Locale.US, n)
            NumberFormat.Percent -> "%.1f%%".format(Locale.US, n * 100)
        }
    }

    fun numericOrNull(): Double? = display.toDoubleOrNull() ?: raw.toDoubleOrNull()
}

data class CellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fill: Int = Color.WHITE,
    val align: CellAlign = CellAlign.Left,
    val numberFormat: NumberFormat = NumberFormat.General
)

enum class CellAlign { Left, Center, Right }
enum class NumberFormat { General, Currency, Percent }

data class CellRef(val row: Int, val col: Int) {
    fun label(): String = columnName(col) + (row + 1)
    companion object {
        fun parse(text: String): CellRef? {
            val match = Regex("""^([A-Z]+)(\d+)$""", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
            val col = match.groupValues[1].uppercase(Locale.US).fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
            val row = match.groupValues[2].toIntOrNull()?.minus(1) ?: return null
            return if (row >= 0 && col >= 0) CellRef(row, col) else null
        }
    }
}

data class CellRange(val start: CellRef, val end: CellRef) {
    val top get() = min(start.row, end.row)
    val bottom get() = max(start.row, end.row)
    val left get() = min(start.col, end.col)
    val right get() = max(start.col, end.col)
    val height get() = bottom - top + 1
    fun normalized(): CellRange = CellRange(CellRef(top, left), CellRef(bottom, right))
    fun cells(): List<CellRef> = (top..bottom).flatMap { r -> (left..right).map { c -> CellRef(r, c) } }
}

class FormulaEngine(private val sheet: Sheet) {
    private lateinit var text: String
    private var pos = 0
    private val visiting = mutableSetOf<CellRef>()

    fun evaluate(origin: CellRef, expression: String): FormulaValue {
        return try {
            text = expression.replace(" ", "")
            pos = 0
            visiting.add(origin)
            val value = parseExpression()
            visiting.remove(origin)
            FormulaValue.Success(value)
        } catch (error: IllegalStateException) {
            FormulaValue.Failure(error.message ?: "Formula error")
        } catch (error: NumberFormatException) {
            FormulaValue.Failure(error.message ?: "Formula error")
        }
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (peek() == '+' || peek() == '-') {
            val op = next()
            val rhs = parseTerm()
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (peek() == '*' || peek() == '/') {
            val op = next()
            val rhs = parseFactor()
            value = if (op == '*') value * rhs else value / rhs
        }
        return value
    }

    private fun parseFactor(): Double {
        if (peek() == '(') {
            next()
            val value = parseExpression()
            expect(')')
            return value
        }
        if (peek()?.isLetter() == true) {
            val word = readWhile { it.isLetter() }.uppercase(Locale.US)
            if (peek() == '(') {
                next()
                val refs = readUntil(')')
                expect(')')
                return function(word, refs)
            }
            val digits = readWhile { it.isDigit() }
            return resolveCell(CellRef.parse(word + digits) ?: error("Bad cell"))
        }
        return readWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: error("Bad number")
    }

    private fun function(name: String, body: String): Double {
        val values = body.split(",").flatMap { token ->
            val parts = token.split(":")
            if (parts.size == 2) {
                val a = CellRef.parse(parts[0]) ?: return@flatMap emptyList()
                val b = CellRef.parse(parts[1]) ?: return@flatMap emptyList()
                CellRange(a, b).cells().map { resolveCell(it) }
            } else {
                listOf(resolveCell(CellRef.parse(token) ?: return@flatMap emptyList()))
            }
        }
        return when (name) {
            "SUM" -> values.sum()
            "AVG", "AVERAGE" -> values.average()
            "MIN" -> values.minOrNull() ?: 0.0
            "MAX" -> values.maxOrNull() ?: 0.0
            "COUNT" -> values.size.toDouble()
            else -> error("Unknown function")
        }
    }

    private fun resolveCell(ref: CellRef): Double {
        if (ref in visiting) error("Circular reference")
        val cell = sheet.cell(ref)
        return if (cell.raw.trim().startsWith("=")) {
            visiting.add(ref)
            val value = when (val result = FormulaEngine(sheet).evaluate(ref, cell.raw.drop(1))) {
                is FormulaValue.Success -> result.value
                is FormulaValue.Failure -> 0.0
            }
            visiting.remove(ref)
            value
        } else {
            cell.raw.toDoubleOrNull() ?: cell.display.toDoubleOrNull() ?: 0.0
        }
    }

    private fun peek(): Char? = text.getOrNull(pos)
    private fun next(): Char = text[pos++]
    private fun expect(ch: Char) {
        if (peek() != ch) error("Expected $ch")
        pos++
    }
    private fun readWhile(ok: (Char) -> Boolean): String {
        val start = pos
        while (peek()?.let(ok) == true) pos++
        return text.substring(start, pos)
    }
    private fun readUntil(ch: Char): String {
        val start = pos
        while (peek() != null && peek() != ch) pos++
        return text.substring(start, pos)
    }
}

sealed class FormulaValue {
    data class Success(val value: Double) : FormulaValue()
    data class Failure(val message: String) : FormulaValue()
}

class UndoStack {
    private val undo = ArrayDeque<Command>()
    private val redo = ArrayDeque<Command>()

    fun push(command: Command) {
        undo.addLast(command)
        redo.clear()
    }

    fun snapshot(workbook: Workbook) = push(SnapshotCommand(workbook.deepCopy()))

    fun undo(workbook: Workbook): Boolean {
        val command = undo.removeLastOrNull() ?: return false
        command.undo(workbook)
        redo.addLast(command)
        return true
    }

    fun redo(workbook: Workbook): Boolean {
        val command = redo.removeLastOrNull() ?: return false
        command.redo(workbook)
        undo.addLast(command)
        return true
    }
}

interface Command {
    fun undo(workbook: Workbook)
    fun redo(workbook: Workbook)
}

data class EditCommand(val sheetIndex: Int, val ref: CellRef, val oldRaw: String, val newRaw: String) : Command {
    override fun undo(workbook: Workbook) {
        workbook.sheets[sheetIndex].cell(ref).raw = oldRaw
        workbook.recalculate()
    }
    override fun redo(workbook: Workbook) {
        workbook.sheets[sheetIndex].cell(ref).raw = newRaw
        workbook.recalculate()
    }
}

data class SnapshotCommand(private val before: Workbook) : Command {
    private var after: Workbook? = null
    override fun undo(workbook: Workbook) {
        after = workbook.deepCopy()
        workbook.sheets.clear()
        workbook.sheets.addAll(before.deepCopy().sheets)
        workbook.activeSheetIndex = before.activeSheetIndex
        workbook.recalculate()
    }
    override fun redo(workbook: Workbook) {
        val next = after ?: return
        workbook.sheets.clear()
        workbook.sheets.addAll(next.deepCopy().sheets)
        workbook.activeSheetIndex = next.activeSheetIndex
        workbook.recalculate()
    }
}

object Palette {
    val excel = 0xFF217346.toInt()
    val blue = 0xFF2563EB.toInt()
    val ribbon = 0xFFF1F5F4.toInt()
    val canvas = 0xFFF7F9FB.toInt()
    val cellHeader = 0xFFEFF3F5.toInt()
    val grid = 0xFFD7DEE3.toInt()
    val text = 0xFF17212B.toInt()
    val textMuted = 0xFF5B6770.toInt()
}

fun columnName(index: Int): String {
    var n = index + 1
    val out = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        out.append(('A'.code + r).toChar())
        n = (n - 1) / 26
    }
    return out.reverse().toString()
}

fun Double.short(): String = if (abs(this - toLong()) < 0.0001) "%d".format(toLong()) else "%.2f".format(Locale.US, this)

fun String.csvEscape(): String {
    val needs = contains(",") || contains("\"") || contains("\n")
    val escaped = replace("\"", "\"\"")
    return if (needs) "\"$escaped\"" else escaped
}

fun parseCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val item = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && quoted && line.getOrNull(i + 1) == '"' -> {
                item.append('"')
                i++
            }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> {
                out.add(item.toString())
                item.clear()
            }
            else -> item.append(ch)
        }
        i++
    }
    out.add(item.toString())
    return out
}
