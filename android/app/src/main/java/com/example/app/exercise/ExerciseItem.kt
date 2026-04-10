package com.example.app.exercise

import com.google.firebase.firestore.DocumentId

data class ExerciseItem(
    @DocumentId
    var id: String = "",       // Default value provided
    val name: String = "",     // Default value provided
    val sets: Int = 0,         // Default value provided
    val reps: Int = 0,         // Default value provided
    val roundTripTime: Int = 0,// Default value provided
    val restTime: Int = 0
)