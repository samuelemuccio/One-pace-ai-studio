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
        const val CURRENT_DATA_VERSION = 4
    }

    init {
        createNotificationChannel()
        applyBackupMigrationIfNeeded()
        sanitizeLegacyUrlsIfNeeded()
    }

    private fun sanitizeLegacyUrlsIfNeeded() {
        val savedUrl = prefs.getString("last_url", null)
        if (savedUrl != null && (savedUrl.contains("onepiecepower.net") || !savedUrl.contains("onepiecepower.com"))) {
            val ep = getLastEpisode()
            val lang = getAudioLanguage()
            val cleanUrl = OnePieceHelper.buildEpisodeUrl("", ep, lang)
            prefs.edit().putString("last_url", cleanUrl).apply()
        }
    }

    private fun applyBackupMigrationIfNeeded() {
        val currentVersion = prefs.getInt("user_backup_version", 0)
        if (currentVersion < CURRENT_DATA_VERSION) {
            val currentWatched = prefs.getStringSet("watched_set", null)
                ?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
            // L'utente è arrivato all'episodio 542
            currentWatched.addAll(1..542)

            val currentLast = prefs.getInt("last_episode", 542)
            val newLast = if (currentLast <= 542) 543 else currentLast

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(cal.time)

            val existingStreak = prefs.getInt("daily_streak", 8).coerceAtLeast(8)
            val initialLang = getAudioLanguage()
            val cleanUrl = OnePieceHelper.buildEpisodeUrl("", newLast, initialLang)

            prefs.edit()
                .putInt("user_backup_version", CURRENT_DATA_VERSION)
                .putInt("last_episode", newLast)
                .putLong("last_position_ms", 0L)
                .putInt("daily_streak", existingStreak)
                .putString("last_watch_date", yesterdayStr)
                .putLong("bounty_beli", 0L)
                .putStringSet("watched_set", currentWatched.map { it.toString() }.toSet())
                .putString("last_url", cleanUrl)
                .apply()
        }
    }

    fun setLastEpisode(episodeNumber: Int) {
        prefs.edit().putInt("last_episode", episodeNumber).apply()
    }

    fun saveLastPlayback(url: String, episodeNumber: Int, positionMs: Long, language: AudioLanguage? = null) {
        val detectedLang = language ?: OnePieceHelper.detectLanguage(url)
        val cleanUrl = if (url.contains("onepiecepower.net") || (!url.contains("onepiecepower.com") && !url.startsWith("/"))) {
            OnePieceHelper.buildEpisodeUrl("", episodeNumber, detectedLang)
        } else {
            url
        }
        val editor = prefs.edit()
            .putString("last_url", cleanUrl)
            .putInt("last_episode", episodeNumber)
            .putString("audio_language", detectedLang.name)
            .putLong("ep_last_seen_$episodeNumber", System.currentTimeMillis())

        if (positionMs > 0L) {
            editor.putLong("last_position_ms", positionMs)
            editor.putLong("ep_pos_$episodeNumber", positionMs)
        }
        editor.apply()
        recordWatchForStreak()
    }

    fun getAudioLanguage(): AudioLanguage {
        val raw = prefs.getString("audio_language", AudioLanguage.ITA.name) ?: AudioLanguage.ITA.name
        return try {
            AudioLanguage.valueOf(raw)
        } catch (_: Exception) {
            AudioLanguage.ITA
        }
    }

    fun setAudioLanguage(lang: AudioLanguage) {
        prefs.edit().putString("audio_language", lang.name).apply()
    }

    fun saveEpisodePosition(episodeNumber: Int, positionMs: Long) {
        val editor = prefs.edit()
            .putInt("last_episode", episodeNumber)
            .putLong("ep_last_seen_$episodeNumber", System.currentTimeMillis())
        if (positionMs > 0L) {
            editor.putLong("last_position_ms", positionMs)
            editor.putLong("ep_pos_$episodeNumber", positionMs)
        }
        editor.apply()
    }

    fun getEpisodeWatchTimestamp(episodeNumber: Int): Long {
        return prefs.getLong("ep_last_seen_$episodeNumber", 0L)
    }

    fun getLastUrl(): String? {
        val raw = prefs.getString("last_url", null) ?: return null
        if (raw.contains("onepiecepower.net") || !raw.contains("onepiecepower.com")) {
            val ep = getLastEpisode()
            val lang = getAudioLanguage()
            val cleanUrl = OnePieceHelper.buildEpisodeUrl("", ep, lang)
            prefs.edit().putString("last_url", cleanUrl).apply()
            return cleanUrl
        }
        return raw
    }
    fun getLastEpisode(): Int = prefs.getInt("last_episode", 543)
    fun getLastPositionMs(): Long = prefs.getLong("last_position_ms", 0L)
    fun getEpisodePositionMs(episodeNumber: Int): Long = prefs.getLong("ep_pos_$episodeNumber", 0L)

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

    fun toggleWatched(episode: Int): Boolean {
        val isWatched = isEpisodeWatched(episode)
        markEpisodeWatched(episode, !isWatched)
        return !isWatched
    }

    fun markWatched(episode: Int) {
        markEpisodeWatched(episode, true)
    }

    fun unmarkWatched(episode: Int) {
        markEpisodeWatched(episode, false)
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
            val initSet = (1..542).toSet()
            prefs.edit().putStringSet("watched_set", initSet.map { it.toString() }.toSet()).apply()
            initSet
        } else {
            raw.mapNotNull { it.toIntOrNull() }.toSet()
        }
    }

    // --- GAMIFICATION: STREAK & NOTIFICATIONS ---

    fun getStreak(): Int = prefs.getInt("daily_streak", 8)

    fun getLastWatchDate(): String = prefs.getString("last_watch_date", "") ?: ""

    fun recordWatchForStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val todayStr = sdf.format(Date())
        val lastDateStr = getLastWatchDate()

        val currentStreak = getStreak().coerceAtLeast(8)
        if (lastDateStr == todayStr) {
            // Già registrato oggi, la streak è confermata
            return currentStreak
        }

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        // Se era ieri, se è il primo giorno, o se l'utente ha la sua serie pirata attiva (>= 8),
        // incrementiamo con successo senza mai azzerare la streak a 1!
        val newStreak = if (lastDateStr == yesterdayStr || lastDateStr.isEmpty() || currentStreak >= 8) {
            currentStreak + 1
        } else {
            1
        }

        prefs.edit()
            .putInt("daily_streak", newStreak)
            .putString("last_watch_date", todayStr)
            .apply()

        notifyStreakMilestone(newStreak)
        return newStreak
    }

    fun saveDailyStreak(streak: Int, lastWatchDate: String) {
        prefs.edit()
            .putInt("daily_streak", streak)
            .putString("last_watch_date", lastWatchDate)
            .apply()
    }

    // === SOGLIA EPISODIO VISTO (default 22:30) ===
    fun getWatchedThresholdMs(): Long =
        prefs.getLong("watched_threshold_ms", 22L * 60_000L + 30_000L)

    fun setWatchedThresholdMs(ms: Long) {
        prefs.edit().putLong("watched_threshold_ms", ms).apply()
    }

    // === BOOST VELOCITÀ (long-press metà destra) ===
    fun getBoostSpeed(): Float = prefs.getFloat("boost_speed", 2.0f)

    fun setBoostSpeed(speed: Float) {
        prefs.edit().putFloat("boost_speed", speed.coerceIn(1.25f, 4.0f)).apply()
    }

    fun isBoostEnabled(): Boolean = prefs.getBoolean("boost_enabled", true)
    fun setBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("boost_enabled", enabled).apply()
    }

    // === AUTO-LEARNING TIMESTAMP MEDIASET ===
    fun saveCustomTimestamp(episode: Int, key: String, valueMs: Long) {
        prefs.edit().putLong("ts_${episode}_$key", valueMs).apply()
    }

    fun getCustomTimestamp(episode: Int, key: String): Long =
        prefs.getLong("ts_${episode}_$key", -1L)

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

    fun isStreakReminderEnabled(): Boolean = prefs.getBoolean("streak_reminder_enabled", true)
    fun setStreakReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("streak_reminder_enabled", enabled).apply()
    }

    fun getPreferredDownloadQuality(): String = prefs.getString("download_quality", "720p") ?: "720p"
    fun setPreferredDownloadQuality(quality: String) {
        prefs.edit().putString("download_quality", quality).apply()
    }

    fun isAutoSkipIntroEnabled(): Boolean = prefs.getBoolean("auto_skip_intro", true)
    fun setAutoSkipIntroEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_skip_intro", enabled).apply()
    }

    fun sendStreakWarningNotification(currentEp: Int, streakDays: Int) {
        if (!isStreakReminderEnabled()) return
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
                .setContentTitle("⚠️ La tua serie pirata sta per scadere!")
                .setContentText("Hai una streak di $streakDays giorni! Guarda l'Episodio $currentEp prima di mezzanotte 🔥")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID + 1, builder.build())
        } catch (_: Exception) {
        }
    }

    fun sendTestNotification(streakDays: Int, currentEp: Int) {
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
                .setContentTitle("🔥 Promemoria Serie Pirata (Attivo!)")
                .setContentText("Streak attiva: $streakDays giorni consecutivi! Pronto per l'Episodio $currentEp 🏴‍☠️")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID + 2, builder.build())
        } catch (_: Exception) {
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
