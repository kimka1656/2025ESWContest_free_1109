package com.example.app.exercise

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // Make sure this R is correctly imported
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class ExerciseResultAdapter(
    private val results: List<ExerciseResultItem>
    // private val context: Context // Context가 필요할 경우 생성자에서 받아올 수 있음
) : RecyclerView.Adapter<ExerciseResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvExerciseName: TextView = view.findViewById(R.id.tvExerciseNameResultDetail)
        val tvTargetSetsReps: TextView = view.findViewById(R.id.tvTargetSetsRepsResultDetail)
        val tvAchievedSetsReps: TextView = view.findViewById(R.id.tvAchievedSetsRepsResultDetail)
        val ivExerciseStatus: ImageView = view.findViewById(R.id.ivExerciseStatusDetail)
        // val lineChart: LineChart = view.findViewById(R.id.lineChartIndividualExerciseDetail) // 기존 단일 차트 제거
        val chartsContainer: LinearLayout = view.findViewById(R.id.charts_container_result_detail) // 새 컨테이너
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_result_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resultItem = results[position]
        val context = holder.itemView.context // 컨텍스트 가져오기

        holder.tvExerciseName.text = resultItem.exerciseName
        holder.tvTargetSetsReps.text =
            "목표: ${resultItem.targetSets} 세트 / ${resultItem.targetRepsPerSet} 회"
        holder.tvAchievedSetsReps.text =
            "달성: ${resultItem.achievedSets} 세트 / ${resultItem.totalAchievedReps} 회 (세트별: ${resultItem.achievedRepsPerSet.joinToString(", ")})"

        if (resultItem.totalAchievedReps >= (resultItem.targetSets * resultItem.targetRepsPerSet * 0.8)) { // 80% 이상 달성시 성공
            holder.ivExerciseStatus.setImageResource(R.drawable.check_circle_24px)
        } else {
            holder.ivExerciseStatus.setImageResource(R.drawable.cancel_24px)
        }

        // 그래프 컨테이너 초기화 (이전 뷰들 제거)
        holder.chartsContainer.removeAllViews()

        // perSetGraphData 필드가 있는지 확인 (실제 필드명으로 변경하세요)
        // 여기서는 resultItem.perSetGraphData 가 List<List<Entry>> 형태라고 가정합니다.
        // 만약 필드명이 다르거나, 없을 경우 이 부분을 실제 데이터 구조에 맞게 수정해야 합니다.
        val perSetData = resultItem.perSetGraphData // 실제 필드명으로 대체!

        if (perSetData.isNullOrEmpty()) {
            val noDataTextView = TextView(context)
            noDataTextView.text = "표시할 세트별 그래프 데이터가 없습니다."
            noDataTextView.setPadding(16,16,16,16)
            holder.chartsContainer.addView(noDataTextView)
        } else {
            perSetData.forEachIndexed { index, setData ->
                if (setData.isNotEmpty()) {
                    // 세트 제목 TextView 추가
                    val setTitleTextView = TextView(context)
                    setTitleTextView.text = "세트 ${index + 1} 결과 (시간: 초)"
                    setTitleTextView.textSize = 16f // 텍스트 크기
                    setTitleTextView.setTextColor(ContextCompat.getColor(context, R.color.black)) // 색상
                    val titleParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    titleParams.setMargins(0, if (index == 0) 0 else 24, 0, 8) // 마진 (dp 단위 변환 필요시 유틸함수 사용)
                    setTitleTextView.layoutParams = titleParams
                    holder.chartsContainer.addView(setTitleTextView)

                    // 새 LineChart 객체 생성
                    val lineChart = LineChart(context)
                    val chartParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 200) // 높이를 200dp로 설정 (dpToPx 함수 필요)
                    )
                    chartParams.setMargins(0,0,0,16) // 하단 마진
                    lineChart.layoutParams = chartParams

                    // 개별 차트 설정 및 데이터 바인딩
                    setupChartForSet(lineChart, setData)

                    holder.chartsContainer.addView(lineChart)
                }
            }
        }
    }

    override fun getItemCount(): Int = results.size

    private fun setupChartForSet(chart: LineChart, setData: List<Entry>) {
        val lineDataSet = LineDataSet(setData, "운동 속도 (초)") // 각 세트 데이터의 라벨
        lineDataSet.color = Color.rgb(0, 120, 200) // 세트별 차트 색상 (필요시 다양화)
        lineDataSet.setCircleColor(lineDataSet.color)
        lineDataSet.circleRadius = 3f
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.mode = LineDataSet.Mode.LINEAR
        lineDataSet.setDrawValues(false) // 차트 위 값 텍스트 숨김 (선택)

        val lineData = LineData(lineDataSet)
        chart.data = lineData

        chart.description.isEnabled = false
        chart.legend.isEnabled = true // 범례 표시 (데이터셋 라벨이 있으므로)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f // X축은 횟수이므로 1단위
        xAxis.axisMinimum = 1f // X축 1부터 시작 (데이터가 1부터 시작한다고 가정)
        // X축 최대값은 데이터에 따라 자동 설정되도록 둠 (또는 setData.size.toFloat() + 0.5f 등으로 설정 가능)

        val leftAxis = chart.axisLeft
        leftAxis.axisMinimum = 0f // Y축(시간)은 항상 0부터 시작
        // leftAxis.axisMaximum = ... // Y축 최대값은 자동 조절 또는 필요시 고정값 설정
        // leftAxis.setLabelCount(6, false) // Y축 레이블 개수 제어 (이전 논의됨)

        chart.axisRight.isEnabled = false

        // 사용자와의 상호작용 설정 (이전 요청사항 반영)
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.isScaleXEnabled = false // X축 확대/축소 비활성화
        chart.isScaleYEnabled = true  // Y축 확대/축소 가능
        chart.setPinchZoom(false)     // X축 스케일 비활성화 시 핀치줌 false

        // chart.animateX(700) // 각 차트 애니메이션 (선택 사항)
        chart.invalidate() // 차트 새로고침
    }

    // dp를 px로 변환하는 유틸리티 함수 (Adapter 내에 두거나 별도 유틸 클래스에 둘 수 있음)
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
