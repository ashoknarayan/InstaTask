package com.instatask.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModelsTest {
    @Test
    fun computesElapsedTimeForEachStopwatchState() {
        val idle = ActiveStopwatchState(null, "", StopwatchRunState.IDLE, 0L, 0L)
        val paused = ActiveStopwatchState(null, "", StopwatchRunState.PAUSED, 4_000L, 0L)
        val running = ActiveStopwatchState(null, "", StopwatchRunState.RUNNING, 4_000L, 1_000L)

        assertEquals(0L, idle.computeCurrentElapsedMs(9_000L))
        assertEquals(4_000L, paused.computeCurrentElapsedMs(9_000L))
        assertEquals(8_000L, running.computeCurrentElapsedMs(5_000L))
        assertEquals(4_000L, running.computeCurrentElapsedMs(500L))
    }

    @Test
    fun actioniserModeMatchesReelType() {
        assertEquals(ActioniserMode.STOPWATCH, reel(ReelType.TASK).actioniserMode)
        assertEquals(ActioniserMode.PERMISSION_TICK, reel(ReelType.PERMISSION).actioniserMode)
        assertEquals(ActioniserMode.STAR, reel(ReelType.PLAN).actioniserMode)
        assertEquals(ActioniserMode.STAR, reel(ReelType.MOTIVATIONAL).actioniserMode)
        assertEquals(ActioniserMode.STAR, reel(ReelType.INFO).actioniserMode)
    }

    private fun reel(type: ReelType) = ReelUiModel(
        reelId = "reel",
        reelType = type,
        linkedTaskId = null,
        linkedPlanId = null,
        markdownContent = "",
        moodCategory = MoodCategory.PEACEFUL,
        audioTrackId = null,
        backgroundThemeId = "test",
        permissionQuery = null,
        isLiked = false,
        isStarred = false,
        commentCount = 0,
    )
}
