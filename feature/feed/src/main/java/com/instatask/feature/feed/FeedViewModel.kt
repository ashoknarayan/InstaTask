package com.instatask.feature.feed

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.instatask.core.database.CommentEntity
import com.instatask.core.database.InstaTaskDao
import com.instatask.core.database.ReelEntity
import com.instatask.core.media.StopwatchForegroundService
import com.instatask.core.media.StopwatchState
import com.instatask.core.worker.ConstrainedWebSearchWorker
import com.instatask.core.worker.FeedOrdering
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    app: Application,
    private val dao: InstaTaskDao,
    val stopwatchState: StopwatchState,
) : AndroidViewModel(app) {
    data class StopwatchReflection(
        val sessionId: String,
        val reel: ReelEntity,
        val elapsedMs: Long,
    )

    val reels = dao.observeActiveSessionDeck()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _stopwatchReflection = MutableStateFlow<StopwatchReflection?>(null)
    val stopwatchReflection = _stopwatchReflection.asStateFlow()

    init {
        viewModelScope.launch {
            stopwatchState.completedSession.collect { event ->
                if (event == null) return@collect
                dao.getTaskReel(event.taskId)?.let { reel ->
                    _stopwatchReflection.value = StopwatchReflection(
                        sessionId = event.sessionId,
                        reel = reel,
                        elapsedMs = event.elapsedMs,
                    )
                }
            }
        }
    }

    fun dismissStopwatchReflection() {
        _stopwatchReflection.value?.let {
            stopwatchState.acknowledgeCompletedSession(it.sessionId)
        }
        _stopwatchReflection.value = null
    }

    fun toggleLike(reel: ReelEntity) =
        viewModelScope.launch { dao.setReelLiked(reel.reelId, !reel.isLiked) }

    fun toggleStar(reel: ReelEntity) =
        viewModelScope.launch {
            dao.setReelStarred(
                reel.reelId,
                !reel.isStarred,
                if (reel.isStarred) null else System.currentTimeMillis(),
            )
        }

    fun addComment(reel: ReelEntity, text: String, elapsedMs: Long? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            dao.insertComment(
                CommentEntity(
                    commentId = UUID.randomUUID().toString(),
                    reelId = reel.reelId,
                    linkedTaskId = reel.linkedTaskId,
                    text = text,
                    stopwatchSessionMs = elapsedMs,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            FeedOrdering.enqueueBatch(WorkManager.getInstance(getApplication()))
        }
    }

    fun startTask(reel: ReelEntity) {
        val intent = Intent(getApplication(), StopwatchForegroundService::class.java).apply {
            action = StopwatchForegroundService.ACTION_START
            putExtra(StopwatchForegroundService.EXTRA_TASK_ID, reel.linkedTaskId)
            putExtra(StopwatchForegroundService.EXTRA_TASK_TITLE, reel.markdownContent.lineSequence().firstOrNull().orEmpty())
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun pauseStopwatch() {
        val intent = Intent(getApplication(), StopwatchForegroundService::class.java).apply {
            action = StopwatchForegroundService.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
    }

    fun resumeStopwatch() {
        val intent = Intent(getApplication(), StopwatchForegroundService::class.java).apply {
            action = StopwatchForegroundService.ACTION_RESUME
        }
        getApplication<Application>().startService(intent)
    }

    fun stopStopwatch() {
        val intent = Intent(getApplication(), StopwatchForegroundService::class.java).apply {
            action = StopwatchForegroundService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun approvePermission(reel: ReelEntity) {
        val query = reel.permissionQuery ?: return
        val data = Data.Builder()
            .putString("query", query)
            .putString("reelId", reel.reelId)
            .build()
        val work = OneTimeWorkRequestBuilder<ConstrainedWebSearchWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(getApplication()).enqueue(work)
    }
}
