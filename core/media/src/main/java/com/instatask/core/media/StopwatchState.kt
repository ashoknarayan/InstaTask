package com.instatask.core.media

import com.instatask.core.model.ActiveStopwatchState
import com.instatask.core.model.StopwatchRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class StopwatchState @Inject constructor() {
    private val _state = MutableStateFlow(
        ActiveStopwatchState(null, "", StopwatchRunState.IDLE, 0L, 0L),
    )
    val state: StateFlow<ActiveStopwatchState> = _state.asStateFlow()
    private val _completedSession = MutableStateFlow<StopwatchCompletedEvent?>(null)
    val completedSession: StateFlow<StopwatchCompletedEvent?> = _completedSession.asStateFlow()

    fun start(taskId: String?, title: String, now: Long) {
        _state.value = ActiveStopwatchState(taskId, title, StopwatchRunState.RUNNING, 0L, now)
    }

    fun pause(now: Long) {
        val current = _state.value
        if (current.runState == StopwatchRunState.RUNNING) {
            val elapsed = current.computeCurrentElapsedMs(now)
            _state.value = current.copy(runState = StopwatchRunState.PAUSED, accumulatedMs = elapsed)
        }
    }

    fun resume(now: Long) {
        val current = _state.value
        if (current.runState == StopwatchRunState.PAUSED) {
            _state.value = current.copy(runState = StopwatchRunState.RUNNING, lastResumedRealtimeMs = now)
        }
    }

    fun stop(now: Long): ActiveStopwatchState {
        val completed = _state.value
        val elapsed = completed.computeCurrentElapsedMs(now)
        val result = completed.copy(runState = StopwatchRunState.IDLE, accumulatedMs = elapsed)
        _state.value = ActiveStopwatchState(null, "", StopwatchRunState.IDLE, 0L, 0L)
        return result
    }

    fun emitCompletedSession(taskId: String, elapsedMs: Long) {
        _completedSession.value = StopwatchCompletedEvent(
            sessionId = UUID.randomUUID().toString(),
            taskId = taskId,
            elapsedMs = elapsedMs,
        )
    }

    fun acknowledgeCompletedSession(sessionId: String) {
        if (_completedSession.value?.sessionId == sessionId) {
            _completedSession.value = null
        }
    }
}

data class StopwatchCompletedEvent(val sessionId: String, val taskId: String, val elapsedMs: Long)
