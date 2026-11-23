package com.instatask.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.instatask.core.model.*

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val planId: String,
    val goalTitle: String,
    val planReelId: String,
    val steps: List<PlanStep>,
    val currentStepIndex: Int,
    val status: PlanStatus,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "tasks", indices = [Index("parentPlanId"), Index("status")])
data class TaskEntity(
    @PrimaryKey val taskId: String,
    val parentPlanId: String?,
    val stepIndex: Int?,
    val title: String,
    val preferredTimeOfDay: TimeBucket,
    val isUrgentTimeCrunch: Boolean,
    val isRecurringDaily: Boolean,
    val totalFocusedMs: Long = 0L,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "reels",
    indices = [Index("inActiveSessionDeck"), Index("isStarred"), Index("linkedPlanId")],
)
data class ReelEntity(
    @PrimaryKey val reelId: String,
    val reelType: ReelType,
    val linkedTaskId: String?,
    val linkedPlanId: String?,
    val markdownContent: String,
    val moodCategory: MoodCategory,
    val audioTrackId: String?,
    val backgroundThemeId: String,
    val permissionQuery: String? = null,
    val permissionApproved: Boolean = false,
    val isLiked: Boolean = false,
    val isStarred: Boolean = false,
    val starredAtEpochMs: Long? = null,
    val inActiveSessionDeck: Boolean = true,
    val sessionOrderRank: Float = 0f,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "comments", indices = [Index("reelId"), Index("isProcessedByWorker")])
data class CommentEntity(
    @PrimaryKey val commentId: String,
    val reelId: String,
    val linkedTaskId: String?,
    val text: String,
    val stopwatchSessionMs: Long?,
    val isProcessedByWorker: Boolean = false,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val isFromUser: Boolean,
    val text: String,
    val isProcessedByWorker: Boolean = false,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "memory_chunks")
data class MemoryChunkEntity(
    @PrimaryKey val chunkId: String,
    val sourceType: MemorySource,
    val contentText: String,
    val embedding: FloatArray,
    val createdAtEpochMs: Long,
)
