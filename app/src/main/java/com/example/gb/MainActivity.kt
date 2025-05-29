package com.example.gb

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import kotlin.math.min
import kotlin.random.Random

class MainActivity : AppCompatActivity(), BGInputDialogFragment.BGInputListener,
    ParameterInputDialogFragment.ParameterInputListener,
    BolusCalculationDialogFragment.BolusCalculationListener {

    // Реализация интерфейса BGInputListener
    override fun onBGInput(input: Float) {
        bgValue = input
        clearForecastData()
        addGlucosePoint(System.currentTimeMillis(), input)
    }

    // region Variables
    private var bgValue: Float = 6f
    private var tinsulin: Float = 4f
    private var targetBG: Float? = 6f
    private var isf: Float = 2.5f
    private var icRatio: Float = 12f
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

    private lateinit var glucoseHandler: Handler
    private lateinit var glucoseUpdateRunnable: Runnable
    private lateinit var daySimulator: DaySimulator
    private lateinit var autoPilot: AutoPilotSimulator




    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastGlucoseUpdateTime: Long = 0
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupChart()
        setupButtons()
        startGlucoseMonitoring()
        initSimulator()
        autoPilot = AutoPilotSimulator(this)

        onBGInput(bgValue)
    }
    private fun startMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                // 1. Пересчитываем IOB
                calculateIOB()

                // 2. Проверяем микроболюс
                checkMicrobolus()

                // 3. Повторяем каждую секунду
                handler.postDelayed(this, 200)
            }
        }
        handler.post(updateRunnable)
    }

    private fun checkMicrobolus() {
        // Если ГК выше целевой и IOB не слишком высокий
        if (bgValue > (targetBG ?: 0f) && iob < 1.0f) {
            val microBolus = calculateMicrobolusAmount()

            if (microBolus > 0.05f) { // Минимальный микроболюс (0.05 ЕД)
                applyMicrobolus(microBolus)
            }
        }
    }

    private fun applyMicrobolus(amount: Float) {
        bolus += amount
        tbolus = System.currentTimeMillis()

        runOnUiThread {
            findViewById<TextView>(R.id.bolusValue).text = "%.2f".format(bolus)
            findViewById<TextView>(R.id.tbolusValue).text = dateFormat.format(Date(tbolus))
            addDiamondMarkerToChart(System.currentTimeMillis().toFloat())
        }
    }

    private fun calculateMicrobolusAmount(): Float {
        return max((bgValue - (targetBG ?: 0f)) / isf - iob, 0f)
            .coerceAtMost(0.2f) // Макс. микроболюс 0.2 ЕД
    }


    override fun onDestroy() {
        stopGlucoseMonitoring()
        daySimulator.stop() // Правильный вызов метода stop() для daySimulator
        autoPilot.stopAutoPilot()
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

        lineChart.apply {
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            description.isEnabled = false

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = TimeValueFormatter(System.currentTimeMillis())

            axisLeft.axisMinimum = 3f
            axisLeft.axisMaximum = 15f
        }

        addTargetBGLines()
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
            if (::autoPilot.isInitialized) {
                autoPilot.startAutoPilot()
            } else {
                Toast.makeText(this, "Ошибка инициализации эмулятора", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.stopSimulation).setOnClickListener {
            autoPilot.stopAutoPilot()
        }
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
            addMeal(8 * 60, 60f, 4f)
            addMeal(13 * 60, 80f, 5f)
            addMeal(19 * 60, 50f, 3f)
        }
    }
    // endregion

    // region Glucose Monitoring
    private fun startGlucoseMonitoring() {
        glucoseHandler = Handler(Looper.getMainLooper())
        glucoseUpdateRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()

                // 1. Генерируем новое значение глюкозы
                val newGlucose = generateGlucoseValue(currentTime)

                // 2. Добавляем точку на график
                addGlucosePoint(currentTime, newGlucose)

                // 3. Повторяем каждые 0.5 секунд
                glucoseHandler.postDelayed(this, 600)
            }
        }
        glucoseHandler.post(glucoseUpdateRunnable)
    }
    private fun generateGlucoseValue(timestamp: Long): Float {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..8 -> 5.0f + Random.nextFloat() * 2.0f  // Утро
            hour in 12..14 -> 7.0f + Random.nextFloat() * 3.0f // Обед
            hour in 18..20 -> 6.0f + Random.nextFloat() * 2.0f // Ужин
            else -> 5.5f + Random.nextFloat() * 1.5f           // Ночь
        }.coerceIn(3.5f, 15.0f)
    }
    private fun stopGlucoseMonitoring() {
        glucoseHandler.removeCallbacks(glucoseUpdateRunnable)
    }
    private fun updateAllValues(currentTime: Long) {
        // 1. Расчёт глюкозы и IOB
        calculateIOB()
        bgValue = generateGlucoseValue(currentTime)

        // 2. Добавление точки на график
        addGlucosePoint(currentTime, bgValue)

        // 3. Обновление ВСЕХ полей UI
        runOnUiThread {
            // Основные показатели
            currentBGTextView.text = "%.1f".format(bgValue)
            currentTimeTextView.text = dateFormat.format(Date(currentTime))
            iobTextView.text = "%.2f".format(iob)

            // Время последнего болюса
            findViewById<TextView>(R.id.tbolusValue).text =
                if (tbolus == 0L) "—"
                else dateFormat.format(Date(tbolus))

            // Доп. поля (если есть)
            targetBGTextView.text = targetBG?.let { "%.1f".format(it) } ?: "—"
            isfTextView.text = "%.1f".format(isf)
        }
    }
    private fun updateUIFields(timestamp: Long, glucose: Float) {
        runOnUiThread {
            // 1. Добавляем точку на график
            entries.add(Entry(timestamp.toFloat(), glucose))
            dataSet.notifyDataSetChanged()
            lineChart.data?.notifyDataChanged()
            lineChart.moveViewToX(timestamp.toFloat())

            // 2. Обновляем текстовые поля
            currentBGTextView.text = "%.1f".format(glucose)
            currentTimeTextView.text = dateFormat.format(Date(timestamp))
            iobTextView.text = "%.2f".format(iob)

            // 3. Время последнего болюса (используем существующее поле tbolusValue)
            findViewById<TextView>(R.id.tbolusValue).text =
                if (tbolus == 0L) "Нет данных"
                else dateFormat.format(Date(tbolus))

            lineChart.invalidate()
        }
    }
    private fun updateGlucoseValue(timestamp: Long) {
        if (timestamp - lastGlucoseUpdateTime > 30000) {
            calculateIOB()
            lastGlucoseUpdateTime = timestamp
        }

        var newGlucose = bgValue
        newGlucose -= iob * isf * 0.001f
        newGlucose += (Random.nextFloat() - 0.5f) * 0.2f
        newGlucose = max(min(newGlucose, 15f), 3.5f)
        bgValue = newGlucose

        // Добавляем точку и сразу обновляем график
        addGlucosePoint(timestamp, newGlucose)
    }

    private fun addGlucosePoint(timestamp: Long, glucoseValue: Float) {
        runOnUiThread {
            try {
                // 1. Создаем новую точку
                val newEntry = Entry(timestamp.toFloat(), glucoseValue)

                // 2. Добавляем в список точек
                entries.add(newEntry)

                // 3. Если это первая точка, инициализируем график
                if (lineChart.data == null) {
                    dataSet = LineDataSet(entries, "Глюкоза").apply {
                        color = Color.BLUE
                        lineWidth = 2f
                        setDrawCircles(true)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.LINEAR
                    }
                    lineChart.data = LineData(dataSet)
                }

                // 4. Обновляем данные графика
                lineChart.data?.notifyDataChanged()
                lineChart.notifyDataSetChanged()

                // 5. Автоматически прокручиваем к последней точке
                lineChart.moveViewToX(timestamp.toFloat())
                lineChart.invalidate()

                // 6. Обновляем текстовые поля
                currentBGTextView.text = "%.1f".format(glucoseValue)
                currentTimeTextView.text = dateFormat.format(Date(timestamp))

            } catch (e: Exception) {
                Log.e("GLUCOSE", "Error adding point", e)
            }
        }
    }


    private fun logGlucoseData(timestamp: Long, glucoseValue: Float) {
        val timeString = dateFormat.format(Date(timestamp))
        val logMessage = """
            |=== Glucose Update ===
            |Time: $timeString
            |Current BG: ${"%.1f".format(glucoseValue)} mmol/L
            |Target BG: ${targetBG?.let { "%.1f".format(it) } ?: "Not set"} mmol/L
            |IOB: ${"%.2f".format(iob)} U
            |Bolus: ${"%.2f".format(bolus)} U
            |ISF: ${"%.1f".format(isf)} mmol/L/U
            |IC Ratio: ${"%.1f".format(icRatio)} g/U
            |Carbs: ${carbs?.let { "%.0f".format(it) } ?: "0"} g
            |=====================
        """.trimMargin()

        Log.d("GLUCOSE_MONITOR", logMessage)
    }
    // endregion

    // region Core Logic
    private fun calculateIOB() {
        if (tbolus == 0L) {
            iob = 0f
            return
        }

        val hoursSinceBolus = (System.currentTimeMillis() - tbolus).toFloat() / (60 * 60 * 1000)
        iob = if (hoursSinceBolus < tinsulin) {
            max(bolus * (1 - hoursSinceBolus / tinsulin), 0f)
        } else {
            0f
        }

        runOnUiThread {
            iobTextView.text = "%.2f".format(iob)
        }
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
        val deltaTime = 1800000f

        for (i in 1..48) {
            val forecastTime = currentTime + i * deltaTime
            forecastBG = max(forecastBG - isf * forecastIOB, 3.5f)
            forecastIOB = max(bolus - (bolus / tinsulin) * (i * 0.5f), 0f)

            forecastBG += (Random.nextFloat() - 0.5f) * 0.2f
            forecastBG = max(min(forecastBG, 15f), 3.5f)

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

    private fun updateVerticalLine(timestamp: Long) {
        currentVerticalLine?.let { lineChart.xAxis.removeLimitLine(it) }
        currentVerticalLine = LimitLine(timestamp.toFloat(), "Сейчас").apply {
            lineWidth = 1f
            lineColor = Color.GREEN
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 10f
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
        initSimulator()
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
    fun updateParameterViews() {
        findViewById<TextView>(R.id.tinsulinValue).text = "%.1f".format(tinsulin)
        findViewById<TextView>(R.id.targetBGValue).text = "%.1f".format(targetBG ?: 0f)
        findViewById<TextView>(R.id.isfValue).text = "%.1f".format(isf)
        findViewById<TextView>(R.id.icRatioValue).text = "%.1f".format(icRatio)
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

    fun checkForecastButton() {
        forecastButton.isEnabled = bgValue > 0 && targetBG != null && carbs != null &&
                bgValue < (targetBG ?: 0f) && bgValue > 3.9f
    }

    private fun allParametersEntered(): Boolean {
        return targetBG != null && isf > 0 && icRatio > 0
    }
    // endregion
    data class BolusRecord(val time: Long, val amount: Float)
    data class MealEvent(val timeMin: Int, val carbs: Float, val bolus: Float)
    // region Simulator Classes
    inner class DaySimulator(
        private val activity: MainActivity,
        private var currentBG: Float,
        private val targetBG: Float,
        private val isf: Float,
        private val icRatio: Float,
        private val tinsulin: Float
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val bolusHistory = mutableListOf<BolusRecord>()
        private var timeMinutes = 0
        var isRunning = false
            private set
        private val mealEvents = mutableListOf<MealEvent>()

        inner class BolusRecord(val time: Long, val amount: Float)

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

                currentBG = activity.bgValue

                if (timeMinutes < 24 * 60) {
                    scheduleNextStep()
                } else {
                    stop() // Вызов метода stop() внутри класса
                }
            }, 500)
        }

        private fun processMeals() {
            mealEvents.firstOrNull { it.timeMin == timeMinutes }?.let { meal ->
                currentBG += meal.carbs / icRatio * 10
                val bolusTime = System.currentTimeMillis()
                bolusHistory.add(BolusRecord(bolusTime, meal.bolus))
                activity.addDiamondMarkerToChart(bolusTime.toFloat())
                showToast("Прием пищи: ${meal.carbs} углеводов, болюс: ${meal.bolus} ЕД")
            }
        }

        private fun simulateBasal() {
            currentBG -= 0.2f
        }

        private fun calculateMicroBolus() {
            if (timeMinutes % 15 != 0) return

            val currentIOB = calculateTotalIOB()
            val error = currentBG - targetBG
            val correction = max(error / isf - currentIOB, 0f)

            if (correction > 0.1f) {
                val bolusTime = System.currentTimeMillis()
                bolusHistory.add(BolusRecord(bolusTime, correction))
                showToast("Микроболюс: ${"%.2f".format(correction)}")
            }

            currentBG = max(currentBG - correction * isf * 0.2f, 3.5f)
        }

        private fun calculateTotalIOB(): Float {
            val currentTime = System.currentTimeMillis()
            return bolusHistory.sumOf {
                calculateIOB(currentTime, it.time, it.amount, tinsulin).toDouble()
            }.toFloat()
        }

        private fun calculateIOB(currentTime: Long, bolusTime: Long, bolusAmount: Float, tInsulin: Float): Float {
            if (bolusTime == 0L) return 0f

            val hoursSinceBolus = (currentTime - bolusTime).toFloat() / (60 * 60 * 1000)
            return if (hoursSinceBolus < tInsulin) {
                max(bolusAmount * (1 - hoursSinceBolus / tInsulin), 0f)
            } else {
                0f
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

    inner class AutoPilotSimulator(
        private val activity: MainActivity
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var isRunning = false
        private var currentTime = 0

        private val presetParams = mapOf(
            "tinsulin" to 4f,
            "targetBG" to 4f,
            "isf" to 2.5f,
            "icRatio" to 12f
        )

        private val mealSchedule = listOf(
            MealEvent(7*60, 60f, 5f),
            MealEvent(13*60, 80f, 6f),
            MealEvent(19*60, 50f, 4f)
        )

        fun startAutoPilot() {
            if (isRunning) return

            activity.runOnUiThread {
                activity.tinsulin = presetParams["tinsulin"] ?: 4f
                activity.targetBG = presetParams["targetBG"] ?: 6f
                activity.isf = presetParams["isf"] ?: 2.5f
                activity.icRatio = presetParams["icRatio"] ?: 12f

                activity.updateParameterViews()
                activity.addTargetBGLines()
                activity.checkForecastButton()

                activity.bgValue = 6f
                activity.onBGInput(activity.bgValue)

                activity.iob = 0f
                activity.iobTextView.text = "0.00"

                Toast.makeText(activity,
                    "Автоэмулятор запущен\n" +
                            "Цель: ${activity.targetBG} ммоль/л\n" +
                            "Чувствительность: ${activity.isf}",
                    Toast.LENGTH_LONG).show()
            }

            isRunning = true
            currentTime = 0
            scheduleNextStep()
        }



        private fun scheduleNextStep() {
            handler.postDelayed({
                if (!isRunning) return@postDelayed

                currentTime += 5

                mealSchedule.firstOrNull { it.timeMin == currentTime }?.let { meal ->
                    activity.onBolusCalculation(meal.carbs, activity.bgValue)
                    activity.addDiamondMarkerToChart(System.currentTimeMillis().toFloat())
                }

                activity.runOnUiThread {
                    activity.findViewById<TextView>(R.id.currentTime).text =
                        "${currentTime/60}:${"%02d".format(currentTime%60)}"
                }

                if (currentTime < 24*60) {
                    scheduleNextStep()
                } else {
                    stopAutoPilot()
                }
            }, 300)
        }

        fun stopAutoPilot() {
            isRunning = false
            handler.removeCallbacksAndMessages(null)
            activity.runOnUiThread {
                Toast.makeText(activity, "Эмуляция завершена", Toast.LENGTH_SHORT).show()
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