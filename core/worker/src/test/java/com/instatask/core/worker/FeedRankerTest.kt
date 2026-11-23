package com.instatask.core.worker

import com.instatask.core.database.PlanEntity
import com.instatask.core.database.ReelEntity
import com.instatask.core.database.TaskEntity
import com.instatask.core.model.MoodCategory
import com.instatask.core.model.PlanStatus
import com.instatask.core.model.PlanStep
import com.instatask.core.model.ReelType
import com.instatask.core.model.StepStatus
import com.instatask.core.model.TaskStatus
import com.instatask.core.model.TimeBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRankerTest {
    @Test
    fun urgentTaskRanksAheadOfOrdinaryTask() {
        val urgent = taskReel("urgent")
        val ordinary = taskReel("ordinary")
        val tasks = listOf(
            task("urgent", urgent = true),
            task("ordinary", urgent = false),
        )

        val ranked = FeedRanker.rank(
            reels = listOf(ordinary, urgent),
            tasks = tasks,
            activePlans = emptyList(),
            nowBucket = TimeBucket.NIGHT,
            jitter = { 0f },
        )

        assertEquals("urgent", ranked.first().linkedTaskId)
    }

    @Test
    fun matchingTimeBucketRanksAheadOfNonmatchingTask() {
        val morning = taskReel("morning")
        val evening = taskReel("evening")
        val tasks = listOf(
            task("morning", time = TimeBucket.MORNING),
            task("evening", time = TimeBucket.EVENING),
        )

        val ranked = FeedRanker.rank(
            reels = listOf(evening, morning),
            tasks = tasks,
            activePlans = emptyList(),
            nowBucket = TimeBucket.MORNING,
            jitter = { 0f },
        )

        assertEquals("morning", ranked.first().linkedTaskId)
    }

    @Test
    fun activePlanCurrentStepGetsContinuityBoostAndTimeBucketsMapCorrectly() {
        val current = taskReel("current", planId = "plan")
        val unrelated = taskReel("unrelated")
        val tasks = listOf(
            task("current", parentPlanId = "plan", step = 1),
            task("unrelated", parentPlanId = "plan", step = 2),
        )
        val plan = PlanEntity(
            planId = "plan",
            goalTitle = "Goal",
            planReelId = "plan-reel",
            steps = listOf(PlanStep(1, "Current", StepStatus.CURRENT)),
            currentStepIndex = 1,
            status = PlanStatus.ACTIVE,
            updatedAtEpochMs = 0L,
        )

        val ranked = FeedRanker.rank(
            reels = listOf(unrelated, current),
            tasks = tasks,
            activePlans = listOf(plan),
            nowBucket = TimeBucket.ANY,
            jitter = { 0f },
        )

        assertEquals("current", ranked.first().linkedTaskId)
        assertEquals(TimeBucket.MORNING, FeedRanker.currentTimeBucket(5))
        assertEquals(TimeBucket.AFTERNOON, FeedRanker.currentTimeBucket(12))
        assertEquals(TimeBucket.EVENING, FeedRanker.currentTimeBucket(17))
        assertEquals(TimeBucket.NIGHT, FeedRanker.currentTimeBucket(21))
        assertTrue(ranked.first().sessionOrderRank > ranked.last().sessionOrderRank)
    }

    private fun taskReel(taskId: String, planId: String? = null) = ReelEntity(
        reelId = "reel-$taskId",
        reelType = ReelType.TASK,
        linkedTaskId = taskId,
        linkedPlanId = planId,
        markdownContent = taskId,
        moodCategory = MoodCategory.ENERGETIC,
        audioTrackId = null,
        backgroundThemeId = "test",
        createdAtEpochMs = 0L,
    )

    private fun task(
        taskId: String,
        urgent: Boolean = false,
        time: TimeBucket = TimeBucket.ANY,
        parentPlanId: String? = null,
        step: Int? = null,
    ) = TaskEntity(
        taskId = taskId,
        parentPlanId = parentPlanId,
        stepIndex = step,
        title = taskId,
        preferredTimeOfDay = time,
        isUrgentTimeCrunch = urgent,
        isRecurringDaily = false,
        totalFocusedMs = 0L,
        status = TaskStatus.ACTIVE,
        createdAtEpochMs = 0L,
    )
}
