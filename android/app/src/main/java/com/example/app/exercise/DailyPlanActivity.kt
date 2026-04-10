package com.example.app.exercise // 실제 패키지명으로 변경하세요

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
import com.example.app.ble.BleScanActivity
// ExerciseManager는 com.example.app.exercise 패키지에 있다고 가정합니다.
// ExerciseItem은 별도 파일(ExerciseItem.kt)에 정의되어 있고, 같은 패키지이거나 import 되었다고 가정합니다.

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject // Firestore toObject 확장 함수
import java.text.SimpleDateFormat
import java.util.*

// 1. OnExerciseItemInteractionListener 인터페이스 구현 추가
class DailyPlanActivity : AppCompatActivity(), ExerciseAdapter.OnExerciseItemInteractionListener {

    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var tvDate: TextView
    private lateinit var rvExerciseList: RecyclerView
    private lateinit var btnAddExercise: Button
    private lateinit var btnStartWorkout: Button

    private lateinit var db: FirebaseFirestore

    private val exerciseList = mutableListOf<ExerciseItem>()
    private lateinit var exerciseAdapter: ExerciseAdapter
    private var currentDate = Calendar.getInstance()

    companion object {
        private const val TAG = "DailyPlanActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_plan)

        db = FirebaseFirestore.getInstance()

        btnPrevDay = findViewById(R.id.btn_prev_day)
        btnNextDay = findViewById(R.id.btn_next_day)
        tvDate = findViewById(R.id.tv_date)
        rvExerciseList = findViewById(R.id.rv_exercise_list)
        btnAddExercise = findViewById(R.id.btn_add_exercise)
        btnStartWorkout = findViewById(R.id.btn_start_workout)

        btnPrevDay.bringToFront()
        btnNextDay.bringToFront()

        // 2. ExerciseAdapter 생성 시 리스너(this) 전달
        exerciseAdapter = ExerciseAdapter(exerciseList, this)
        rvExerciseList.layoutManager = LinearLayoutManager(this)
        rvExerciseList.adapter = exerciseAdapter

        updateDateDisplay()
        loadExercisesForCurrentDate()

        btnPrevDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateDisplay()
            loadExercisesForCurrentDate()
        }
        btnNextDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateDisplay()
            loadExercisesForCurrentDate()
        }

        btnAddExercise.setOnClickListener {
            showAddExerciseDialog()
        }

        btnStartWorkout.setOnClickListener {
            Log.d(TAG, "btnStartWorkout clicked. exerciseList size: ${exerciseList.size}")
            if (exerciseList.isNotEmpty()) {
                Log.d(TAG, "First exercise in list: ${exerciseList[0].name}")
                ExerciseManager.startExerciseSession(this.exerciseList)
                val intent = Intent(this@DailyPlanActivity, BleScanActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "운동 목록이 비어있습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDateDisplay() {
        val year = currentDate.get(Calendar.YEAR)
        val month = currentDate.get(Calendar.MONTH) + 1
        val day = currentDate.get(Calendar.DAY_OF_MONTH)
        tvDate.text = "${year}년 ${month}월 ${day}일"
    }

    private fun showAddExerciseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exercise, null)
        val etExerciseName = dialogView.findViewById<EditText>(R.id.et_exercise_name)
        val etReps = dialogView.findViewById<EditText>(R.id.et_reps)
        val etSets = dialogView.findViewById<EditText>(R.id.et_sets)
        val etRoundTripTime = dialogView.findViewById<EditText>(R.id.et_round_trip_time)
        val etRestTime = dialogView.findViewById<EditText>(R.id.et_rest_time)

        AlertDialog.Builder(this)
            .setTitle("운동 추가")
            .setView(dialogView)
            .setPositiveButton("추가") { dialog, _ ->
                val exerciseName = etExerciseName.text.toString()
                val reps = etReps.text.toString().toIntOrNull() ?: 0
                val sets = etSets.text.toString().toIntOrNull() ?: 0
                val roundTripTime = etRoundTripTime.text.toString().toIntOrNull() ?: 0
                val restTime = etRestTime.text.toString().toIntOrNull() ?: 0

                if (exerciseName.isNotBlank() && reps > 0 && sets > 0) {
                    val newExercise = ExerciseItem(name = exerciseName, sets = sets, reps = reps, roundTripTime = roundTripTime, restTime = restTime)
                    saveNewExerciseToPlan(newExercise) // 새 운동 저장 함수 호출
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Firestore에서 운동 로드 시 ExerciseItem의 id 필드도 채우도록 수정 (하위 컬렉션 사용 가정)
    private fun loadExercisesForCurrentDate() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)
        Log.d(TAG, "Loading exercises for date: $dateKey from sub-collection")

        db.collection("workout_plans").document(dateKey)
            .collection("exercises_sub_collection") // 하위 컬렉션 이름
            .orderBy("name") // 필요에 따라 정렬 기준 추가
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "Firestore success for date: $dateKey. Documents count: ${querySnapshot.size()}")
                this.exerciseList.clear()
                if (!querySnapshot.isEmpty) {
                    for (document in querySnapshot.documents) {
                        val exercise = document.toObject<ExerciseItem>()
                        if (exercise != null) {
                            // exercise.id = document.id // @DocumentId를 사용하면 이 줄은 자동으로 처리됨
                            this.exerciseList.add(exercise)
                        }
                    }
                }
                exerciseAdapter.notifyDataSetChanged()
                Log.d(TAG, "Local exerciseList updated. Size: ${this.exerciseList.size}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading exercises for $dateKey from sub-collection", e)
                Toast.makeText(this, "데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                this.exerciseList.clear()
                exerciseAdapter.notifyDataSetChanged()
            }
    }

    private fun saveNewExerciseToPlan(exerciseItem: ExerciseItem) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)
        db.collection("workout_plans").document(dateKey)
            .collection("exercises_sub_collection") // 하위 컬렉션에 추가
            .add(exerciseItem) // add()는 자동 ID로 문서를 생성
            .addOnSuccessListener { documentReference ->
                val newId = documentReference.id
                Log.d(TAG, "New exercise added to Firestore with ID: $newId for date $dateKey")
                Toast.makeText(this, "'${exerciseItem.name}' 추가됨", Toast.LENGTH_SHORT).show()
                val newExerciseWithId = exerciseItem.copy(id = newId)
                this.exerciseList.add(newExerciseWithId)
                // 추가된 아이템이 정렬된 위치에 오도록 하거나, 목록을 다시 로드할 수 있음.
                // 단순하게 마지막에 추가하고 UI 갱신:
                exerciseAdapter.notifyItemInserted(this.exerciseList.size - 1)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding new exercise for $dateKey", e)
                Toast.makeText(this, "운동 추가 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 기존 saveWorkoutPlan() 함수는 하위 컬렉션 모델과 맞지 않으므로 주석 처리 또는 수정 필요
    /*
    private fun saveWorkoutPlan() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        val exercisesToSave = this.exerciseList.map { exercise ->
            hashMapOf(
                "name" to exercise.name,
                "sets" to exercise.sets,
                "reps" to exercise.reps,
                "roundTripTime" to exercise.roundTripTime,
                "restTime" to exercise.restTime
                // ID는 Firestore 문서 자체 ID이므로 map에 포함 안 함 (하위 컬렉션 방식)
            )
        }

        // 이 방식은 전체 exercises 배열을 덮어쓰므로 하위 컬렉션 모델과 맞지 않음
        // 각 운동은 이미 개별 문서로 exercises_sub_collection에 저장됨.
        // 날짜별 계획 문서에는 날짜 정보 외 다른 메타데이터만 저장하거나, 아예 사용하지 않을 수도 있음.
        val workoutPlanData = hashMapOf(
            "date" to dateKey
            // "exercises" to exercisesToSave // 이 부분 제거 또는 수정
        )

        db.collection("workout_plans")
            .document(dateKey)
            .set(workoutPlanData) // exercises 배열 없이 날짜 정보만 저장하거나 필요한 메타데이터 저장
            .addOnSuccessListener {
                Log.d(TAG, "Workout plan metadata saved for $dateKey")
                // Toast.makeText(this, "운동 계획이 Firebase에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving workout plan metadata for $dateKey", e)
                // Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    */

    override fun onDeleteClicked(exerciseItem: ExerciseItem, position: Int) {
        if (exerciseItem.id.isBlank()) {
            Toast.makeText(this, "오류: 운동 ID가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Cannot delete. ExerciseItem ID is blank for: ${exerciseItem.name}")
            return
        }

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        AlertDialog.Builder(this)
            .setTitle("운동 삭제")
            .setMessage("'${exerciseItem.name}' 운동을 현재 계획에서 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                db.collection("workout_plans").document(dateKey)
                    .collection("exercises_sub_collection").document(exerciseItem.id)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Exercise '${exerciseItem.name}' (ID: ${exerciseItem.id}) deleted from Firestore for date $dateKey.")
                        Toast.makeText(this, "'${exerciseItem.name}' 삭제됨", Toast.LENGTH_SHORT).show()
                        exerciseAdapter.removeItem(position)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error deleting exercise '${exerciseItem.name}' (ID: ${exerciseItem.id}) for date $dateKey", e)
                        Toast.makeText(this, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
