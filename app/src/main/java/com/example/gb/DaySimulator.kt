package com.example.gb

import com.example.gb.MainActivity
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import java.util.Random
import kotlin.math.max

class DaySimulator(
    private val activity: MainActivity,
    private var currentBG: Float,  // Исправлено на currentBG
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

    data class MealEvent(val timeMin: Int, val carbs: Float, val bolus: Float)

    fun addMeal(timeMin: Int, carbs: Float, bolus: Float) {
        mealEvents.add(MealEvent(timeMin, carbs, bolus))
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.simulatorStatus).text = "Эмуляция запущена"
        }
        scheduleNextStep()
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.simulatorStatus).text = "Эмуляция остановлена"
        }
    }

    private fun scheduleNextStep() {
        handler.postDelayed({
            if (!isRunning) return@postDelayed

            timeMinutes += 5
            processMeals()
            simulateBasal()
            calculateMicroBolus()
            updateUI()

            if (timeMinutes < 24 * 60) { // 24 часа
                scheduleNextStep()
            } else {
                stop()
            }
        }, 500) // 0.5 сек = 5 мин
    }

    private fun processMeals() {
        mealEvents.firstOrNull { it.timeMin == timeMinutes }?.let { meal ->
            currentBG += meal.carbs / icRatio * 10 // Упрощенная модель влияния углеводов
            iob += meal.bolus
            activity.addDiamondMarkerToChart(System.currentTimeMillis().toFloat())
            activity.runOnUiThread {
                Toast.makeText(activity, "Прием пищи: ${meal.carbs} углеводов", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun simulateBasal() {
        // Базальный инсулин (упрощенная модель)
        currentBG -= 0.2f
        iob = max(iob - 0.05f, 0f)
    }

    private fun calculateMicroBolus() {
        if (timeMinutes % 15 != 0) return // Коррекция каждые 15 мин (в эмуляции - каждые 1.5 сек)

        val error = currentBG - targetBG
        val correction = max(error / isf - iob, 0f)

        if (correction > 0.1f) { // Минимальный болюс
            iob += correction
            activity.runOnUiThread {
                Toast.makeText(activity, "Микроболюс: ${"%.2f".format(correction)}", Toast.LENGTH_SHORT).show()
            }
        }

        // Упрощенная модель метаболизма
        currentBG = max(currentBG - correction * isf * 0.2f, 3.5f)
        currentBG += Random().nextFloat() * 0.4f - 0.2f // Случайные колебания
    }

    private fun updateUI() {
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.currentTime).text =
                "${timeMinutes / 60}:${"%02d".format(timeMinutes % 60)}"
            activity.findViewById<TextView>(R.id.iobValue).text = "%.2f".format(iob)
            activity.onBGInput(currentBG) // Обновим график
        }
    }
}