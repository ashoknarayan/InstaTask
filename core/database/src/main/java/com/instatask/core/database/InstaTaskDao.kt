package com.instatask.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InstaTaskDao {
    @Query("SELECT * FROM reels WHERE inActiveSessionDeck = 1 ORDER BY sessionOrderRank DESC LIMIT 60")
    fun observeActiveSessionDeck(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE isStarred = 1 ORDER BY starredAtEpochMs DESC")
    fun observeStarredReels(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE isLiked = 1 ORDER BY createdAtEpochMs DESC")
    fun observeLikedReels(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE isLiked = 1 ORDER BY createdAtEpochMs DESC LIMIT 5")
    suspend fun getRecentLikedReels(): List<ReelEntity>

    @Query("SELECT * FROM reels WHERE reelId = :reelId")
    suspend fun getReel(reelId: String): ReelEntity?

    @Query("SELECT * FROM comments WHERE reelId = :reelId ORDER BY createdAtEpochMs ASC")
    fun observeCommentsForReel(reelId: String): Flow<List<CommentEntity>>

    @Query("UPDATE reels SET isLiked = :liked WHERE reelId = :reelId")
    suspend fun setReelLiked(reelId: String, liked: Boolean)

    @Query("UPDATE reels SET isStarred = :starred, starredAtEpochMs = :starredAt WHERE reelId = :reelId")
    suspend fun setReelStarred(reelId: String, starred: Boolean, starredAt: Long?)

    @Query("UPDATE reels SET permissionApproved = 1, markdownContent = :updatedMarkdown WHERE reelId = :reelId")
    suspend fun approvePermissionReel(reelId: String, updatedMarkdown: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(msg: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE isProcessedByWorker = 0 ORDER BY createdAtEpochMs ASC")
    suspend fun getUnprocessedChatMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMs ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM comments WHERE isProcessedByWorker = 0 ORDER BY createdAtEpochMs ASC")
    suspend fun getUnprocessedComments(): List<CommentEntity>

    @Query("UPDATE chat_messages SET isProcessedByWorker = 1 WHERE messageId IN (:ids)")
    suspend fun markChatsProcessed(ids: List<String>)

    @Query("UPDATE comments SET isProcessedByWorker = 1 WHERE commentId IN (:ids)")
    suspend fun markCommentsProcessed(ids: List<String>)

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE'")
    suspend fun getActiveTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentPlanId = :planId AND stepIndex = :stepIndex AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTaskForPlanStep(planId: String, stepIndex: Int): TaskEntity?

    @Query("SELECT * FROM reels WHERE linkedTaskId = :taskId AND reelType = 'TASK' LIMIT 1")
    suspend fun getTaskReel(taskId: String): ReelEntity?

    @Query("SELECT * FROM plans WHERE planId = :planId")
    suspend fun getPlanById(planId: String): PlanEntity?

    @Query("SELECT * FROM plans WHERE status = 'ACTIVE'")
    suspend fun getActivePlans(): List<PlanEntity>

    @Query("UPDATE tasks SET totalFocusedMs = totalFocusedMs + :deltaMs WHERE taskId = :taskId")
    suspend fun addFocusedTime(taskId: String, deltaMs: Long)

    @Query("UPDATE tasks SET status = 'COMPLETED' WHERE taskId = :taskId")
    suspend fun markTaskCompleted(taskId: String)

    @Query("DELETE FROM reels WHERE linkedTaskId = :taskId AND reelType = 'TASK'")
    suspend fun deleteTaskReelsForTask(taskId: String)

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Update
    suspend fun updateReels(reels: List<ReelEntity>)

    @Query("UPDATE reels SET markdownContent = :newMarkdown WHERE reelId = :planReelId")
    suspend fun updatePlanReelMarkdown(planReelId: String, newMarkdown: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: PlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReels(reels: List<ReelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryChunk(chunk: MemoryChunkEntity)

    @Query("SELECT * FROM memory_chunks ORDER BY createdAtEpochMs DESC LIMIT 2000")
    suspend fun getAllMemoryChunks(): List<MemoryChunkEntity>

    @Query("SELECT COUNT(*) FROM reels WHERE inActiveSessionDeck = 1")
    suspend fun activeDeckCount(): Int

    @Query("SELECT * FROM reels WHERE inActiveSessionDeck = 1")
    suspend fun getActiveDeck(): List<ReelEntity>

    @Query("UPDATE reels SET inActiveSessionDeck = 0")
    suspend fun clearActiveDeck()
}
