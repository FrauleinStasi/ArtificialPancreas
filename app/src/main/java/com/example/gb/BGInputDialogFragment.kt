package com.example.gb

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

class BGInputDialogFragment : DialogFragment() {

    interface BGInputListener {
        fun onBGInput(input: Float)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view: View = inflater.inflate(R.layout.dialog_bg_input, null)

        val bgInput = view.findViewById<EditText>(R.id.bgInput)

        builder.setView(view)
            .setTitle("Введите значение BG")
            .setPositiveButton("OK") { dialog, id ->
                val input = bgInput.text.toString().toFloatOrNull()
                if (input != null) {
                    (activity as? BGInputListener)?.onBGInput(input)
                }
            }
            .setNegativeButton("Отмена") { dialog, id ->
                dialog.dismiss()
            }

        return builder.create()
    }
}
