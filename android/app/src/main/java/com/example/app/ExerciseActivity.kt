//package com.example.app
//
//import android.content.*
//import android.os.Bundle
//import android.os.CountDownTimer
//import android.os.IBinder
//import android.util.Log
//import android.view.View
//import android.widget.Button
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//
//
//
//class ExerciseActivity : AppCompatActivity() {
//
//    // BLE 서비스 관련
//    private var bleService: BleService? = null
//    private var isBound = false
//
//    // UI 위젯
//    private lateinit var statusTextView: TextView
//    private lateinit var exerciseNameTextView: TextView
//    private lateinit var skipButton: Button
//
//    // 휴식 타이머
//    private var restTimer: CountDownTimer? = null
//
//    // ServiceConnection 객체: 서비스와 연결/해제 시 콜백 정의
//    private val serviceConnection = object : ServiceConnection {
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            val binder = service as BleService.LocalBinder
//            bleService = binder.getService()
//            isBound = true
//            Log.d("ExerciseActivity", "Service Bound.")
//            // 서비스가 바인딩되면, ExerciseManager의 현재 상태에 따라 UI를 즉시 업데이트
//            updateUiBasedOnState()
//        }
//
//        override fun onServiceDisconnected(name: ComponentName?) {
//            isBound = false
//            bleService = null
//            Log.d("ExerciseActivity", "Service Unbound.")
//        }
//    }
//
//    // BroadcastReceiver: BleService로부터 오는 신호를 수신
//    private val workoutUpdateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            when (intent.action) {
//                // 디바이스가 한 세트를 완료했다는 신호를 보내면
//                BleService.ACTION_DEVICE_READY_SIGNAL -> {
//                    Log.d("ExerciseActivity", "Device signal received (set finished).")
//                    // 다음 단계(휴식 또는 다음 운동)로 상태 변경
//                    ExerciseManager.moveToNextStep()
//                    // 변경된 상태에 맞게 UI 업데이트
//                    updateUiBasedOnState()
//                }
//                // GATT 연결이 끊어지면 액티비티 종료
//                BleService.ACTION_GATT_DISCONNECTED -> {
//                    Log.d("ExerciseActivity", "GATT Disconnected. Finishing activity.")
//                    finish()
//                }
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // activity_exercise.xml 레이아웃 파일을 설정해야 합니다.
//        setContentView(R.layout.activity_exercise)
//
//        // UI 위젯 초기화
//        statusTextView = findViewById(R.id.statusTextView)
//        exerciseNameTextView = findViewById(R.id.exerciseNameTextView)
//        skipButton = findViewById(R.id.skipButton)
//
//        // 서비스 바인딩 시작
//        val serviceIntent = Intent(this, BleService::class.java)
//        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
//
//        // 스킵 버튼 클릭 리스너
//        skipButton.setOnClickListener {
//            // 현재 세트를 강제로 종료하고 다음 단계로 이동
//            ExerciseManager.moveToNextStep()
//            updateUiBasedOnState()
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // 브로드캐스트 리시버 등록
//        val filter = IntentFilter().apply {
//            addAction(BleService.ACTION_DEVICE_READY_SIGNAL)
//            addAction(BleService.ACTION_GATT_DISCONNECTED)
//        }
//        ContextCompat.registerReceiver(this, workoutUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
//    }
//
//    override fun onPause() {
//        super.onPause()
//        // 브로드캐스트 리시버 해제
//        unregisterReceiver(workoutUpdateReceiver)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // 액티비티 종료 시 서비스 바인딩 해제 및 타이머 취소
//        if (isBound) {
//            unbindService(serviceConnection)
//        }
//        restTimer?.cancel()
//    }
//
//    /**
//     * ExerciseManager의 현재 상태를 기반으로 UI를 업데이트하는 핵심 함수.
//     */
//    private fun updateUiBasedOnState() {
//        restTimer?.cancel() // 진행 중인 타이머가 있다면 일단 취소
//
//        val currentState = ExerciseManager.state
//        val currentExercise = ExerciseManager.getCurrentExercise()
//
//        when (currentState) {
//            SessionState.WORKING -> {
//                if (currentExercise != null) {
//                    // 1. 디바이스에 현재 세트의 roundTripTime 전송
//                    bleService?.sendAckStart(currentExercise.roundTripTime)
//                    Log.d("ExerciseActivity", "Sent ACK for ${currentExercise.name}, Set ${ExerciseManager.getCurrentSetInfo()}")
//
//                    // 2. UI 업데이트: 운동 정보 표시
//                    statusTextView.text = ExerciseManager.getCurrentSetInfo()
//                    exerciseNameTextView.text = "${currentExercise.name} (${currentExercise.reps}회)"
//                    skipButton.visibility = View.VISIBLE
//                }
//            }
//            SessionState.RESTING -> {
//                if (currentExercise != null) {
//                    Log.d("ExerciseActivity", "Starting rest for ${currentExercise.restTime} seconds.")
//                    // 휴식 시간 타이머 시작
//                    startRestTimer(currentExercise.restTime)
//                }
//            }
//            SessionState.FINISHED -> {
//                Log.d("ExerciseActivity", "Workout finished.")
//                statusTextView.text = "운동 완료!"
//                exerciseNameTextView.text = "수고하셨습니다."
//                skipButton.visibility = View.GONE
//                bleService?.disconnect() // 운동 완료 후 연결 종료
//                ExerciseManager.clear()
//            }
//            SessionState.IDLE -> {
//                statusTextView.text = "세션 준비 중..."
//                exerciseNameTextView.text = ""
//                skipButton.visibility = View.GONE
//            }
//        }
//    }
//
//    /**
//     * 휴식 시간 카운트다운 타이머를 시작하는 함수.
//     * @param restSeconds 총 휴식 시간(초)
//     */
//    private fun startRestTimer(restSeconds: Int) {
//        skipButton.visibility = View.GONE // 휴식 중에는 스킵 버튼 숨김
//
//        restTimer = object : CountDownTimer(restSeconds * 1000L, 1000) {
//            override fun onTick(millisUntilFinished: Long) {
//                val secondsLeft = millisUntilFinished / 1000
//                statusTextView.text = "휴식 시간"
//                exerciseNameTextView.text = "$secondsLeft 초"
//            }
//
//            override fun onFinish() {
//                // 타이머가 끝나면 휴식 종료 상태로 변경하고 다시 UI 업데이트
//                Log.d("ExerciseActivity", "Rest finished.")
//                ExerciseManager.finishRest()
//                updateUiBasedOnState()
//            }
//        }.start()
//    }
//}