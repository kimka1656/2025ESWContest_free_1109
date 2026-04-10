package com.example.app.model

import java.util.UUID

data class Exercise(
    val id: String = UUID.randomUUID().toString(), // 고유 ID
    var name: String,
    var sets: Int,
    var reps: Int,
    var roundTripTime: Int, // 초 단위
    var restTime: Int,      // 초 단위
    var isExpanded: Boolean = false // 확장 상태 UI 관리를 위해 추가 (선택 사항)
)
