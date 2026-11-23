package com.instatask.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.instatask.core.ai.LocalRagRetriever
import com.instatask.core.ai.TaskIntelligenceEngine
import com.instatask.core.database.ChatMessageEntity
import com.instatask.core.database.CommentEntity
import com.instatask.core.database.InstaTaskDatabase
import com.instatask.core.database.InstaTaskDao
import com.instatask.core.database.PlanEntity
import com.instatask.core.database.PlanMarkdownFormatter
import com.instatask.core.database.ReelEntity
import com.instatask.core.database.TaskEntity
import com.instatask.core.model.MemorySource
import com.instatask.core.model.MoodCategory
import com.instatask.core.model.PlanStatus
import com.instatask.core.model.PlanStep
import com.instatask.core.model.ReelType
import com.instatask.core.model.StepStatus
import com.instatask.core.model.TaskStatus
import com.instatask.core.model.TimeBucket
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime
import java.util.UUID

@HiltWorker
class InterSessionBatchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: InstaTaskDatabase,
    private val dao: InstaTaskDao,
    private val aiEngine: TaskIntelligenceEngine,
    private val ragRetriever: LocalRagRetriever,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chats = dao.getUnprocessedChatMessages()
        val comments = dao.getUnprocessedComments()

        if (chats.isNotEmpty() || comments.isNotEmpty()) {
            val activeTasks = dao.getActiveTasks()
            val delta = aiEngine.extractUserDelta(
                rawNotesAndComments = chats.map { it.text } + comments.map { it.text },
                activeTasks = activeTasks.map { it.taskId to it.title },
            )
            database.withTransaction {
                processDelta(delta = delta, comments = comments, activeTasks = activeTasks)
                dao.markChatsProcessed(chats.map { it.messageId })
                dao.markCommentsProcessed(comments.map { it.commentId })
                ensureStarterDeckIfNeeded()
                rankAndPersistActiveDeck()
            }
        } else {
            database.withTransaction {
                ensureStarterDeckIfNeeded()
                rankAndPersistActiveDeck()
            }
        }
        return Result.success()
    }

    private suspend fun processDelta(
        delta: com.instatask.core.ai.ExtractedDelta,
        comments: List<CommentEntity>,
        activeTasks: List<TaskEntity>,
    ) {
        delta.coreMotivations.forEach {
            ragRetriever.indexMemory(MemorySource.ONBOARDING_WHY, it)
        }
        comments.forEach {
            ragRetriever.indexMemory(MemorySource.COMMENT_FEEDBACK, it.text)
        }

        for (completedId in delta.completedTaskIds) {
            val task = activeTasks.find { it.taskId == completedId } ?: continue
            dao.markTaskCompleted(completedId)
            dao.deleteTaskReelsForTask(completedId)
            ragRetriever.indexMemory(MemorySource.COMPLETED_WIN, "Completed: ${task.title}")
            task.parentPlanId?.let { planId ->
                advanceParentPlanInPlace(planId, task.stepIndex ?: 0)
            }
        }

        for (draft in delta.newTasks) {
            val taskId = UUID.randomUUID().toString()
            val task = TaskEntity(
                taskId = taskId,
                parentPlanId = null,
                stepIndex = null,
                title = draft.title,
                preferredTimeOfDay = draft.timeBucket,
                isUrgentTimeCrunch = draft.isUrgentTimeCrunch,
                isRecurringDaily = draft.isRecurringDaily,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            dao.upsertTask(task)
            val reelType = if (draft.isUrgentTimeCrunch) ReelType.MOTIVATIONAL else ReelType.TASK
            val context = ragRetriever.retrieveTopK(draft.title)
            val markdown = aiEngine.generateReelMarkdown(reelType, draft.title, context, emptyList())
            dao.upsertReels(
                listOf(
                    ReelEntity(
                        reelId = UUID.randomUUID().toString(),
                        reelType = reelType,
                        linkedTaskId = taskId,
                        linkedPlanId = null,
                        markdownContent = markdown,
                        moodCategory = if (draft.isUrgentTimeCrunch) {
                            MoodCategory.MOTIVATIONAL
                        } else {
                            MoodCategory.ENERGETIC
                        },
                        audioTrackId = null,
                        backgroundThemeId = if (draft.isUrgentTimeCrunch) {
                            "motivation_amber"
                        } else {
                            "energy_blue"
                        },
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
        }

        for (goal in delta.newGoals) {
            val planId = UUID.randomUUID().toString()
            val planReelId = UUID.randomUUID().toString()
            val steps = goal.orderedSteps.mapIndexed { index, title ->
                PlanStep(index, title, if (index == 0) StepStatus.CURRENT else StepStatus.PENDING)
            }
            val plan = PlanEntity(
                planId = planId,
                goalTitle = goal.goalTitle,
                planReelId = planReelId,
                steps = steps,
                currentStepIndex = steps.firstOrNull()?.index ?: 0,
                status = if (steps.isEmpty()) PlanStatus.COMPLETED else PlanStatus.ACTIVE,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            dao.upsertPlan(plan)
            dao.upsertReels(
                listOf(
                    ReelEntity(
                        reelId = planReelId,
                        reelType = ReelType.PLAN,
                        linkedTaskId = null,
                        linkedPlanId = planId,
                        markdownContent = PlanMarkdownFormatter.renderPlanReel(goal.goalTitle, steps),
                        moodCategory = MoodCategory.PEACEFUL,
                        audioTrackId = null,
                        backgroundThemeId = "calm_mint",
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
            steps.firstOrNull()?.let { step ->
                createTaskForPlanStep(plan, step)
            }
            if (goal.requiresExternalFacts) {
                createPermissionReel(goal.suggestedSearchQuery ?: goal.goalTitle)
            }
        }

        delta.needsWebLookupQuery?.let { query -> createPermissionReel(query) }
    }

    private suspend fun advanceParentPlanInPlace(planId: String, completedStepIndex: Int) {
        val plan = dao.getPlanById(planId) ?: return
        val nextStep = plan.steps
            .filter { it.index > completedStepIndex }
            .minByOrNull { it.index }
        val updatedSteps = plan.steps.map { step ->
            when {
                step.index <= completedStepIndex -> step.copy(status = StepStatus.COMPLETED)
                step.index == nextStep?.index -> step.copy(status = StepStatus.CURRENT)
                else -> step.copy(status = StepStatus.PENDING)
            }
        }
        val updatedPlan = plan.copy(
            steps = updatedSteps,
            currentStepIndex = nextStep?.index ?: (completedStepIndex + 1),
            status = if (nextStep == null) PlanStatus.COMPLETED else PlanStatus.ACTIVE,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        dao.updatePlan(updatedPlan)
        dao.updatePlanReelMarkdown(
            plan.planReelId,
            PlanMarkdownFormatter.renderPlanReel(plan.goalTitle, updatedSteps),
        )
        if (nextStep != null && dao.getActiveTaskForPlanStep(planId, nextStep.index) == null) {
            createTaskForPlanStep(updatedPlan, nextStep)
        }
    }

    private suspend fun createTaskForPlanStep(plan: PlanEntity, step: PlanStep) {
        val taskId = UUID.randomUUID().toString()
        val task = TaskEntity(
            taskId = taskId,
            parentPlanId = plan.planId,
            stepIndex = step.index,
            title = step.title,
            preferredTimeOfDay = TimeBucket.ANY,
            isUrgentTimeCrunch = false,
            isRecurringDaily = false,
            status = TaskStatus.ACTIVE,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        dao.upsertTask(task)
        val memories = ragRetriever.retrieveTopK(step.title)
        val markdown = aiEngine.generateReelMarkdown(ReelType.TASK, step.title, memories, emptyList())
        dao.upsertReels(
            listOf(
                ReelEntity(
                    reelId = UUID.randomUUID().toString(),
                    reelType = ReelType.TASK,
                    linkedTaskId = taskId,
                    linkedPlanId = plan.planId,
                    markdownContent = markdown,
                    moodCategory = MoodCategory.ENERGETIC,
                    audioTrackId = null,
                    backgroundThemeId = "energy_blue",
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private suspend fun createPermissionReel(query: String) {
        val cleanQuery = query.trim().take(180)
        if (cleanQuery.isBlank()) return
        dao.upsertReels(
            listOf(
                ReelEntity(
                    reelId = UUID.randomUUID().toString(),
                    reelType = ReelType.PERMISSION,
                    linkedTaskId = null,
                    linkedPlanId = null,
                    markdownContent = "## External Research Request\n\nAllow InstaTask to search the web once for **$cleanQuery**?\n\n- Tap ✓ to approve\n- Scroll past to decline",
                    moodCategory = MoodCategory.SILENT,
                    audioTrackId = null,
                    backgroundThemeId = "permission_black",
                    permissionQuery = cleanQuery,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private suspend fun ensureStarterDeckIfNeeded() {
        if (dao.activeDeckCount() > 0) return
        val now = System.currentTimeMillis()
        val starter = listOf(
            starterReel(
                "starter-motivation",
                ReelType.MOTIVATIONAL,
                "## You are ready to begin\n\n**Start small.** One honest action is enough.",
                MoodCategory.MOTIVATIONAL,
                "motivation_amber",
                now,
            ),
            starterReel(
                "starter-talk",
                ReelType.INFO,
                "## Talk With Me\n\nTell me what is on your mind. A deadline, a goal, or even a messy vent.",
                MoodCategory.PEACEFUL,
                "calm_mint",
                now,
            ),
            starterReel(
                "starter-task",
                ReelType.TASK,
                "## Choose today's one thing\n\n- Open Talk With Me\n- Add one clear task",
                MoodCategory.ENERGETIC,
                "energy_blue",
                now,
            ),
            starterReel(
                "starter-plan",
                ReelType.PLAN,
                "## A simple weekly reset\n\n- ➡️ Clarify the priority\n- ⬜ Choose two focus blocks\n- ⬜ Review what changed",
                MoodCategory.PEACEFUL,
                "calm_mint",
                now,
            ),
        ).map { fresh ->
            dao.getReel(fresh.reelId)?.copy(inActiveSessionDeck = true) ?: fresh
        }
        dao.upsertReels(starter)
    }

    private fun starterReel(
        id: String,
        type: ReelType,
        content: String,
        mood: MoodCategory,
        theme: String,
        createdAt: Long,
    ) = ReelEntity(
        reelId = id,
        reelType = type,
        linkedTaskId = null,
        linkedPlanId = null,
        markdownContent = content,
        moodCategory = mood,
        audioTrackId = null,
        backgroundThemeId = theme,
        createdAtEpochMs = createdAt,
    )

    private suspend fun rankAndPersistActiveDeck() {
        val reels = dao.getActiveDeck()
        if (reels.isEmpty()) return
        val ranked = FeedRanker.rank(
            reels = reels,
            tasks = dao.getActiveTasks(),
            activePlans = dao.getActivePlans(),
            nowBucket = FeedRanker.currentTimeBucket(LocalTime.now().hour),
        )
        dao.updateReels(
            ranked.mapIndexed { index, reel ->
                reel.copy(
                    inActiveSessionDeck = index < SESSION_DECK_CAP,
                    sessionOrderRank = ranked.size - index.toFloat(),
                )
            },
        )
    }

    private companion object {
        const val SESSION_DECK_CAP = 60
    }
}
