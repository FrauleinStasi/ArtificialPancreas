package com.example.gb

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : AppCompatActivity(), BGInputDialogFragment.BGInputListener,
    ParameterInputDialogFragment.ParameterInputListener,
    BolusCalculationDialogFragment.BolusCalculationListener {

    private var bgValue: Float = 0f
    private var tinsulin: Float = 0f
    private var targetBG: Float? = null
    private var isf: Float = 0f
    private var icRatio: Float = 0f
    private var iob: Float = 0f
    private var bolus: Float = 0f
    private var tbolus: Long = 0L
    private var carbs: Float? = null

    private lateinit var lineChart: LineChart
    private lateinit var entries: ArrayList<Entry>
    private lateinit var dataSet: LineDataSet
    private lateinit var forecastButton: Button
    private lateinit var bolusCalculationButton: Button
    private lateinit var addBolusButton: Button
    private lateinit var hypoglycemiaTimeTextView: TextView
    private var currentVerticalLine: LimitLine? = null

    private lateinit var handler: Handler
    private lateinit var iobRunnable: Runnable

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация элементов UI
        initViews()
        setupChart()
        setupButtons()
        setupIOBCalculation()
    }

    private fun initViews() {
        lineChart = findViewById(R.id.lineChart)
        forecastButton = findViewById(R.id.forecastButton)
        bolusCalculationButton = findViewById(R.id.bolusCalculationButton)
        addBolusButton = findViewById(R.id.addBolusButton)
        hypoglycemiaTimeTextView = findViewById(R.id.hypoglycemiaTimeTextView)
    }

    private fun setupChart() {
        entries = ArrayList()
        dataSet = LineDataSet(entries, "Глюкоза").apply {
            color = Color.parseColor("#FF00FF")
            lineWidth = 2f
            circleRadius = 5f
            circleHoleRadius = 2.5f
            setCircleColor(Color.MAGENTA)
            setDrawValues(true)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String {
                    return "${dateFormat.format(Date(entry!!.x.toLong()))} / ${entry.y}"
                }
            }
        }

        lineChart.apply {
            description.isEnabled = false
            data = LineData(dataSet)
            axisLeft.axisMinimum = 2f
            axisLeft.axisMaximum = 14f
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = TimeValueFormatter(System.currentTimeMillis())
                granularity = 3600000f
                labelRotationAngle = -45f
            }
            legend.isEnabled = true
            setTouchEnabled(true)
            setPinchZoom(true)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.menuButton).setOnClickListener {
            ParameterInputDialogFragment().show(supportFragmentManager, "ParameterInputDialog")
        }

        findViewById<Button>(R.id.bgInputButton).setOnClickListener {
            BGInputDialogFragment().show(supportFragmentManager, "BGInputDialog")
        }

        bolusCalculationButton.setOnClickListener {
            if (allParametersEntered()) {
                BolusCalculationDialogFragment().show(supportFragmentManager, "BolusCalculationDialog")
            } else {
                Toast.makeText(this, "Введите все параметры", Toast.LENGTH_SHORT).show()
            }
        }

        forecastButton.setOnClickListener {
            if (bgValue > 0 && targetBG != null) {
                calculateForecast()
            } else {
                Toast.makeText(this, "Введите данные BG и параметры", Toast.LENGTH_SHORT).show()
            }
        }

        addBolusButton.setOnClickListener {
            addDiamondMarkerToChart(System.currentTimeMillis().toFloat())
        }
    }

    private fun setupIOBCalculation() {
        handler = Handler(Looper.getMainLooper())
        iobRunnable = object : Runnable {
            override fun run() {
                calculateIOB()
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(iobRunnable)
    }

    private fun calculateIOB() {
        val currentTime = System.currentTimeMillis()
        if (tbolus == 0L) {
            iob = 0f
            return
        }

        val hoursSinceBolus = (currentTime - tbolus).toFloat() / (60 * 60 * 1000)
        iob = if (hoursSinceBolus < tinsulin) {
            max(bolus * (1 - hoursSinceBolus / tinsulin), 0f)
        } else {
            0f
        }
        findViewById<TextView>(R.id.iobValue).text = "%.2f".format(iob)
    }

    override fun onBGInput(input: Float) {
        bgValue = input
        val currentTime = System.currentTimeMillis().toFloat()

        // Очищаем старый прогноз
        clearForecastData()

        // Добавляем новое значение
        entries.add(Entry(currentTime, bgValue))
        dataSet.notifyDataSetChanged()
        lineChart.data.notifyDataChanged()
        lineChart.moveViewToX(currentTime)
        lineChart.invalidate()

        updateVerticalLine()
        checkForecastButton()
    }

    private fun clearForecastData() {
        entries.removeAll { it.data == "forecast" }
        lineChart.data?.removeDataSet(lineChart.data.getDataSetByLabel("Forecast", false))
    }

    private fun calculateForecast() {
        clearForecastData()
        val forecastEntries = ArrayList<Entry>()
        var forecastBG = bgValue
        var forecastIOB = iob
        val currentTime = System.currentTimeMillis().toFloat()
        val deltaTime = 1800000f // 30 минут

        for (i in 1..48) {
            val forecastTime = currentTime + i * deltaTime
            forecastBG = max(forecastBG - isf * forecastIOB, 4f)
            forecastIOB = max(bolus - (bolus / tinsulin) * (i * 0.5f), 0f)

            forecastEntries.add(Entry(forecastTime, forecastBG).apply {
                data = "forecast"
            })

            if (forecastBG <= 4f) {
                showHypoglycemiaWarning(i * 30)
                break
            }
        }

        val forecastDataSet = LineDataSet(forecastEntries, "Forecast").apply {
            color = Color.GRAY
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
            setDrawCircles(false)
            setDrawValues(false)
        }

        lineChart.data?.addDataSet(forecastDataSet)
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun showHypoglycemiaWarning(minutes: Int) {
        val hours = minutes / 60
        val mins = minutes % 60
        hypoglycemiaTimeTextView.text =
            "Возможна гипогликемия через: $hours ч. $mins мин."
    }

    private fun updateVerticalLine() {
        currentVerticalLine?.let { lineChart.xAxis.removeLimitLine(it) }
        currentVerticalLine = LimitLine(System.currentTimeMillis().toFloat(), "Сейчас").apply {
            lineWidth = 2f
            lineColor = Color.BLUE
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        lineChart.xAxis.addLimitLine(currentVerticalLine)
    }

    private fun addDiamondMarkerToChart(x: Float) {
        val markerLine = LimitLine(x, "\u25C6 ${dateFormat.format(Date(x.toLong()))}").apply {
            lineWidth = 2f
            lineColor = Color.TRANSPARENT
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            textSize = 14f
            textColor = Color.RED
        }
        lineChart.xAxis.addLimitLine(markerLine)
        lineChart.invalidate()
    }

    override fun onParameterInput(tinsulin: Float, targetBG: Float, isf: Float, icRatio: Float) {
        this.tinsulin = tinsulin
        this.targetBG = targetBG
        this.isf = isf
        this.icRatio = icRatio

        updateParameterViews()
        addTargetBGLines()
        checkForecastButton()
    }

    private fun updateParameterViews() {
        findViewById<TextView>(R.id.tinsulinValue).text = tinsulin.toString()
        findViewById<TextView>(R.id.targetBGValue).text = targetBG.toString()
        findViewById<TextView>(R.id.isfValue).text = isf.toString()
        findViewById<TextView>(R.id.icRatioValue).text = icRatio.toString()
    }

    private fun addTargetBGLines() {
        lineChart.axisLeft.removeAllLimitLines()
        targetBG?.let { addLimitLine(it, "Цель", Color.RED, false) }
        addLimitLine(4f, "Гипо", Color.YELLOW, true)
        addLimitLine(11f, "Гипер", Color.YELLOW, true)
    }

    private fun addLimitLine(value: Float, label: String, color: Int, dashed: Boolean) {
        LimitLine(value, label).apply {
            lineWidth = 2f
            lineColor = color
            if (dashed) enableDashedLine(10f, 10f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            lineChart.axisLeft.addLimitLine(this)
        }
    }

    override fun onBolusCalculation(carbs: Float, bg: Float) {
        this.carbs = carbs
        bolus = max((bg - (targetBG ?: 0f)) / isf + carbs / icRatio - iob, 0f)
        tbolus = System.currentTimeMillis()

        findViewById<TextView>(R.id.bolusValue).text = "%.2f".format(bolus)
        findViewById<TextView>(R.id.tbolusValue).text = dateFormat.format(Date(tbolus))
        calculateIOB()
        checkForecastButton()
    }

    private fun checkForecastButton() {
        forecastButton.isEnabled = bgValue > 0 && targetBG != null && carbs != null &&
                bgValue < (targetBG ?: 0f) && bgValue > 3.9f
    }

    private fun allParametersEntered(): Boolean {
        return targetBG != null && isf > 0 && icRatio > 0
    }

    override fun onDestroy() {
        handler.removeCallbacks(iobRunnable)
        super.onDestroy()
    }

    private class TimeValueFormatter(private val referenceTime: Long) : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        override fun getFormattedValue(value: Float): String {
            return dateFormat.format(Date(referenceTime + value.toLong()))
        }
    }
}