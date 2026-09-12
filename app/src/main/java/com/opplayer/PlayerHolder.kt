package com.opplayer

import androidx.media3.exoplayer.ExoPlayer

/**
 * Singleton che tiene un riferimento all'istanza ExoPlayer attiva.
 * Serve per:
 *  - condividere il player tra Activity e Compose (PiP)
 *  - permettere al BroadcastReceiver del PiP di controllare la riproduzione
 */
object PlayerHolder {
    @Volatile var player: ExoPlayer? = null
    @Volatile var currentVideoUrl: String? = null
    @Volatile var currentEpisode: Int = 0
    @Volatile var currentPositionProvider: (() -> Long)? = null

    fun safeRelease() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        currentVideoUrl = null
        currentPositionProvider = null
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skip(ms: Long) {
        player?.let {
            val target = (it.currentPosition + ms).coerceAtLeast(0L)
            it.seekTo(target)
        }
    }
}
