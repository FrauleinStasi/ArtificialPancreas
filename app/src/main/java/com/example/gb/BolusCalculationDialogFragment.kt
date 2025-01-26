package com.example.gb

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast

class BolusCalculationDialogFragment : DialogFragment() {

    interface BolusCalculationListener {
        fun onBolusCalculation(carbs: Float, bg: Float)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view: View = inflater.inflate(R.layout.dialog_bolus_calculation, null)

        val carbsInput = view.findViewById<EditText>(R.id.carbsInput)
        val bgInput = view.findViewById<EditText>(R.id.bgInput)

        builder.setView(view)
            .setTitle("Введите параметры для расчета болюса")
            .setPositiveButton("Рассчитать") { dialog, id ->
                val carbs = carbsInput.text.toString().toFloatOrNull()
                val bg = bgInput.text.toString().toFloatOrNull()
                if (carbs != null && bg != null) {
                    (activity as? BolusCalculationListener)?.onBolusCalculation(carbs, bg)
                } else {
                    Toast.makeText(requireContext(), "Пожалуйста, введите все параметры", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена") { dialog, id ->
                dialog.dismiss()
            }

        return builder.create()
    }
}
