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
        dataSet.lineWidth = 0f  // Убираем соединяющие линии
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
        val currentTimeMillis = System.currentTimeMillis()
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = TimeValueFormatter(currentTimeMillis)
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



        // Добавляем пунктирные линии для y=11 и y=4
        addLimitLine(11f, "", Color.YELLOW, true)
        addLimitLine(4f, "", Color.YELLOW, true)

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
        addBolusButton = findViewById(R.id.addBolusButton)
        addBolusButton.setOnClickListener {
            val currentTime = System.currentTimeMillis().toFloat()
            addDiamondMarkerToChart(currentTime)
        }

    }

    private fun calculateIOB() {
        val currentTimeMillis = System.currentTimeMillis()

        // Проверка условий для IOB
        if ((currentTimeMillis - tbolus) < tinsulin * 60 * 60 * 1000) { // Переводим Tinsulin в миллисекунды
            val timeSinceBolus = (currentTimeMillis - tbolus).toFloat() / (60 * 60 * 1000) // Время с момента болюса в часах
            val insulinEffect = integrateInsulinEffect(tbolus, currentTimeMillis, bolus, tinsulin)
            iob = bolus - insulinEffect
        } else {
            iob = 0f
        }

        // Обновляем значение IOB на экране
        iobValueTextView.text = String.format("%.2f", iob)
    }

    private fun integrateInsulinEffect(start: Long, end: Long, bolus: Float, tinsulin: Float): Float {
        val duration = (end - start).toFloat() / (60 * 60 * 1000) // Длительность в часах
        return bolus * (duration / tinsulin) // Простая линейная модель
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
        lineChart.invalidate() // Перерисовываем график

        // Обновляем вертикальную линию для текущего времени
        updateVerticalLine()
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
        this.tinsulin = tinsulin
        this.targetBG = targetBG
        this.isf = isf
        this.icRatio = icRatio
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
        tinsulinValueTextView.text = tinsulin.toString()
        targetBGValueTextView.text = targetBG.toString()
        isfValueTextView.text = isf.toString()
        icRatioValueTextView.text = icRatio.toString()

        // Проверяем условия для кнопки Forecast
        if (bgValue < targetBG && bgValue > 3.9) {
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

        // Проверка условий для IOB
        if (bolus < 0 && (currentTimeMillis - tbolus) < tinsulin * 60 * 60 * 1000) {  // Переводим Tinsulin в миллисекунды
            iob = bolus - (bolus / tinsulin) * ((currentTimeMillis - tbolus) / (60 * 60 * 1000))  // Переводим разницу времени в часы
            iobValueTextView.text = iob.toString()
        } else {
            iob = 0f
            bolus = 0f
            tbolus = 0L
            iobValueTextView.text = iob.toString()
        }

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




}
