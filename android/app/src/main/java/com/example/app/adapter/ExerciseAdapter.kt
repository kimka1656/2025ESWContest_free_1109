package com.example.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // 실제 R 클래스 경로로 수정 필요
import com.example.app.model.Exercise // Exercise 데이터 클래스 경로

class ExerciseAdapter(
    private var exercises: MutableList<Exercise>
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    interface OnExerciseItemInteractionListener {
        fun onDeleteClicked(position: Int, exercise: Exercise)
        fun onExpandClicked(position: Int, exercise: Exercise) // 확장/축소 이벤트
    }

    private var listener: OnExerciseItemInteractionListener? = null

    fun setOnExerciseItemInteractionListener(listener: OnExerciseItemInteractionListener) {
        this.listener = listener
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerLayout: ViewGroup = itemView.findViewById(R.id.header_layout)
        val titleTextView: TextView = itemView.findViewById(R.id.tv_exercise_title)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete)
        val expandButton: ImageButton = itemView.findViewById(R.id.btn_expand)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.details_layout)

        val setsTextView: TextView = itemView.findViewById(R.id.tv_sets)
        val repsTextView: TextView = itemView.findViewById(R.id.tv_reps)
        val roundTripTimeTextView: TextView = itemView.findViewById(R.id.tv_round_trip_time)
        val restTimeTextView: TextView = itemView.findViewById(R.id.tv_rest_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val currentExercise = exercises[position]

        holder.titleTextView.text = currentExercise.name
        holder.setsTextView.text = "• 목표 세트: ${currentExercise.sets}"
        holder.repsTextView.text = "• 1세트당 목표 횟수: ${currentExercise.reps}"
        holder.roundTripTimeTextView.text = "• 1회 왕복 시간: ${currentExercise.roundTripTime}초"
        holder.restTimeTextView.text = "• 세트 간 쉬는 시간: ${currentExercise.restTime/60}분 ${currentExercise.restTime%60}초"

        // 확장/축소 상태 반영
        holder.detailsLayout.visibility = if (currentExercise.isExpanded) View.VISIBLE else View.GONE
        holder.expandButton.setImageResource(
            if (currentExercise.isExpanded) R.drawable.arrow_drop_up_24px // 확장된 경우 위쪽 화살표 (이 drawable 필요)
            else R.drawable.arrow_drop_down_24px // 축소된 경우 아래쪽 화살표
        )

        holder.deleteButton.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                listener?.onDeleteClicked(currentPosition, exercises[currentPosition])
            }
        }

        // 헤더 클릭 시 또는 확장 버튼 클릭 시 확장/축소 토글
        val expandClickListener = View.OnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                listener?.onExpandClicked(currentPosition, exercises[currentPosition])
            }
        }
        holder.headerLayout.setOnClickListener(expandClickListener)
        holder.expandButton.setOnClickListener(expandClickListener) // expand 버튼도 동일 리스너 사용
    }

    override fun getItemCount() = exercises.size

    fun removeItem(position: Int) {
        if (position >= 0 && position < exercises.size) {
            exercises.removeAt(position)
            notifyItemRemoved(position)
            // notifyItemRangeChanged(position, exercises.size) // 아이템 제거 후 인덱스 변경 알림 (선택)
        }
    }

    fun toggleExpand(position: Int) {
        if (position >= 0 && position < exercises.size) {
            val exercise = exercises[position]
            exercise.isExpanded = !exercise.isExpanded
            notifyItemChanged(position)
        }
    }

    // 전체 데이터 업데이트
    fun submitList(newExercises: List<Exercise>) {
        exercises = newExercises.toMutableList()
        notifyDataSetChanged()
    }
}
