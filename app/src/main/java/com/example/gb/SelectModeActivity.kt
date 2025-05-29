package com.example.gb

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class SelectModeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_mode)

        findViewById<Button>(R.id.btn_manual_mode).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btn_auto_mode).setOnClickListener {
            Toast.makeText(this, "Автоматический режим в разработке", Toast.LENGTH_SHORT).show()
        }
    }
}