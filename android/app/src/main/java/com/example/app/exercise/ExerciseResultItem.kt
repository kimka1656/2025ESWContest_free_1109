package com.example.app.exercise

import com.github.mikephil.charting.data.Entry

/**
 * 개별 운동의 결과 데이터를 담는 클래스
 *
 * @property exerciseName 운동 이름
 * @property targetSets 목표 세트 수
 * @property targetRepsPerSet 세트당 목표 반복 횟수
 * @property achievedSets 실제 수행한 세트 수
 * @property achievedRepsPerSet 각 세트별 실제 수행한 반복 횟수 리스트
 * @property totalAchievedReps 실제 수행한 총 반복 횟수
 * @property graphData 각 운동의 세트별 진행 상황을 나타내는 그래프 데이터.
 *                   예: List<Entry>는 각 세트의 평균 강도 (Entry(setNumber.toFloat(), averageIntensity.toFloat()))
 *                   또는 List<List<Entry>> 형태로 각 세트별 상세 센서 데이터 그래프를 표현할 수도 있음.
 *                   우선은 단일 리스트로 가정하고, 필요시 확장.
 */
data class ExerciseResultItem(
    val exerciseName: String,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val achievedSets: Int,
    val achievedRepsPerSet: List<Int>, // 각 세트에서 몇 회 했는지
    val totalAchievedReps: Int, // 전체 운동에서 총 몇 회 했는지
    val perSetGraphData: List<List<Entry>> // MPAndroidChart용 데이터
)
