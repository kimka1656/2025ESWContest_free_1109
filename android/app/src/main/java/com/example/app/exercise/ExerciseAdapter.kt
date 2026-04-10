package com.example.app.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R

class ExerciseAdapter(
    private val exerciseList: MutableList<ExerciseItem>,
    private val listener: OnExerciseItemInteractionListener // 리스너는 그대로 유지
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    interface OnExerciseItemInteractionListener {
        fun onDeleteClicked(exerciseItem: ExerciseItem, position: Int)
    }

    class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseTitle: TextView = itemView.findViewById(R.id.tv_exercise_title)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.details_layout)
        val setsTextView: TextView = itemView.findViewById(R.id.tv_sets)
        val repsTextView: TextView = itemView.findViewById(R.id.tv_reps)
        val roundTripTimeTextView: TextView = itemView.findViewById(R.id.tv_round_trip_time)
        val restTimeTextView: TextView = itemView.findViewById(R.id.tv_rest_time)
        val expandButton: ImageButton = itemView.findViewById(R.id.btn_expand)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exerciseList[position]

        holder.exerciseTitle.text = exercise.name
        holder.setsTextView.text = "• 목표 세트: ${exercise.sets}"
        holder.repsTextView.text = "• 1세트당 목표 횟수: ${exercise.reps}"
        holder.roundTripTimeTextView.text = "• 1회 왕복 시간: ${exercise.roundTripTime}초"
        holder.restTimeTextView.text = "• 세트 간 쉬는 시간: ${exercise.restTime}초"

        // 확장/축소 로직 (원래 방식대로 onBindViewHolder 내 로컬 isExpanded 변수 사용)
        var isExpandedLocal = false // 로컬 변수로 상태 관리
        holder.detailsLayout.visibility = View.GONE // 기본은 닫힌 상태
        // expandButton 아이콘은 기본 아이콘(arrow_drop_down_24px)으로 유지, 회전으로 상태 표시
        holder.expandButton.rotation = 0f


        holder.expandButton.setOnClickListener {
            isExpandedLocal = !isExpandedLocal
            holder.detailsLayout.visibility = if (isExpandedLocal) View.VISIBLE else View.GONE
            holder.expandButton.rotation = if (isExpandedLocal) 180f else 0f // 아이콘 회전으로 상태 표시
        }

        holder.deleteButton.setOnClickListener {
            listener.onDeleteClicked(exercise, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int {
        return exerciseList.size
    }

    fun removeItem(position: Int) {
        if (position >= 0 && position < exerciseList.size) {
            exerciseList.removeAt(position)
            notifyItemRemoved(position)
            // notifyItemRangeChanged(position, exerciseList.size) // 필요에 따라
        }
    }

    fun updateData(newExercises: List<ExerciseItem>) {
        exerciseList.clear()
        exerciseList.addAll(newExercises)
        notifyDataSetChanged()
    }
}
