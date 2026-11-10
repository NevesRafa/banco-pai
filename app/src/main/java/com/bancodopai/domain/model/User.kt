package com.bancodopai.domain.model

enum class UserRole {
    PARENT,
    CHILD
}

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val balanceCents: Long = 0L,
    val goalName: String = "",
    val goalTargetCents: Long = 0L,
    val parentId: String? = null
) {
    val goalProgress: Float
        get() = if (goalTargetCents <= 0L) {
            0f
        } else {
            (balanceCents.toFloat() / goalTargetCents.toFloat()).coerceIn(0f, 1f)
        }

    val hasGoal: Boolean
        get() = goalName.isNotBlank() && goalTargetCents > 0L
}
