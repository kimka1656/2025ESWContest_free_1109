package com.example.app.exercise

import android.util.Log
import com.github.mikephil.charting.data.Entry
import java.util.concurrent.TimeUnit

// 가정: data class ExerciseItem(val name: String, val sets: Int, val reps: Int, ...)
// 가정: data class ExerciseResultItem(..., val perSetGraphData: List<List<Entry>>, ...)
// ExerciseResultItem에 perSetGraphData: List<List<Entry>> 필드가 있다고 가정합니다.
// 실제 ExerciseResultItem 클래스 정의에 맞게 필드명과 타입을 확인하세요.

enum class SessionState {
    IDLE, WORKING, RESTING, FINISHED
}

object ExerciseManager {
    private const val TAG = "ExerciseManager"

    private var exerciseList: List<ExerciseItem> = emptyList()
    private var currentExerciseIndex: Int = -1
    private var currentSet: Int = 1
    var state: SessionState = SessionState.IDLE
        private set

    private val completedExerciseResults = mutableListOf<ExerciseResultItem>()
    private val currentExerciseAchievedRepsList = mutableListOf<Int>()

    // 변경: 단일 리스트 대신, 각 세트의 그래프 데이터를 담는 리스트의 리스트로 변경
    private val currentExercisePerSetGraphData = mutableListOf<List<Entry>>()

    private var sessionStartTimeMillis: Long = 0L

    fun startExerciseSession(plan: List<ExerciseItem>) {
        Log.d(TAG, "startExerciseSession called. Plan size: ${plan.size}")
        exerciseList = ArrayList(plan)

        completedExerciseResults.clear()
        // clearTemporaryDataForNewExercise()는 새 운동 시작 시 호출되므로 여기서 호출하지 않아도 됨
        sessionStartTimeMillis = System.currentTimeMillis()

        if (exerciseList.isNotEmpty()) {
            currentExerciseIndex = 0
            currentSet = 1
            state = SessionState.WORKING
            clearTemporaryDataForNewExercise() // 첫 운동 시작 시 임시 데이터 초기화
            Log.d(TAG, "First exercise '${exerciseList[0].name}' set to WORKING. Index: $currentExerciseIndex, State: $state")
        } else {
            currentExerciseIndex = -1
            state = SessionState.IDLE
            Log.w(TAG, "Empty plan provided. State set to IDLE.")
        }
    }

    // 새 운동 항목을 시작할 때 임시 데이터 초기화
    private fun clearTemporaryDataForNewExercise() {
        currentExerciseAchievedRepsList.clear()
        currentExercisePerSetGraphData.clear() // 세트별 그래프 데이터 리스트 초기화
        Log.d(TAG, "Temporary reps and per-set graph data cleared for new/next exercise.")
    }

    /**
     * ExerciseSetActivity에서 한 세트가 완료되었을 때 호출됩니다.
     * @param achievedRepsInSet 해당 세트에서 달성한 횟수
     * @param graphDataForSet 해당 세트 동안 수집된 그래프 데이터 포인트들 (X값은 해당 세트 내의 횟수)
     */
    fun recordSetCompletion(achievedRepsInSet: Int, graphDataForSet: List<Entry>) {
        if (state != SessionState.WORKING) {
            Log.w(TAG, "recordSetCompletion called when not in WORKING state. Ignoring.")
            return
        }
        currentExerciseAchievedRepsList.add(achievedRepsInSet)
        // 변경: 전달받은 세트의 그래프 데이터를 그대로 리스트에 추가 (방어적 복사)
        currentExercisePerSetGraphData.add(ArrayList(graphDataForSet))
        Log.d(TAG, "Set $currentSet completed. Reps: $achievedRepsInSet, Graph points for this set: ${graphDataForSet.size}. Total sets recorded for item: ${currentExercisePerSetGraphData.size}")
    }

    fun getCurrentExercise(): ExerciseItem? {
        return if (currentExerciseIndex in exerciseList.indices) exerciseList[currentExerciseIndex] else null
    }

    fun moveToNextStep() {
        val exercise = getCurrentExercise() ?: run {
            state = if (exerciseList.isEmpty() && currentExerciseIndex == -1) SessionState.IDLE else SessionState.FINISHED
            Log.d(TAG, "moveToNextStep: No current exercise. State set to $state.")
            if (state == SessionState.FINISHED) logSessionDuration()
            return
        }
        Log.d(TAG, "moveToNextStep for '${exercise.name}'. CurrentSet: $currentSet / ${exercise.sets}")

        if (currentSet < exercise.sets) { // 현재 운동의 마지막 세트가 아니라면
            currentSet++
            state = SessionState.RESTING
            Log.d(TAG, "Moved to RESTING for next set. Next set: $currentSet for ${exercise.name}")
        } else { // 현재 운동의 마지막 세트 완료
            // 현재 ExerciseItem에 대한 결과 기록
            val resultItem = ExerciseResultItem(
                exerciseName = exercise.name,
                targetSets = exercise.sets,
                targetRepsPerSet = exercise.reps, // ExerciseItem에 reps가 세트당 목표 횟수라고 가정
                achievedSets = currentSet, // 이 시점에는 exercise.sets와 동일해야 함
                achievedRepsPerSet = currentExerciseAchievedRepsList.toList(), // 방어적 복사
                totalAchievedReps = currentExerciseAchievedRepsList.sum(),
                // 변경: 수집된 세트별 그래프 데이터 리스트를 전달 (방어적 복사)
                // graphData = ... // 이 필드가 없어졌거나 이름이 perSetGraphData로 변경되었다고 가정
                perSetGraphData = currentExercisePerSetGraphData.map { ArrayList(it) } // 각 내부 리스트도 복사
            )
            completedExerciseResults.add(resultItem)
            Log.i(TAG, "Exercise '${exercise.name}' completed and result recorded. Total sets done: ${resultItem.achievedSets}, Total reps: ${resultItem.totalAchievedReps}, Graph sets: ${resultItem.perSetGraphData.size}")

            state = SessionState.IDLE // 다음 운동 시작 전 대기 상태로 변경
            Log.i(TAG, "State set to IDLE. Waiting for external trigger to start next exercise.")
        }
    }

    fun prepareAndStartNextExercise(): Boolean {
        if (state != SessionState.IDLE && currentExerciseIndex >= 0) {
            // Log.w(TAG, "prepareAndStartNextExercise called when state is $state, not IDLE. Current Index: $currentExerciseIndex.")
        }

        val nextExerciseAttemptIndex = if (currentExerciseIndex == -1 && exerciseList.isNotEmpty()) 0 else currentExerciseIndex + 1

        Log.d(TAG, "prepareAndStartNextExercise: Current index before advance: $currentExerciseIndex. Attempting index: $nextExerciseAttemptIndex")

        if (nextExerciseAttemptIndex >= exerciseList.size) {
            state = SessionState.FINISHED
            logSessionDuration()
            Log.i(TAG, "prepareAndStartNextExercise: All exercises in the plan are finished. State: FINISHED.")
            return false
        } else {
            currentExerciseIndex = nextExerciseAttemptIndex
            currentSet = 1 // 새 운동 시작이므로 세트 1로 초기화
            state = SessionState.WORKING

            // 새 운동 시작 전, 이전 운동의 결과가 저장되었으므로 임시 데이터 초기화
            clearTemporaryDataForNewExercise()

            val nextExercise = exerciseList[currentExerciseIndex]
            Log.i(TAG, "prepareAndStartNextExercise: Starting next exercise '${nextExercise.name}'. Index: $currentExerciseIndex, State: WORKING, Set: $currentSet")
            return true
        }
    }

    private fun logSessionDuration() {
        if (sessionStartTimeMillis > 0L) {
            val durationMillis = System.currentTimeMillis() - sessionStartTimeMillis
            Log.i(TAG, "Total session duration: ${formatMillisToHMS(durationMillis)}")
        }
    }

    fun finishRest() {
        if (state == SessionState.RESTING) {
            state = SessionState.WORKING
            val currentExerciseName = getCurrentExercise()?.name ?: "Unknown Exercise"
            Log.d(TAG, "Rest finished. State set to WORKING for exercise '$currentExerciseName', set $currentSet.")
        } else {
            Log.w(TAG, "finishRest called but state is not RESTING. Current State: $state")
        }
    }

    fun getCurrentSetInfo(): String {
        val exercise = getCurrentExercise()
        return if (exercise != null) {
            "$currentSet / ${exercise.sets} 세트"
        } else {
            ""
        }
    }

    fun isSessionFinished(): Boolean {
        return state == SessionState.FINISHED
    }

    fun clear() {
        Log.d(TAG, "clear() called")
        exerciseList = emptyList()
        currentExerciseIndex = -1
        currentSet = 1
        state = SessionState.IDLE

        completedExerciseResults.clear()
        clearTemporaryDataForNewExercise()
        sessionStartTimeMillis = 0L
        Log.i(TAG, "All exercise data and results cleared.")
    }

    // --- 결과 제공 및 요약 정보 함수들 ---
    fun getCompletedExerciseResults(): List<ExerciseResultItem> {
        return completedExerciseResults.toList() // 외부 변경 방지를 위해 복사본 제공
    }

    fun getTotalExerciseDurationFormatted(): String {
        if (sessionStartTimeMillis == 0L ) { //|| state != SessionState.FINISHED 세션 중에도 시간 표시
            val currentTime = if (sessionStartTimeMillis == 0L) 0L else System.currentTimeMillis() - sessionStartTimeMillis
            return formatMillisToHMS(currentTime)
        }
        val durationMillis = System.currentTimeMillis() - sessionStartTimeMillis
        return formatMillisToHMS(durationMillis)
    }

    private fun formatMillisToHMS(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun getTotalSetsAttempted(): Int {
        // 실제로 각 운동에서 달성한 세트 수의 합
        return completedExerciseResults.sumOf { it.achievedSets }
    }

    fun getTotalRepsAttempted(): Int {
        // 실제로 각 운동에서 달성한 총 횟수의 합
        return completedExerciseResults.sumOf { it.totalAchievedReps }
    }

    fun getExerciseListSizeForDebug(): Int {
        return exerciseList.size
    }
}
