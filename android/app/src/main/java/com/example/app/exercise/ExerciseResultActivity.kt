package com.example.app.exercise

// import android.graphics.Color // 전체 그래프용 import 제거
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R
// import com.github.mikephil.charting.charts.LineChart // LineChart import 제거
// import com.github.mikephil.charting.components.XAxis // XAxis import 제거
// import com.github.mikephil.charting.data.Entry // Entry import 제거 (Adapter에서만 사용)
// import com.github.mikephil.charting.data.LineData // LineData import 제거
// import com.github.mikephil.charting.data.LineDataSet // LineDataSet import 제거

class ExerciseResultActivity : AppCompatActivity() {

    private lateinit var tvResultTitle: TextView
    // private lateinit var lineChartExerciseProgress: LineChart // 변수 선언 제거
    private lateinit var tvTotalExerciseTimeValue: TextView
    // private lateinit var tvTotalSetsValue: TextView // 변수 선언 제거
    // private lateinit var tvTotalRepsValue: TextView // 변수 선언 제거
    private lateinit var rvExerciseResults: RecyclerView
    private lateinit var btnCloseResult: Button

    private lateinit var exerciseResultAdapter: ExerciseResultAdapter

    companion object {
        private const val TAG = "ExerciseResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_result)

        // UI 요소 초기화
        tvResultTitle = findViewById(R.id.tvResultTitle)
        // lineChartExerciseProgress = findViewById(R.id.lineChartExerciseProgress) // findViewById 제거
        tvTotalExerciseTimeValue = findViewById(R.id.tvTotalExerciseTimeValue)
        // tvTotalSetsValue = findViewById(R.id.tvTotalSetsValue) // findViewById 제거
        // tvTotalRepsValue = findViewById(R.id.tvTotalRepsValue) // findViewById 제거
        rvExerciseResults = findViewById(R.id.rvExerciseResults)
        btnCloseResult = findViewById(R.id.btnCloseResult)

        Log.d(TAG, "ExerciseResultActivity created.")

        loadAndDisplayResults()

        btnCloseResult.setOnClickListener {
            Log.d(TAG, "Close button clicked.")
            finish()
        }
    }

    private fun loadAndDisplayResults() {
        val completedResults = ExerciseManager.getCompletedExerciseResults()

        if (completedResults.isEmpty()) {
            Log.w(TAG, "No completed exercises found in ExerciseManager.")
            tvResultTitle.text = "운동 결과 없음"
            tvTotalExerciseTimeValue.text = "00:00:00"
            // tvTotalSetsValue.text = "0 세트" // 관련 UI 코드 제거
            // tvTotalRepsValue.text = "0 회" // 관련 UI 코드 제거
            // lineChartExerciseProgress.setNoDataText("표시할 운동 데이터가 없습니다.") // 관련 UI 코드 제거
            // lineChartExerciseProgress.invalidate() // 관련 UI 코드 제거
        } else {
            Log.i(TAG, "Exercise results loaded. Count: ${completedResults.size}")
            tvResultTitle.text = "운동 결과"
        }

        // RecyclerView 설정
        exerciseResultAdapter = ExerciseResultAdapter(completedResults)
        rvExerciseResults.adapter = exerciseResultAdapter
        rvExerciseResults.layoutManager = LinearLayoutManager(this)
        Log.i(TAG, "RecyclerView setup complete with ${completedResults.size} items.")

        // 전체 요약 결과 표시
        displayOverallSummary() // 파라미터 제거 (ExerciseManager 직접 사용)

        // 전체 운동 진행 그래프 설정 함수 호출 제거
        // setupOverallProgressChart(completedResults)
    }

    private fun displayOverallSummary() { // 파라미터 제거
        tvTotalExerciseTimeValue.text = ExerciseManager.getTotalExerciseDurationFormatted()
        // tvTotalSetsValue.text = "${ExerciseManager.getTotalSetsAttempted()} 세트" // 관련 UI 코드 제거
        // tvTotalRepsValue.text = "${ExerciseManager.getTotalRepsAttempted()} 회"   // 관련 UI 코드 제거
        Log.i(TAG, "Overall summary displayed (Time only).")
    }

    // setupOverallProgressChart 함수 전체 제거
    // private fun setupOverallProgressChart(results: List<ExerciseResultItem>) { ... }

    // ExerciseNameXAxisValueFormatter 내부 클래스 전체 제거
    // inner class ExerciseNameXAxisValueFormatter(private val exerciseResults: List<ExerciseResultItem>) : com.github.mikephil.charting.formatter.ValueFormatter() { ... }
}
