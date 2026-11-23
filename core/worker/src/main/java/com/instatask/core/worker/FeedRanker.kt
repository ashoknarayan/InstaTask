package com.instatask.core.worker

import com.instatask.core.database.PlanEntity
import com.instatask.core.database.ReelEntity
import com.instatask.core.database.TaskEntity
import com.instatask.core.model.MoodCategory
import com.instatask.core.model.ReelType
import com.instatask.core.model.TimeBucket
import kotlin.random.Random

object FeedRanker {
    fun rank(
        reels: List<ReelEntity>,
        tasks: List<TaskEntity>,
        activePlans: List<PlanEntity>,
        nowBucket: TimeBucket,
        jitter: (ReelEntity) -> Float = { Random.nextFloat() * MAX_JITTER },
    ): List<ReelEntity> {
        val tasksById = tasks.associateBy { it.taskId }
        val plansById = activePlans.associateBy { it.planId }
        return reels.map { reel ->
            reel.copy(
                sessionOrderRank = score(
                    reel = reel,
                    tasksById = tasksById,
                    plansById = plansById,
                    nowBucket = nowBucket,
                    jitter = jitter(reel).coerceIn(0f, MAX_JITTER),
                ),
            )
        }.sortedByDescending { it.sessionOrderRank }
    }

    fun currentTimeBucket(hour: Int): TimeBucket = when (hour) {
        in 5..11 -> TimeBucket.MORNING
        in 12..16 -> TimeBucket.AFTERNOON
        in 17..20 -> TimeBucket.EVENING
        else -> TimeBucket.NIGHT
    }

    private fun score(
        reel: ReelEntity,
        tasksById: Map<String, TaskEntity>,
        plansById: Map<String, PlanEntity>,
        nowBucket: TimeBucket,
        jitter: Float,
    ): Float {
        val task = reel.linkedTaskId?.let(tasksById::get)
        val plan = reel.linkedPlanId?.let(plansById::get)
            ?: task?.parentPlanId?.let(plansById::get)
        val isCurrentPlanStep = task != null && plan != null &&
            task.stepIndex == plan.currentStepIndex
        val isActivePlanReel = reel.reelType == ReelType.PLAN && plan != null
        val timeScore = task?.preferredTimeOfDay?.let { preferred ->
            when {
                preferred == nowBucket -> 1.5f
                preferred == TimeBucket.ANY -> 0.4f
                else -> 0f
            }
        } ?: 0f
        val typeScore = when (reel.reelType) {
            ReelType.TASK -> 0.3f
            ReelType.PLAN -> 0.2f
            ReelType.MOTIVATIONAL -> 0.15f
            ReelType.PERMISSION, ReelType.INFO -> 0f
        }
        val moodScore = if (reel.moodCategory == MoodCategory.MOTIVATIONAL && task?.isUrgentTimeCrunch == true) {
            0.25f
        } else 0f

        return (if (task?.isUrgentTimeCrunch == true) URGENCY_WEIGHT else 0f) +
            timeScore +
            (if (isCurrentPlanStep) ACTIVE_STEP_WEIGHT else 0f) +
            (if (isActivePlanReel) ACTIVE_PLAN_WEIGHT else 0f) +
            typeScore + moodScore + jitter
    }

    private const val URGENCY_WEIGHT = 4f
    private const val ACTIVE_STEP_WEIGHT = 2f
    private const val ACTIVE_PLAN_WEIGHT = 0.8f
    private const val MAX_JITTER = 0.35f
}
