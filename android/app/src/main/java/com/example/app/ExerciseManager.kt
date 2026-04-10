package com.example.app

import android.util.Log
import com.example.app.exercise.ExerciseItem

/**
 * 현재 운동 세션의 상태를 나타내는 Enum 클래스.
 */
enum class SessionState {
    IDLE,       // 세션 시작 전 대기 상태
    WORKING,    // 운동 세트 진행 중
    RESTING,    // 세트 사이 휴식 중
    FINISHED    // 모든 운동 세션 완료
}

/**
 * 앱 전체의 운동 상태(어떤 운동, 몇 세트, 현재 상태 등)를 관리하는 싱글톤 객체.
 */
object ExerciseManager {
    private var exerciseList: List<ExerciseItem> = emptyList()
    private var currentExerciseIndex: Int = -1
    private var currentSet: Int = 1
    var state: SessionState = SessionState.IDLE
        private set // 외부에서는 읽기만 가능하도록 설정

    /**
     * 새로운 운동 계획으로 세션을 시작합니다.
     * @param plan Firebase 등에서 가져온 운동 목록
     */
    fun startExerciseSession(plan: List<ExerciseItem>) {
        Log.d("ExerciseManager", "startExerciseSession called. Plan size: ${plan.size}")
        if (plan.isNotEmpty()) {
            Log.d("ExerciseManager", "Plan[0] name: ${plan[0].name}")
        }

        exerciseList = plan
        currentExerciseIndex = 0
        currentSet = 1
        state = SessionState.WORKING

        Log.d("ExerciseManager", "After setup - internal list size: ${this.exerciseList.size}, index: $currentExerciseIndex, state: $state")
        if (this.exerciseList.isNotEmpty()) {
            Log.d("ExerciseManager", "Internal list[0] name: ${this.exerciseList[0].name}")
        }
    }

    fun getExerciseListSizeForDebug(): Int {
        return exerciseList.size
    }

    fun getCurrentExercise(): ExerciseItem? {
        Log.d("ExerciseManager", "getCurrentExercise called. Index: $currentExerciseIndex, Internal list size: ${this.exerciseList.size}")
        if (currentExerciseIndex in exerciseList.indices) {
            val exercise = exerciseList[currentExerciseIndex]
            Log.d("ExerciseManager", "Returning exercise: ${exercise.name}")
            return exercise
        }
        Log.d("ExerciseManager", "Returning null. currentExerciseIndex: $currentExerciseIndex, exerciseList.indices: ${exerciseList.indices}")
        return null
    }

    /**
     * 한 세트가 끝난 후 다음 단계(휴식 또는 다음 운동)로 상태를 전환합니다.
     */
    fun moveToNextStep() {
        val currentExercise = getCurrentExercise() ?: run {
            state = SessionState.FINISHED
            return
        }

        // 현재 운동의 마지막 세트가 아니라면 -> 휴식 상태로 전환하고 다음 세트로.
        if (currentSet < currentExercise.sets) {
            currentSet++
            state = SessionState.RESTING
        }
        // 현재 운동의 마지막 세트였다면 -> 다음 운동으로 이동.
        else {
            currentExerciseIndex++
            // 모든 운동이 끝났는지 확인
            if (currentExerciseIndex >= exerciseList.size) {
                state = SessionState.FINISHED
            }
            // 다음 운동이 있다면, 세트를 1로 초기화하고 휴식 상태로.
            else {
                currentSet = 1
                state = SessionState.RESTING
            }
        }
    }

    /**
     * 휴식이 끝났음을 알리고, 상태를 다시 운동 중으로 변경합니다.
     */
    fun finishRest() {
        if (state == SessionState.RESTING) {
            state = SessionState.WORKING
        }
    }

    /**
     * UI에 표시할 현재 세트 정보를 문자열로 반환합니다. (예: "1 / 3 세트")
     */
    fun getCurrentSetInfo(): String {
        val exercise = getCurrentExercise()
        return if (exercise != null) {
            "$currentSet / ${exercise.sets} 세트"
        } else {
            ""
        }
    }

    /**
     * 전체 운동 세션이 종료되었는지 확인합니다.
     */
    fun isSessionFinished(): Boolean {
        return state == SessionState.FINISHED
    }

    /**
     * 모든 상태를 초기화합니다.
     */
    fun clear() {
        exerciseList = emptyList()
        currentExerciseIndex = -1
        currentSet = 1
        state = SessionState.IDLE
    }
}