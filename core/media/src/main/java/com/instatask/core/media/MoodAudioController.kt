package com.instatask.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.instatask.core.model.MoodCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodAudioController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    fun play(mood: MoodCategory) {
        if (mood == MoodCategory.SILENT) {
            player?.pause()
            return
        }
        val resourceId = when (mood) {
            MoodCategory.MOTIVATIONAL -> tryResource("motiv_01")
            MoodCategory.PEACEFUL -> tryResource("peace_01")
            MoodCategory.ENERGETIC -> tryResource("energ_01")
            MoodCategory.SILENT -> null
        } ?: return
        val exo = player ?: ExoPlayer.Builder(context).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            player = it
        }
        exo.setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$resourceId"))
        exo.prepare()
        exo.play()
    }

    fun pause() = player?.pause()
    fun release() {
        player?.release()
        player = null
    }

    private fun tryResource(name: String): Int? {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return id.takeIf { it != 0 }
    }
}
