package com.example.gb

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

class ParameterInputDialogFragment : DialogFragment() {

    interface ParameterInputListener {
        fun onParameterInput(tinsulin: Float, targetBG: Float, isf: Float, icRatio: Float)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view: View = inflater.inflate(R.layout.dialog_parameter_input, null)

        val tinsulinInput = view.findViewById<EditText>(R.id.tinsulinInput)
        val targetBGInput = view.findViewById<EditText>(R.id.targetBGInput)
        val isfInput = view.findViewById<EditText>(R.id.isfInput)
        val icRatioInput = view.findViewById<EditText>(R.id.icRatioInput)

        builder.setView(view)
            .setTitle("Введите параметры")
            .setPositiveButton("OK") { dialog, id ->
                val tinsulin = tinsulinInput.text.toString().toFloatOrNull() ?: 0f
                val targetBG = targetBGInput.text.toString().toFloatOrNull() ?: 0f
                val isf = isfInput.text.toString().toFloatOrNull() ?: 0f
                val icRatio = icRatioInput.text.toString().toFloatOrNull() ?: 0f
                (activity as? ParameterInputListener)?.onParameterInput(tinsulin, targetBG, isf, icRatio)
            }
            .setNegativeButton("Отмена") { dialog, id ->
                dialog.dismiss()
            }

        return builder.create()
    }
}
