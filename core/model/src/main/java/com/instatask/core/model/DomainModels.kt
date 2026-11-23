package com.instatask.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlanStep(val index: Int, val title: String, val status: StepStatus)

data class ReelUiModel(
    val reelId: String,
    val reelType: ReelType,
    val linkedTaskId: String?,
    val linkedPlanId: String?,
    val markdownContent: String,
    val moodCategory: MoodCategory,
    val audioTrackId: String?,
    val backgroundThemeId: String,
    val permissionQuery: String?,
    val isLiked: Boolean,
    val isStarred: Boolean,
    val commentCount: Int,
) {
    val actioniserMode: ActioniserMode
        get() = when (reelType) {
            ReelType.TASK -> ActioniserMode.STOPWATCH
            ReelType.PERMISSION -> ActioniserMode.PERMISSION_TICK
            ReelType.PLAN, ReelType.MOTIVATIONAL, ReelType.INFO -> ActioniserMode.STAR
        }
}

data class ActiveStopwatchState(
    val taskId: String?,
    val taskTitle: String,
    val runState: StopwatchRunState,
    val accumulatedMs: Long,
    val lastResumedRealtimeMs: Long,
) {
    fun computeCurrentElapsedMs(nowRealtimeMs: Long): Long = when (runState) {
        StopwatchRunState.IDLE -> 0L
        StopwatchRunState.PAUSED -> accumulatedMs
        StopwatchRunState.RUNNING -> accumulatedMs +
            (nowRealtimeMs - lastResumedRealtimeMs).coerceAtLeast(0L)
    }
}
