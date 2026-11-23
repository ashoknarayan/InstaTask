package com.instatask.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.instatask.core.database.InstaTaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StopwatchForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.instatask.action.STOPWATCH_START"
        const val ACTION_PAUSE = "com.instatask.action.STOPWATCH_PAUSE"
        const val ACTION_RESUME = "com.instatask.action.STOPWATCH_RESUME"
        const val ACTION_STOP = "com.instatask.action.STOPWATCH_STOP"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val EXTRA_COMPLETED_TASK_ID = "completed_task_id"
    }

    @Inject lateinit var stopwatchState: StopwatchState
    @Inject lateinit var dao: InstaTaskDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTaskTitle: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = SystemClock.elapsedRealtime()
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
                activeTaskTitle = title
                stopwatchState.start(taskId, title, now)
                startForeground(1001, notification(title, paused = false))
            }
            ACTION_PAUSE -> {
                stopwatchState.pause(now)
                updateNotification(paused = true)
            }
            ACTION_RESUME -> {
                stopwatchState.resume(now)
                updateNotification(paused = false)
            }
            ACTION_STOP -> {
                val completed = stopwatchState.stop(now)
                completed.taskId?.let { taskId ->
                    scope.launch { dao.addFocusedTime(taskId, completed.accumulatedMs) }
                    stopwatchState.emitCompletedSession(taskId, completed.accumulatedMs)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(paused: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(1001, notification(activeTaskTitle, paused))
    }

    private fun notification(title: String, paused: Boolean): Notification {
        val channelId = "instatask_focus"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Focus Stopwatch", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (paused) "Resume" else "Pause"
        val toggleIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Focusing: $title")
            .setContentText(if (paused) "Focus stopwatch is paused" else "InstaTask stopwatch is running")
            .setOngoing(true)
            .addAction(
                toggleIcon,
                toggleLabel,
                servicePendingIntent(toggleAction, if (paused) 1003 else 1002),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                servicePendingIntent(ACTION_STOP, 1004),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, StopwatchForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
