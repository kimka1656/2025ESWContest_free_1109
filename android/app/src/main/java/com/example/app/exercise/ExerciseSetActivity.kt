package com.example.app.exercise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.BleWaitingActivity // "START REQ 받는 곳"으로 가정
import com.example.app.R
import com.example.app.ble.BleConnectionManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.json.JSONObject

class ExerciseSetActivity : AppCompatActivity(), BleConnectionManager.BleDataListener {

    private lateinit var exerciseNameTextView: TextView
    private lateinit var currentRepsTextView: TextView
    private lateinit var targetRepsTextView: TextView
    private lateinit var setInfoTextView: TextView
    private lateinit var debugLogTextView: TextView
    private lateinit var skipButton: Button
    private lateinit var lineChart: LineChart

    private val realTimeEntries = ArrayList<Entry>()
    private lateinit var realTimeDataSet: LineDataSet

    private val debugStringBuilder = StringBuilder()

    private var internalRepsCount = 0 // 앱 자체 횟수 카운터

    // 중복 데이터 필터링을 위한 변수
    private var lastReceivedTime: Float = -10.0f
    private var lastReceivedDeviceCount: Int = -10
    private var lastReceiveTimestamp: Long = 0L

    companion object {
        private const val TAG = "ExerciseSetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_set)
        Log.d(TAG, "onCreate CALLED")

        exerciseNameTextView = findViewById(R.id.exerciseNameTextView)
        currentRepsTextView = findViewById(R.id.currentRepsTextView)
        targetRepsTextView = findViewById(R.id.targetRepsTextView)
        setInfoTextView = findViewById(R.id.setInfoTextView)
        debugLogTextView = findViewById(R.id.debugLogTextView)
        lineChart = findViewById(R.id.speedGraphView)
        skipButton = findViewById(R.id.skipButton)

        debugLogTextView.movementMethod = ScrollingMovementMethod()
        appendToDebugLog("onCreate: State=${ExerciseManager.state}")

        initializeChartComponents()

        val currentExerciseName = ExerciseManager.getCurrentExercise()?.name ?: "N/A"
        appendToDebugLog("onCreate Initial: State=${ExerciseManager.state}, Ex=${currentExerciseName}")

        if (ExerciseManager.state == SessionState.IDLE) {
            appendToDebugLog("onCreate: IDLE state. Finishing.")
            Toast.makeText(this, "운동 세션이 시작되지 않았습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val currentExerciseOnCreate = ExerciseManager.getCurrentExercise()
        if (currentExerciseOnCreate == null) {
            appendToDebugLog("onCreate: No current exercise. Finishing.")
            Toast.makeText(this, "현재 운동 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        appendToDebugLog("onCreate: InitialEx=${currentExerciseOnCreate.name}, State=${ExerciseManager.state}")

        skipButton.setOnClickListener {
            appendToDebugLog("Skip button clicked. Current state before: ${ExerciseManager.state}")
            val currentStateBeforeAction = ExerciseManager.state
            if (currentStateBeforeAction == SessionState.WORKING) {
                // --- 세트 완료 데이터 기록 ---
                val achievedRepsThisSet = internalRepsCount
                val graphDataThisSet = ArrayList(realTimeEntries) // 현재 세트의 그래프 데이터 복사
                ExerciseManager.recordSetCompletion(achievedRepsThisSet, graphDataThisSet)
                appendToDebugLog("Set completed. Recorded to ExerciseManager. Reps: $achievedRepsThisSet, Graph points: ${graphDataThisSet.size}")
                // --- 데이터 기록 끝 ---

                ExerciseManager.moveToNextStep()
                appendToDebugLog("State after moveToNextStep(): ${ExerciseManager.state}")
                val nextState = ExerciseManager.state
                val nextExerciseForRest = ExerciseManager.getCurrentExercise()

                if (nextState == SessionState.RESTING) {
                    if (nextExerciseForRest != null) {
                        appendToDebugLog("To RestTimer. Rest: ${nextExerciseForRest.restTime}s for ${nextExerciseForRest.name}")
                        val intent = Intent(this, RestTimerActivity::class.java)
                        intent.putExtra("EXTRA_REST_TIME_SECONDS", nextExerciseForRest.restTime.toLong())
                        startActivity(intent)
                        // RestTimerActivity에서 돌아오면 onResume -> refreshUi가 호출됨
                    } else {
                        appendToDebugLog("State RESTING but currentEx NULL. Refreshing.")
                        refreshUi()
                    }
                } else if (nextState == SessionState.FINISHED) {
                    appendToDebugLog("All exercises FINISHED.")
                    Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, ExerciseResultActivity::class.java) // 결과 화면으로 이동
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else if (nextState == SessionState.IDLE) {
                    // 한 운동 아이템이 끝나고 다음 운동 시작 전 대기 (DeviceControlActivity에서 START_REQ 받아야 함)
                    appendToDebugLog("Exercise item finished, state IDLE. Waiting for next START_REQ.")
                    Toast.makeText(this, "${currentExerciseOnCreate.name} 완료! 다음 운동을 준비합니다.", Toast.LENGTH_SHORT).show()
                    // BleWaitingActivity로 가서 다음 START_REQ를 기다리도록 할 수 있음
                    val intent = Intent(this, BleWaitingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                else { // WORKING (다음 세트/운동 - 이 경우는 현재 로직에서 refreshUi로 처리됨)
                    refreshUi() // 새 세트 또는 새 운동 준비
                }
            } else if (currentStateBeforeAction == SessionState.RESTING) {
                // 휴식 중 스킵: 다음 운동으로 바로 진행 (ExerciseManager가 상태 변경)
                ExerciseManager.finishRest() // 혹시 RESTING 상태를 명시적으로 WORKING으로 바꿔야 한다면
                ExerciseManager.prepareAndStartNextExercise() // 다음 운동으로
                refreshUi()
                appendToDebugLog("Skip during RESTING. Moved to next. New state: ${ExerciseManager.state}")
            } else {
                appendToDebugLog("Skip in unhandled state: $currentStateBeforeAction")
                refreshUi()
            }
        }
    }

    private fun appendToDebugLog(message: String) {
        val logWithMessage = "[${System.currentTimeMillis() % 10000}] $message"
        debugStringBuilder.append("\n").append(logWithMessage)
        if (debugStringBuilder.length > 3000) {
            debugStringBuilder.delete(0, debugStringBuilder.length - 3000)
        }
        debugLogTextView.text = debugStringBuilder.toString()
        val layout = debugLogTextView.layout
        if (layout != null) {
            val scrollAmount = layout.getLineTop(debugLogTextView.lineCount) - debugLogTextView.height
            debugLogTextView.scrollTo(0, if (scrollAmount > 0) scrollAmount else 0)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume CALLED")
        appendToDebugLog("onResume: State BEFORE listener reg=${ExerciseManager.state}")
        BleConnectionManager.setBleDataListener(this)
        appendToDebugLog("onResume: Listener REGISTERED.")

        val currentExerciseName = ExerciseManager.getCurrentExercise()?.name ?: "N/A"
        appendToDebugLog("onResume AfterReg: State=${ExerciseManager.state}, Ex=${currentExerciseName}")

        if (ExerciseManager.state == SessionState.IDLE && ExerciseManager.getCompletedExerciseResults().isNotEmpty()) {
            // 운동 항목 하나가 끝나고 IDLE 상태로 돌아온 경우 (예: RestTimerActivity 종료 후 또는 skip 버튼으로 IDLE 진입)
            if (ExerciseManager.isSessionFinished()){ // 모든 운동이 끝난 경우
                appendToDebugLog("onResume: Session is FINISHED. Going to Result Screen.")
                val intent = Intent(this, ExerciseResultActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
                return
            }
            // 아직 더 할 운동이 남은 IDLE 상태 (항목 간 대기)
            appendToDebugLog("onResume: State is IDLE (between exercises), NOT session finished. Finishing to wait for next trigger from DeviceControl/BleWaiting.")
            val intent = Intent(this, BleWaitingActivity::class.java) // 다음 START_REQ 대기 화면으로
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            return
        } else if (ExerciseManager.state == SessionState.IDLE &&
            ExerciseManager.getCompletedExerciseResults().isEmpty() &&
            ExerciseManager.getCurrentExercise() == null) { // <<< 여기가 수정된 부분입니다
            // 앱 시작 직후 아직 아무 운동도 시작 안 한 초기 IDLE 상태
            appendToDebugLog("onResume: Initial IDLE state (no current exercise, no results). Finishing.")
            Toast.makeText(this, "운동 세션이 시작되지 않았습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 그 외의 경우 (주로 WORKING 상태이거나, RestTimerActivity에서 돌아와 다음 세트/운동을 준비하는 WORKING 상태)
        refreshUi() // 현재 상태에 맞게 UI 갱신
    }


    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause CALLED")
        appendToDebugLog("onPause CALLED.")
        BleConnectionManager.setBleDataListener(null)
        appendToDebugLog("onPause: Listener UNREGISTERED.")
    }

    private fun initializeChartComponents() {
        appendToDebugLog("initializeChartComponents CALLED.")
        realTimeDataSet = LineDataSet(realTimeEntries, "측정된 실제 속도") // realTimeEntries를 초기 데이터로 사용
        realTimeDataSet.color = Color.BLUE
        realTimeDataSet.setCircleColor(Color.BLUE)
        realTimeDataSet.lineWidth = 2f
        realTimeDataSet.circleRadius = 3f
        realTimeDataSet.setDrawValues(false)
        realTimeDataSet.mode = LineDataSet.Mode.LINEAR // 곡선 모드 잠시 제거
        setupSpeedChartAppearance()
    }

    private fun setupSpeedChartAppearance() {
        appendToDebugLog("setupSpeedChartAppearance CALLED. Y축 시작점 0으로 재설정.") // 로그 메시지 수정
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.isScaleXEnabled = false   // X축 스케일(확대/축소) 비활성화 (이전 요청 유지)
        lineChart.isScaleYEnabled = true    // Y축 스케일(확대/축소)은 가능하도록 명시 (유지)
        lineChart.setPinchZoom(false)       // X축 스케일이 비활성화되었으므로 핀치줌도 false (유지)

        // X축(가로축, 횟수) 기본 설정
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f // X축은 횟수이므로 1단위로 명확히 표시
        xAxis.axisMinimum = 1f // X축도 0부터 시작 (실제 횟수는 1부터 시작하지만, 축의 시작은 0으로)

        // Y축(세로축, 시간) 기본 설정
        val leftAxis = lineChart.axisLeft
        leftAxis.axisMinimum = 0f       // Y축(시간)은 항상 0부터 시작하도록 명시
        leftAxis.resetAxisMaximum()

        // Y축(오른쪽) 비활성화
        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true // 범례는 표시

        // invalidate()는 setupChartDataAndDisplay에서 데이터 설정 후 호출됨
    }


    private fun setupChartDataAndDisplay() {
        val currentExercise = ExerciseManager.getCurrentExercise() ?: run {
            appendToDebugLog("setupChart: currentExercise is NULL. Cannot setup chart.")
            realTimeEntries.clear() // 데이터 클리어
            if (::realTimeDataSet.isInitialized) { // 초기화되었는지 확인
                realTimeDataSet.clear() // DataSet도 클리어
            }
            lineChart.data = null
            lineChart.invalidate()
            return
        }
        appendToDebugLog("setupChart: Clearing realTimeEntries (size before clear=${realTimeEntries.size}) for ${currentExercise.name}. internalReps: $internalRepsCount")
        // internalRepsCount는 refreshUi에서 0으로 초기화됨. realTimeEntries도 여기서 새 세트에 맞게 초기화.
        realTimeEntries.clear()
        if (::realTimeDataSet.isInitialized) {
            realTimeDataSet.clear() // 기존 데이터셋의 엔트리도 비워야 함
        } else {
            initializeChartComponents() // 만약 초기화 안됐으면 여기서 하도록 (안전장치)
        }


        val targetEntries = ArrayList<Entry>()
        val targetReps = currentExercise.reps
        val targetSpeedValue = currentExercise.roundTripTime.toFloat()

        if (targetSpeedValue <= 0f) {
            appendToDebugLog("Target speed invalid or not set: $targetSpeedValue. Target speed line will not be shown.")
        }

        // 목표 속도 라인은 1부터 targetReps까지 그림
        for (i in 0 until targetReps) {
            targetEntries.add(Entry((i + 1).toFloat(), if (targetSpeedValue > 0) targetSpeedValue else 3.0f))
        }
        val targetDataSet = LineDataSet(targetEntries, "목표 속도 (시간)")
        targetDataSet.color = Color.RED
        targetDataSet.lineWidth = 1.5f
        targetDataSet.enableDashedLine(10f, 5f, 0f)
        targetDataSet.setDrawCircles(false)
        targetDataSet.setDrawValues(false)

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(realTimeDataSet)
        if (targetSpeedValue > 0 && targetEntries.isNotEmpty()) {
            dataSets.add(targetDataSet)
        }

        val lineData = LineData(dataSets)
        lineChart.data = lineData // 새 LineData 객체 할당
        lineChart.invalidate() // 차트 다시 그리기
        appendToDebugLog("Chart setup complete for ${currentExercise.name}: TargetReps=${targetReps}, TargetSpeed=${targetSpeedValue}, RealTimeEntries should be empty now: ${realTimeEntries.size}")
    }

    private fun refreshUi() {
        val currentExercise = ExerciseManager.getCurrentExercise()
        val currentState = ExerciseManager.state
        appendToDebugLog("refreshUi: Ex=${currentExercise?.name ?: "N/A"}, St=$currentState, InternalReps (before potential reset in WORKING)=$internalRepsCount")

        currentRepsTextView.visibility = View.GONE
        targetRepsTextView.visibility = View.GONE
        lineChart.visibility = View.GONE
        findViewById<View>(android.R.id.content).setBackgroundColor(Color.WHITE) // 기본 배경색

        if (currentState == SessionState.IDLE) {
            if (ExerciseManager.isSessionFinished()) {
                appendToDebugLog("refreshUi: State IDLE, and session is FINISHED. Going to Result Screen.")
                Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, ExerciseResultActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            } else {
                appendToDebugLog("refreshUi: State IDLE, but session NOT finished. Finishing to wait for next trigger.")
                Toast.makeText(this, "다음 운동을 준비합니다.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, BleWaitingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            return
        }
        if (currentState == SessionState.FINISHED) {
            appendToDebugLog("refreshUi: FINISHED state. Going to Result Screen.")
            Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
            val intent = Intent(this, ExerciseResultActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            return
        }

        if (currentExercise == null) {
            appendToDebugLog("refreshUi: currentExercise NULL but state $currentState. This shouldn't happen if not IDLE/FINISHED. Finishing.")
            Toast.makeText(this, "운동 정보를 찾을 수 없습니다. 세션을 종료합니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        exerciseNameTextView.text = currentExercise.name

        when (currentState) {
            SessionState.WORKING -> {
                internalRepsCount = 0
                appendToDebugLog("refreshUi: WORKING state. internalRepsCount reset to 0.")
                setupChartDataAndDisplay()

                currentRepsTextView.visibility = View.VISIBLE
                targetRepsTextView.visibility = View.VISIBLE
                lineChart.visibility = View.VISIBLE

                setInfoTextView.text = ExerciseManager.getCurrentSetInfo()
                currentRepsTextView.text = internalRepsCount.toString()
                targetRepsTextView.text = currentExercise.reps.toString()
                skipButton.text = "세트 완료"
                skipButton.isEnabled = true
                lineChart.setBackgroundColor(Color.parseColor("#E0F7FA"))

                appendToDebugLog("UI for WORKING. Set: ${ExerciseManager.getCurrentSetInfo()}, Target Reps: ${currentExercise.reps}")
            }
            SessionState.RESTING -> {
                appendToDebugLog("refreshUi: State is RESTING. This is unexpected here. Attempting to advance state.")
                ExerciseManager.finishRest()
                if(ExerciseManager.state == SessionState.WORKING) {
                    refreshUi()
                } else {
                    setInfoTextView.text = "휴식 완료 후 다음 운동 준비 중... (오류 가능성)"
                    skipButton.text = "다음 운동/세트 시작"
                    skipButton.isEnabled = true
                    findViewById<View>(android.R.id.content).setBackgroundColor(Color.LTGRAY)
                    lineChart.visibility = View.GONE
                    appendToDebugLog("refreshUi: Still RESTING or unexpected after finishRest. State: ${ExerciseManager.state}")
                }
            }
            else -> {
                appendToDebugLog("refreshUi with unhandled state: $currentState for exercise ${currentExercise.name}")
            }
        }
    }

    // In ExerciseSetActivity.kt

// ... (다른 클래스 멤버 및 함수들은 이전과 동일하다고 가정)

    override fun onDataReceived(data: String, packetSize: Int) {
        val rawCountFromDevice: Int
        val time: Float
        val currentTimestamp = System.currentTimeMillis()

        appendToDebugLog("DataRecv (Size:$packetSize): '$data' | St: ${ExerciseManager.state}, InternalReps (before inc): $internalRepsCount")

        try {
            val jsonObject = JSONObject(data)
            time = jsonObject.optDouble("time", -1.0).toFloat()
            rawCountFromDevice = jsonObject.optInt("count", -1)

            appendToDebugLog("Parsed: t=$time, device_c=$rawCountFromDevice")

            if (time == -1.0f) {
                appendToDebugLog("InvalidData: time is -1.0. Ignoring.")
                return
            }

            // 중복 데이터 필터링 (유지)
            if (time == lastReceivedTime && rawCountFromDevice == lastReceivedDeviceCount) {
                if ((currentTimestamp - lastReceiveTimestamp) < 500) { // 500ms 이내 중복 필터링
                    appendToDebugLog("Duplicate data filtered: t=$time, dev_c=$rawCountFromDevice, dT=${currentTimestamp - lastReceiveTimestamp}ms. Ignoring.")
                    return
                }
            }
            lastReceivedTime = time
            lastReceivedDeviceCount = rawCountFromDevice
            lastReceiveTimestamp = currentTimestamp

            runOnUiThread {
                if (ExerciseManager.state == SessionState.WORKING) {
                    internalRepsCount++
                    currentRepsTextView.text = internalRepsCount.toString()

                    appendToDebugLog("WORKING: internalRepsCount incremented to $internalRepsCount. time=$time")

                    val newEntry = Entry(internalRepsCount.toFloat(), time)
                    realTimeEntries.add(newEntry) // ExerciseManager 전달용 데이터 리스트에 추가
// onDataReceived 내 runOnUiThread 블록 중 일부...
                    if (::realTimeDataSet.isInitialized && lineChart.data != null) {
                        realTimeDataSet.addEntryOrdered(newEntry)

                        lineChart.data.notifyDataChanged() // <--- 추가된 라인
                        lineChart.notifyDataSetChanged() // 데이터셋 변경 알림

                        // Y축 범위 즉시 업데이트
                        val yAxis = lineChart.axisLeft
                        yAxis.axisMinimum = 0f // 최소값은 항상 0

                        var currentMaxY = realTimeDataSet.yMax // 현재 데이터셋의 최대 Y값
                        val currentExercise = ExerciseManager.getCurrentExercise()
                        val targetSpeedValue = currentExercise?.roundTripTime?.toFloat() ?: 0f

                        // 목표 속도선도 보이도록 최대값 조정 (선택 사항)
                        if (targetSpeedValue > currentMaxY) {
                            currentMaxY = targetSpeedValue
                        }

                        if (currentMaxY > 0f) {
                            yAxis.axisMaximum = currentMaxY * 1.1f // 10% 여유 공간
                        } else {
                            yAxis.axisMaximum = 5f // 데이터가 없거나 모두 0일 경우 기본 최대값 (예: 5초)
                        }

                        lineChart.invalidate() // 변경된 축 범위 및 데이터로 차트 다시 그리기
                        appendToDebugLog("Chart updated. X=${newEntry.x}, Y=${newEntry.y}. YAxis ManualMax: ${yAxis.axisMaximum}") // 로그 이름 변경

                    } else {
                        appendToDebugLog("WARN: realTimeDataSet not initialized or lineChart.data is null.")
                    }
// ...


                    // 목표 횟수 도달 시 자동 세트 완료 로직 (유지)
                    val currentEx = ExerciseManager.getCurrentExercise()
                    if (currentEx != null && internalRepsCount >= currentEx.reps) {
                        appendToDebugLog("Target reps reached ($internalRepsCount/${currentEx.reps}) for ${currentEx.name}. Auto-performing 'Set Complete'.")
                        if (skipButton.isEnabled) {
                            // skipButton.performClick()
                            // 명시적으로 세트 완료 처리 함수를 호출하는 것이 좋을 수 있습니다.
                            // 예: handleSkipOrComplete() 또는 유사한 함수
                            // 현재 skipButton.performClick()이 UI 스레드에서 문제를 일으키지 않는다면 유지 가능
                            // 하지만 performClick()은 UI 상호작용을 모방하는 것이므로, 로직 직접 호출이 더 안정적일 수 있음
                            // 여기서는 일단 기존 로직을 최대한 유지하겠습니다. 문제가 되면 이 부분을 검토합니다.
                            skipButton.performClick() // 기존 코드 유지
                        } else {
                            appendToDebugLog("WARN: Target reps reached, but skipButton is not enabled. Check state logic.")
                        }
                    }
                } else {
                    appendToDebugLog("NoGraphUpdate (Not WORKING state): ${ExerciseManager.state} | InternalReps: $internalRepsCount, Time: $time, DeviceCount: $rawCountFromDevice")
                }
            }
        } catch (e: Exception) {
            appendToDebugLog("ERROR (Size:$packetSize): ${e.message} | Data: '$data'")
            Log.e(TAG, "Error in onDataReceived (Size:$packetSize, Data:'$data')", e)
        }
    }

// ... (initializeChartComponents, setupSpeedChartAppearance, setupChartDataAndDisplay 등 나머지 함수는 이전과 동일)


    override fun onDebugMessage(message: String) {
        appendToDebugLog("BLE_MAN: $message")
    }
}
