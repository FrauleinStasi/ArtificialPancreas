package com.example.gb

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import com.example.gb.R

class MainActivity : AppCompatActivity(), BGInputDialogFragment.BGInputListener,
    ParameterInputDialogFragment.ParameterInputListener,
    BolusCalculationDialogFragment.BolusCalculationListener {

    // region Variables
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
    private lateinit var simulatorStatusTextView: TextView
    private lateinit var currentTimeTextView: TextView
    private lateinit var currentBGTextView: TextView
    private lateinit var iobTextView: TextView
    private lateinit var targetBGTextView: TextView
    private lateinit var isfTextView: TextView
    private var currentVerticalLine: LimitLine? = null

    private lateinit var handler: Handler
    private lateinit var iobRunnable: Runnable
    private lateinit var daySimulator: DaySimulator

    private var bolusValue: Float = 0f  // или TextView, в зависимости от контекста

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    // endregion

    data class MealEvent(val timeMin: Int, val carbs: Float, val bolus: Float)
    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupChart()
        setupButtons()
        setupIOBCalculation()
        initSimulator()
    }

    override fun onDestroy() {
        handler.removeCallbacks(iobRunnable)
        daySimulator.stop()
        super.onDestroy()
    }
    // endregion

    // region Initialization
    private fun initViews() {
        lineChart = findViewById(R.id.lineChart)
        forecastButton = findViewById(R.id.forecastButton)
        bolusCalculationButton = findViewById(R.id.bolusCalculationButton)
        addBolusButton = findViewById(R.id.addBolusButton)
        hypoglycemiaTimeTextView = findViewById(R.id.hypoglycemiaTimeTextView)
        simulatorStatusTextView = findViewById(R.id.simulatorStatus)
        currentTimeTextView = findViewById(R.id.currentTime)
        currentBGTextView = findViewById(R.id.currentBGValue)
        iobTextView = findViewById(R.id.iobValue)
        targetBGTextView = findViewById(R.id.targetBGValue)
        isfTextView = findViewById(R.id.isfValue)
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

        findViewById<Button>(R.id.startSimulation).setOnClickListener {
            if (allParametersEntered()) {
                daySimulator.start()
            } else {
                Toast.makeText(this, "Введите все параметры", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.stopSimulation).setOnClickListener {
            daySimulator.stop()
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

    private fun initSimulator() {
        daySimulator = DaySimulator(
            this,
            bgValue,
            targetBG ?: 6f,
            isf,
            icRatio,
            tinsulin
        ).apply {
            addMeal(8 * 60, 60f, 4f)  // Завтрак в 8:00
            addMeal(13 * 60, 80f, 5f) // Обед в 13:00
            addMeal(19 * 60, 50f, 3f) // Ужин в 19:00
        }
    }
    // endregion

    // region Core Logic
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
        iobTextView.text = "%.2f".format(iob)
    }

    override fun onBGInput(input: Float) {
        if (!daySimulator.isRunning) {
            bgValue = input
            clearForecastData()
        }

        entries.add(Entry(System.currentTimeMillis().toFloat(), input))
        dataSet.notifyDataSetChanged()
        lineChart.data.notifyDataChanged()
        lineChart.moveViewToX(System.currentTimeMillis().toFloat())
        lineChart.invalidate()

        currentBGTextView.text = "%.1f".format(input)
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
                showHypoglycemiaWarning(i * 30)  // Убрано minutes:
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

    fun addDiamondMarkerToChart(x: Float) {
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
    // endregion

    // region Dialogs Callbacks
    override fun onParameterInput(tinsulin: Float, targetBG: Float, isf: Float, icRatio: Float) {
        this.tinsulin = tinsulin
        this.targetBG = targetBG
        this.isf = isf
        this.icRatio = icRatio

        updateParameterViews()
        addTargetBGLines()
        checkForecastButton()
        initSimulator() // Reinitialize simulator with new parameters
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

// endregion

    // region Helpers
// region Helpers
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

    private fun checkForecastButton() {
        forecastButton.isEnabled = bgValue > 0 && targetBG != null && carbs != null &&
                bgValue < (targetBG ?: 0f) && bgValue > 3.9f
    }

    private fun allParametersEntered(): Boolean {
        return targetBG != null && isf > 0 && icRatio > 0
    }
    // endregion

    // region Simulator
    inner class DaySimulator(
        private val activity: MainActivity,
        private var currentBG: Float,
        private val targetBG: Float,
        private val isf: Float,
        private val icRatio: Float,
        private val tinsulin: Float
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var iob = 0f
        private var timeMinutes = 0
        var isRunning = false
            private set
        private val mealEvents = mutableListOf<MealEvent>()


        fun addMeal(timeMin: Int, carbs: Float, bolus: Float) {
            mealEvents.add(MealEvent(timeMin, carbs, bolus))
        }

        fun start() {
            if (isRunning) return
            isRunning = true
            updateStatus("Эмуляция запущена", Color.GREEN)
            scheduleNextStep()
        }

        fun stop() {
            isRunning = false
            handler.removeCallbacksAndMessages(null)
            updateStatus("Эмуляция остановлена", Color.RED)
        }

        private fun scheduleNextStep() {
            handler.postDelayed({
                if (!isRunning) return@postDelayed

                timeMinutes += 5
                processMeals()
                simulateBasal()
                calculateMicroBolus()
                updateUI()

                if (timeMinutes < 24 * 60) {
                    scheduleNextStep()
                } else {
                    stop()
                }
            }, 500) // 0.5 сек = 5 мин
        }

        private fun processMeals() {
            mealEvents.firstOrNull { it.timeMin == timeMinutes }?.let { meal ->
                currentBG += meal.carbs / icRatio * 10
                iob += meal.bolus
                activity.addDiamondMarkerToChart(System.currentTimeMillis().toFloat())
                showToast("Прием пищи: ${meal.carbs} углеводов")
            }
        }

        private fun simulateBasal() {
            currentBG -= 0.2f
            iob = max(iob - 0.05f, 0f)
        }

        private fun calculateMicroBolus() {
            if (timeMinutes % 15 != 0) return

            val error = currentBG - targetBG
            val correction = max(error / isf - iob, 0f)

            if (correction > 0.1f) {
                iob += correction
                showToast("Микроболюс: ${"%.2f".format(correction)}")
            }

            currentBG = max(currentBG - correction * isf * 0.2f, 3.5f)
            currentBG += Random().nextFloat() * 0.4f - 0.2f
        }

        private fun updateUI() {
            activity.runOnUiThread {
                activity.currentTimeTextView.text =
                    "${timeMinutes / 60}:${"%02d".format(timeMinutes % 60)}"
                activity.iobTextView.text = "%.2f".format(iob)
                activity.onBGInput(currentBG)
            }
        }

        private fun updateStatus(text: String, color: Int) {
            activity.runOnUiThread {
                activity.simulatorStatusTextView.text = text
                activity.simulatorStatusTextView.setTextColor(color)
            }
        }

        private fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // endregion

    // region Formatters
    private class TimeValueFormatter(private val referenceTime: Long) : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        override fun getFormattedValue(value: Float): String {
            return dateFormat.format(Date(referenceTime + value.toLong()))
        }
    }
    // endregion
}