package com.example.gb

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import android.widget.Button
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), BGInputDialogFragment.BGInputListener, ParameterInputDialogFragment.ParameterInputListener, BolusCalculationDialogFragment.BolusCalculationListener {

    private var bgValue: Float = 0f
    private var tinsulin: Float = 0f
    private var targetBG: Float? = null
    private var isf: Float = 0f
    private var icRatio: Float = 0f
    private var iob: Float = 0f
    private var bolus: Float = 0f
    private var tbolus: Long = 0L

    private lateinit var lineChart: LineChart
    private lateinit var entries: ArrayList<Entry>
    private lateinit var dataSet: LineDataSet
    private lateinit var textClock: TextClock
    private lateinit var forecastButton: Button
    private lateinit var bolusCalculationButton: Button
    private lateinit var bolusValueTextView: TextView
    private lateinit var tbolusValueTextView: TextView
    private lateinit var tinsulinValueTextView: TextView
    private lateinit var targetBGValueTextView: TextView
    private lateinit var isfValueTextView: TextView
    private lateinit var icRatioValueTextView: TextView
    private lateinit var iobValueTextView: TextView
    private lateinit var addBolusButton: Button
    private var currentVerticalLine: LimitLine? = null

    private lateinit var handler: Handler
    private lateinit var iobRunnable: Runnable

    private var prevTinsulin: Float? = null
    private var prevTargetBG: Float? = null
    private var prevIsf: Float? = null
    private var prevIcRatio: Float? = null

    // Определяем переменную dateFormat
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        entries = ArrayList()

        textClock = findViewById(R.id.textClock)

        dataSet = LineDataSet(entries, "")
        dataSet.setDrawValues(true)
        dataSet.setDrawCircles(true)  // Показываем только точки
        dataSet.setDrawValues(false)  // Не отображаем значения рядом с точками
        dataSet.lineWidth = 2f  // Устанавливаем ширину линии
        dataSet.setColor(Color.parseColor("#FF00FF"))  // Устанавливаем цвет линии (сиреневый)
        dataSet.circleRadius = 5f  // Устанавливаем размер круга
        dataSet.circleHoleRadius = 2.5f
        dataSet.setCircleColor(Color.MAGENTA)  // Устанавливаем цвет точки

        lineChart.description.isEnabled = false
        lineChart.description.text = ""

        val lineData = LineData(dataSet)

        // Настраиваем ось Y от 1 до 14
        lineChart.axisLeft.axisMinimum = 1f
        lineChart.axisLeft.axisMaximum = 14f
        lineChart.axisRight.isEnabled = false

        // Настраиваем ось X
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = TimeValueFormatter(System.currentTimeMillis())
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.granularity = 3600000f  // Шаг в один час (3600000 миллисекунд)
        xAxis.labelRotationAngle = -45f

        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setTouchEnabled(true)
        lineChart.isScaleXEnabled = true
        lineChart.isScaleYEnabled = true
        lineChart.setDragOffsetX(10f) // Небольшой отступ для прокрутки
        lineChart.setDragOffsetY(10f)

        lineChart.data = lineData
        lineChart.invalidate() // Обновляем график

        // Активируем кнопку меню
        val menuButton = findViewById<Button>(R.id.menuButton)
        menuButton.setOnClickListener {
            val dialog = ParameterInputDialogFragment()
            dialog.show(supportFragmentManager, "ParameterInputDialog")
        }

        // Активируем кнопку BG input
        val bgInputButton = findViewById<Button>(R.id.bgInputButton)
        bgInputButton.setOnClickListener {
            val dialog = BGInputDialogFragment()
            dialog.show(supportFragmentManager, "BGInputDialog")
        }

        // Активируем кнопку Bolus calculation
        bolusCalculationButton = findViewById(R.id.bolusCalculationButton)
        bolusCalculationButton.setOnClickListener {
            if (allParametersEntered()) {
                val dialog = BolusCalculationDialogFragment()
                dialog.show(supportFragmentManager, "BolusCalculationDialog")
            } else {
                Toast.makeText(this, "Пожалуйста, введите все параметры", Toast.LENGTH_SHORT).show()
            }
        }
        bolusCalculationButton.isEnabled = false // По умолчанию кнопка отключена

        // Найдем кнопку Forecast
        forecastButton = findViewById(R.id.forecastButton)
        forecastButton.setOnClickListener {
            calculateForecast()
        }
        forecastButton.isEnabled = false // По умолчанию кнопка отключена

        // Получаем текстовые поля для параметров
        tinsulinValueTextView = findViewById(R.id.tinsulinValue)
        targetBGValueTextView = findViewById(R.id.targetBGValue)
        isfValueTextView = findViewById(R.id.isfValue)
        icRatioValueTextView = findViewById(R.id.icRatioValue)
        iobValueTextView = findViewById(R.id.iobValue)
        bolusValueTextView = findViewById(R.id.bolusValue)
        tbolusValueTextView = findViewById(R.id.tbolusValue)

        // Добавляем вертикальную линию для текущего времени
        updateVerticalLine()

        // Инициализация Handler и Runnable
        handler = Handler(Looper.getMainLooper())
        iobRunnable = Runnable {
            calculateIOB()
            handler.postDelayed(iobRunnable, 30000) // Запускать каждые 30 секунд
        }
        handler.post(iobRunnable)

        // Активируем кнопку добавления болюса
        addBolusButton = findViewById(R.id.addBolusButton)
        addBolusButton.setOnClickListener {
            val currentTime = System.currentTimeMillis().toFloat()
            addDiamondMarkerToChart(currentTime)
        }
    }

    private fun calculateIOB() {
        val currentTimeMillis = System.currentTimeMillis()
        val durationInHours = tinsulin

        if ((currentTimeMillis - tbolus) < durationInHours * 60 * 60 * 1000) {
            val timeSinceBolus = (currentTimeMillis - tbolus).toFloat() / (60 * 60 * 1000) // Время с момента болюса в часах
            val insulinEffect = bolus * (1 - timeSinceBolus / durationInHours)
            iob = insulinEffect
        } else {
            iob = 0f
        }

        // Вывод значений для отладки
        println("Current Time: $currentTimeMillis")
        println("Tbolus: $tbolus")
        println("Bolus: $bolus")
        println("Tinsulin: $tinsulin")
        println("IOB: $iob")

        // Обновляем значение IOB на экране
        iobValueTextView.text = String.format("%.2f", iob)
    }






    override fun onBGInput(input: Float) {
        bgValue = input
        Toast.makeText(this, "Значение BG: $bgValue ммоль/л", Toast.LENGTH_SHORT).show()

        // Получаем текущее время в миллисекундах
        val currentTime = System.currentTimeMillis().toFloat()

        // Добавляем новое значение на график
        val newEntry = Entry(currentTime, bgValue)
        entries.add(newEntry)

        // Устанавливаем описание для каждой точки
        dataSet.setDrawValues(true)
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getPointLabel(entry: Entry?): String {
                return "${dateFormat.format(Date(entry!!.x.toLong()))} / ${entry.y}"
            }
        }

        // Обновляем данные графика
        dataSet.notifyDataSetChanged()
        lineChart.data.notifyDataChanged()
        lineChart.notifyDataSetChanged()

        // Устанавливаем видимый диапазон так, чтобы текущая точка была в центре
        lineChart.setVisibleXRangeMaximum(3600000f) // Отображаем 2 часа по оси X
        lineChart.moveViewToX(currentTime - 3600000f) // Смещаем график так, чтобы текущая точка была в центре

        lineChart.invalidate() // Перерисовываем график

        // Обновляем вертикальную линию для текущего времени
        updateVerticalLine()

        // Проверка условий для кнопки Forecast
        forecastButton.isEnabled = bgValue in 3.99..12.01 && bgValue < targetBG!!
    }




    private fun updateVerticalLine() {
        // Удаляем предыдущую вертикальную линию
        currentVerticalLine?.let {
            lineChart.xAxis.removeLimitLine(it)
        }

        // Получаем текущее время в миллисекундах
        val currentTime = System.currentTimeMillis().toFloat()

        // Добавляем новую вертикальную линию для текущего времени
        currentVerticalLine = LimitLine(currentTime, " GK ").apply {
            lineWidth = 2f
            lineColor = Color.BLUE
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        lineChart.xAxis.addLimitLine(currentVerticalLine)
        lineChart.invalidate() // Обновляем график
    }

    override fun onParameterInput(tinsulin: Float, targetBG: Float, isf: Float, icRatio: Float) {
        // Сохраняем предыдущие значения, если новые значения не нулевые
        prevTinsulin = tinsulin.takeIf { it != 0f } ?: prevTinsulin
        prevTargetBG = targetBG.takeIf { it != 0f } ?: prevTargetBG
        prevIsf = isf.takeIf { it != 0f } ?: prevIsf
        prevIcRatio = icRatio.takeIf { it != 0f } ?: prevIcRatio

        // Используем предыдущие значения, если новые значения нулевые
        this.tinsulin = tinsulin.takeIf { it != 0f } ?: prevTinsulin ?: 0f
        this.targetBG = targetBG.takeIf { it != 0f } ?: prevTargetBG
        this.isf = isf.takeIf { it != 0f } ?: prevIsf ?: 0f
        this.icRatio = icRatio.takeIf { it != 0f } ?: prevIcRatio ?: 0f

        Toast.makeText(this, "Параметры сохранены", Toast.LENGTH_SHORT).show()

        // Удаляем предыдущую линию TargetBG, если она существует
        lineChart.axisLeft.removeAllLimitLines()

        // Добавляем линию TargetBG
        addLimitLine(this.targetBG!!, "", Color.RED, false)

        // Добавляем обратно пунктирные линии для y=11 и y=4
        addLimitLine(11f, "", Color.YELLOW, true)
        addLimitLine(4f, "", Color.YELLOW, true)

        lineChart.invalidate() // Перерисовываем график

        // Обновляем текстовые поля с параметрами
        tinsulinValueTextView.text = this.tinsulin.toString()
        targetBGValueTextView.text = this.targetBG.toString()
        isfValueTextView.text = this.isf.toString()
        icRatioValueTextView.text = this.icRatio.toString()

        // Проверяем условия для кнопки Forecast
        if (bgValue < targetBG!! && bgValue > 3.9) {
            forecastButton.isEnabled = true
        } else {
            forecastButton.isEnabled = false
        }

        // Проверяем условия для кнопки Bolus calculation
        bolusCalculationButton.isEnabled = allParametersEntered()
    }



    override fun onBolusCalculation(carbs: Float, bg: Float) {
        // Расчет болюса
        bolus = (bg - (targetBG ?: 0f)) / isf + carbs / icRatio - iob

        // Получаем текущее время
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val currentTimeMillis = System.currentTimeMillis()

        // Обновляем значения Bolus и Tbolus на экране
        bolusValueTextView.text = bolus.toString()
        tbolusValueTextView.text = currentTime

        // Присваиваем текущее время переменной tbolus
        tbolus = currentTimeMillis

        // Пересчёт IOB после рассчета болюса
        calculateIOB()

        Toast.makeText(this, "Bolus рассчитан: $bolus Ед", Toast.LENGTH_SHORT).show()
    }




    private fun addLimitLine(value: Float, label: String, color: Int, dashed: Boolean) {
        val limitLine = LimitLine(value, label)
        limitLine.lineWidth = 2f
        limitLine.lineColor = color
        if (dashed) {
            limitLine.enableDashedLine(10f, 10f, 0f)
        }
        limitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP

        lineChart.axisLeft.addLimitLine(limitLine)
    }

    private fun allParametersEntered(): Boolean {
        return targetBG != null && isf != 0f && icRatio != 0f
    }

    // Форматтер для оси X, который отображает время в формате HH:mm
    private class TimeValueFormatter(private val referenceTimestamp: Long) : com.github.mikephil.charting.formatter.ValueFormatter() {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        override fun getFormattedValue(value: Float): String {
            val millis = referenceTimestamp + value.toLong()
            return dateFormat.format(Date(millis))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(iobRunnable) // Останавливаем выполнение Runnable при уничтожении Activity
    }

    private fun addDiamondMarkerToChart(x: Float) {
        // Форматируем текущее время для метки
        val timeLabel = dateFormat.format(Date(x.toLong()))

        // Создаем новую вертикальную линию для текущего времени с ромбиком в качестве метки
        val markerLine = LimitLine(x, "\u25C6 $timeLabel").apply { // \u25C6 - символ ромба
            lineWidth = 2f
            lineColor = Color.TRANSPARENT // Скрываем линию, оставляем только метку
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            textSize = 14f // Устанавливаем размер текста
            textColor = Color.RED // Устанавливаем цвет текста
        }

        // Добавляем линию в ось X
        lineChart.xAxis.addLimitLine(markerLine)
        lineChart.invalidate() // Обновляем график
    }


    private fun calculateForecast() {
        // Получаем текущее время и значение BG
        val currentTime = System.currentTimeMillis().toFloat()
        var forecastBG = bgValue
        var forecastIOB = iob
        val deltaTime = 3600000f // Интервал времени для прогноза (1 час)

        // Очистка предыдущих прогнозных точек, если таковые имеются
        entries.removeAll { entry -> entry.data == "forecast" }

        for (i in 1..24) { // Прогноз на 24 часа вперед
            forecastBG = forecastBG - isf * forecastIOB
            val forecastTime = currentTime + i * deltaTime

            // Добавление прогнозной точки на график
            val forecastEntry = Entry(forecastTime, forecastBG).apply { data = "forecast" }
            entries.add(forecastEntry)

            // Вычисление нового значения IOB
            forecastIOB = bolus - (bolus / tinsulin) * (forecastTime - tbolus) / (60 * 60 * 1000)

            if (forecastIOB < 0) {
                forecastIOB = 0f
            }
        }

        // Обновление графика
        dataSet.notifyDataSetChanged()
        lineChart.data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }


}