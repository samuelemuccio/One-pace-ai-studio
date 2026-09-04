package com.opplayer

import android.content.Context
import android.content.SharedPreferences

class PlaybackPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("op_player_prefs", Context.MODE_PRIVATE)

    fun saveLastPlayback(url: String, episodeNumber: Int, positionMs: Long) {
        prefs.edit()
            .putString("last_url", url)
            .putInt("last_episode", episodeNumber)
            .putLong("last_position_ms", positionMs)
            .apply()
    }

    fun getLastUrl(): String? = prefs.getString("last_url", null)
    fun getLastEpisode(): Int = prefs.getInt("last_episode", 351)
    fun getLastPositionMs(): Long = prefs.getLong("last_position_ms", 0L)

    fun saveSpeed(speed: Float) {
        prefs.edit().putFloat("playback_speed", speed).apply()
    }
    fun getSpeed(): Float = prefs.getFloat("playback_speed", 1.0f)

    fun saveSkipStep(seconds: Int) {
        prefs.edit().putInt("skip_step", seconds).apply()
    }
    fun getSkipStep(): Int = prefs.getInt("skip_step", 5)

    fun markEpisodeWatched(episode: Int, watched: Boolean = true) {
        val current = getWatchedEpisodes().toMutableSet()
        if (watched) current.add(episode) else current.remove(episode)
        prefs.edit().putStringSet("watched_set", current.map { it.toString() }.toSet()).apply()
    }

    fun isEpisodeWatched(episode: Int): Boolean {
        return getWatchedEpisodes().contains(episode)
    }

    fun toggleFavorite(episode: Int): Boolean {
        val favs = getFavoriteEpisodes().toMutableSet()
        val newState = if (favs.contains(episode)) {
            favs.remove(episode)
            false
        } else {
            favs.add(episode)
            true
        }
        prefs.edit().putStringSet("favorite_set", favs.map { it.toString() }.toSet()).apply()
        return newState
    }

    fun isFavorite(episode: Int): Boolean {
        return getFavoriteEpisodes().contains(episode)
    }

    fun getFavoriteEpisodes(): Set<Int> {
        val raw = prefs.getStringSet("favorite_set", null) ?: return emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun getWatchedEpisodes(): Set<Int> {
        val raw = prefs.getStringSet("watched_set", null)
        return if (raw == null) {
            val initSet = (1..351).toSet()
            prefs.edit().putStringSet("watched_set", initSet.map { it.toString() }.toSet()).apply()
            initSet
        } else {
            raw.mapNotNull { it.toIntOrNull() }.toSet()
        }
    }
}
