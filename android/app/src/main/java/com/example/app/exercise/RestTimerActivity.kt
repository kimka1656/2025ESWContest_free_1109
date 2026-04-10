// RestTimerActivity.kt
package com.example.app.exercise

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R

// import com.example.app.R // 실제 R 클래스 경로
// import com.example.app.exercise.ExerciseManager // 필요하다면 import

class RestTimerActivity : AppCompatActivity() {

    private lateinit var restTimeTextView: TextView
    private lateinit var skipRestButton: Button
    private var restTimer: CountDownTimer? = null

    // 이 변수가 Intent로부터 값을 받아야 합니다.
    private var restTimeSeconds: Long = 30 // 기본값은 유지하거나, Intent에서 못 받았을 때의 값으로 설정

    companion object {
        // ExerciseSetActivity에서 intent.putExtra 할 때 사용한 키와 동일해야 합니다.
        const val EXTRA_REST_TIME_SECONDS = "EXTRA_REST_TIME_SECONDS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rest_timer) // activity_rest_timer.xml 레이아웃 필요

        restTimeTextView = findViewById(R.id.restTimeTextView)
        skipRestButton = findViewById(R.id.skipRestButton)

        // --- 이 부분이 중요! ---
        // Intent로부터 휴식 시간 값을 가져옵니다.
        // defaultValue는 혹시라도 값이 전달되지 않았을 때 사용할 기본값입니다.
        // ExerciseSetActivity에서 항상 값을 보내므로, 0L 또는 적절한 기본값으로 설정.
        val receivedRestTime = intent.getLongExtra(EXTRA_REST_TIME_SECONDS, 30L)

        // 만약 전달된 값이 0 이하면 유효하지 않으므로 기본값(예:30초) 사용 또는 오류 처리
        if (receivedRestTime > 0) {
            restTimeSeconds = receivedRestTime
        } else {
            // 전달된 휴식 시간이 유효하지 않을 경우의 처리
            Log.w("RestTimerActivity", "Received invalid rest time ($receivedRestTime), defaulting to $restTimeSeconds seconds.")
            Toast.makeText(this, "유효하지 않은 휴식 시간 값입니다. 기본값으로 시작합니다.", Toast.LENGTH_SHORT).show()
            // 필요하다면 여기서 finish()를 호출하거나, 기본값으로 타이머를 강행할 수 있습니다.
            // 여기서는 일단 기본값(클래스 멤버 변수 초기값)으로 진행되도록 합니다.
            // 또는 restTimeSeconds = 30L; // 명시적으로 기본값 재설정
        }

        Log.d("RestTimerActivity", "Received rest time: $restTimeSeconds seconds")

        // restTimeSeconds가 0 이하면 타이머를 시작할 의미가 없거나,
        // 오류로 간주하고 Activity를 종료할 수 있습니다.
        if (restTimeSeconds <= 0) {
            Toast.makeText(this, "유효한 휴식 시간이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
            Log.e("RestTimerActivity", "Invalid rest time: $restTimeSeconds, finishing activity.")
            // ExerciseManager.finishRest() // 만약 휴식 없이 바로 다음으로 넘어가야 한다면
            finish()
            return
        }

        skipRestButton.setOnClickListener {
            restTimer?.cancel()
            // ExerciseManager는 com.example.app.exercise 패키지에 있다고 가정
            com.example.app.exercise.ExerciseManager.finishRest() // 휴식 종료 알림
            Log.d("RestTimerActivity", "Skip button clicked, finishing rest and activity.")
            finish()
        }

        startRestTimer(restTimeSeconds)
    }

    private fun startRestTimer(seconds: Long) {
        restTimer?.cancel() // 기존 타이머 취소
        restTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                // val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(minutes)
                // restTimeTextView.text = String.format("%02d:%02d", minutes, remainingSeconds)
                // 위 대신 아래처럼 간단하게 초만 표시하거나, 필요에 맞게 수정
                val remainingSecondsTotal = millisUntilFinished / 1000
                restTimeTextView.text = String.format("%02d:%02d", remainingSecondsTotal / 60, remainingSecondsTotal % 60)
                Log.d("RestTimerActivity", "Timer tick: ${restTimeTextView.text}")
            }

            override fun onFinish() {
                restTimeTextView.text = "00:00"
                // ExerciseManager는 com.example.app.exercise 패키지에 있다고 가정
                com.example.app.exercise.ExerciseManager.finishRest() // 휴식 종료 알림
                Log.d("RestTimerActivity", "Timer finished, finishing rest and activity.")
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        restTimer?.cancel()
        Log.d("RestTimerActivity", "onDestroy called, timer cancelled.")
    }
}
