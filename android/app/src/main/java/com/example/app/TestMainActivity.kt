package com.example.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.app.ble.BleScanActivity
import com.example.app.exercise.DailyPlanActivity

class TestMainActivity : AppCompatActivity() {
    private lateinit var btnExercise: Button
    private lateinit var btnBle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_main)

        btnBle = findViewById<Button>(R.id.bleBtn)
        btnBle.setOnClickListener { startActivity(Intent(this, BleScanActivity::class.java)) }

        btnExercise = findViewById<Button>(R.id.exerciseBtn)
        btnExercise.setOnClickListener { startActivity(Intent(this, DailyPlanActivity::class.java)) }

    }
}