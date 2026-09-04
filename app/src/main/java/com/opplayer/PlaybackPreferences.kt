package com.opplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PlaybackPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("op_player_prefs", Context.MODE_PRIVATE)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "op_streak_channel"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    fun saveLastPlayback(url: String, episodeNumber: Int, positionMs: Long) {
        prefs.edit()
            .putString("last_url", url)
            .putInt("last_episode", episodeNumber)
            .putLong("last_position_ms", positionMs)
            .apply()
        recordWatchForStreak()
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
        if (watched) {
            current.add(episode)
            recordWatchForStreak()
        } else {
            current.remove(episode)
        }
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

    // --- GAMIFICATION: STREAK & NOTIFICATIONS ---

    fun getStreak(): Int = prefs.getInt("daily_streak", 3)

    fun getLastWatchDate(): String = prefs.getString("last_watch_date", "") ?: ""

    fun recordWatchForStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val todayStr = sdf.format(Date())
        val lastDateStr = getLastWatchDate()

        var currentStreak = getStreak()
        if (lastDateStr == todayStr) {
            // Already recorded today, streak maintained
            return currentStreak
        }

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        currentStreak = if (lastDateStr == yesterdayStr) {
            currentStreak + 1
        } else {
            1
        }

        prefs.edit()
            .putInt("daily_streak", currentStreak)
            .putString("last_watch_date", todayStr)
            .apply()

        notifyStreakMilestone(currentStreak)
        return currentStreak
    }

    fun saveDailyStreak(streak: Int, lastWatchDate: String) {
        prefs.edit()
            .putInt("daily_streak", streak)
            .putString("last_watch_date", lastWatchDate)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rotta & Streak Pirata"
            val descriptionText = "Notifiche di avanzamento e serie quotidiana di episodi"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyStreakMilestone(streakDays: Int) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Serie Pirata Attiva! 🔥")
                .setContentText("Hai raggiunto $streakDays giorni consecutivi di visione! Continua la rotta.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Permission or notification disabled gracefully ignored
        }
    }
}
