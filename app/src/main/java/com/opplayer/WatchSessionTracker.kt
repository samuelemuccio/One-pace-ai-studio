package com.opplayer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Traccia il tempo di visione attivo per ogni episodio.
 * - Pausa automaticamente quando l'app va in background
 * - Riprende quando l'utente rientra
 * - Salva metriche per episodio: tempo attivo, pause, completato
 * - Fornisce medie e proiezioni
 */
class WatchSessionTracker(private val context: Context) {

    private val prefs = context.getSharedPreferences("op_watch_sessions", Context.MODE_PRIVATE)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)

    private val _activeSession = MutableStateFlow<WatchSession?>(null)
    val activeSession: StateFlow<WatchSession?> = _activeSession

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    // === CICLO DI VITA DELLA SESSIONE ===

    /** Avvia una nuova sessione per l'episodio specificato */
    fun startEpisode(episode: Int) {
        endEpisode(markCompleted = false, force = true)
        val now = System.currentTimeMillis()
        _activeSession.value = WatchSession(
            episode = episode,
            startedAtMs = now,
            lastResumedAtMs = now,
            activeMs = 0L
        )
        _isTracking.value = true

        // Prima sessione assoluta → registra la data di inizio viaggio
        if (prefs.getLong("first_watch_ms", 0L) == 0L) {
            prefs.edit().putLong("first_watch_ms", now).apply()
        }
    }

    /** App torna in foreground o video riprende */
    fun onResume() {
        _activeSession.value?.let {
            if (it.lastResumedAtMs == 0L) {
                it.lastResumedAtMs = System.currentTimeMillis()
            }
        }
    }

    /** App va in background o video va in pausa */
    fun onPause() {
        _activeSession.value?.let {
            val now = System.currentTimeMillis()
            if (it.lastResumedAtMs > 0L) {
                val delta = (now - it.lastResumedAtMs).coerceAtLeast(0L)
                it.activeMs += delta
                it.lastResumedAtMs = 0L
            }
        }
    }

    /** Chiude l'episodio e salva la sessione */
    fun endEpisode(markCompleted: Boolean, force: Boolean = false) {
        val session = _activeSession.value
        if (session == null && !force) return
        if (session == null) return

        // chiudi l'ultimo segmento attivo
        if (session.lastResumedAtMs > 0L) {
            val delta = (System.currentTimeMillis() - session.lastResumedAtMs).coerceAtLeast(0L)
            session.activeMs += delta
        }
        session.completed = markCompleted

        // Ignora sessioni < 30s (probabilmente tap per sbaglio)
        if (session.activeMs >= 30_000L) {
            persistSession(session)
        }

        _activeSession.value = null
        _isTracking.value = false
    }

    /** Da chiamare quando si mette in pausa il video */
    fun onVideoPaused() = onPause()

    /** Da chiamare quando si riprende il video */
    fun onVideoResumed() = onResume()

    private fun persistSession(s: WatchSession) {
        val arr = try {
            JSONArray(prefs.getString("sessions", "[]"))
        } catch (_: Exception) { JSONArray() }

        val obj = JSONObject().apply {
            put("ep", s.episode)
            put("start", s.startedAtMs)
            put("activeMs", s.activeMs)
            put("pauses", s.pauseCount)
            put("completed", s.completed)
        }
        arr.put(obj)
        prefs.edit().putString("sessions", arr.toString()).apply()
    }

    // === STATISTICHE ===

    fun getFirstWatchMs(): Long = prefs.getLong("first_watch_ms", 0L)

    fun setFirstWatchMs(ms: Long) {
        prefs.edit().putLong("first_watch_ms", ms).apply()
    }

    /** Media ms di visione ATTIVA per episodio (esclude pause in background) */
    fun getAverageActiveMsPerEpisode(): Long {
        val arr = getAllSessions()
        if (arr.length() == 0) return 0L
        var total = 0L
        for (i in 0 until arr.length()) total += arr.getJSONObject(i).optLong("activeMs", 0L)
        return total / arr.length()
    }

    /** Media pause per episodio */
    fun getAveragePausesPerEpisode(): Float {
        val arr = getAllSessions()
        if (arr.length() == 0) return 0f
        var total = 0
        for (i in 0 until arr.length()) total += arr.getJSONObject(i).optInt("pauses", 0)
        return total.toFloat() / arr.length()
    }

    /** Numero totale di sessioni registrate (post-install di questa versione) */
    fun getTotalSessionsCount(): Int = getAllSessions().length()

    /** Giorni trascorsi dalla prima visione */
    fun getDaysSinceFirstWatch(): Int {
        val first = getFirstWatchMs()
        if (first == 0L) return 0
        val diff = System.currentTimeMillis() - first
        return (diff / 86_400_000L).toInt().coerceAtLeast(1)
    }

    /** Media episodi/giorno dal primo avvio della sessione */
    fun getEpisodesPerDay(watchedCount: Int): Float {
        val days = getDaysSinceFirstWatch()
        if (days <= 0) return 0f
        return watchedCount.toFloat() / days
    }

    /** Proiezione: quanti giorni per finire al ritmo specificato */
    fun projectCompletion(
        remainingEpisodes: Int,
        episodesPerDay: Double
    ): ProjectionResult {
        if (episodesPerDay <= 0.0 || remainingEpisodes <= 0) {
            return ProjectionResult(
                episodesPerDay = episodesPerDay,
                daysRemaining = Int.MAX_VALUE,
                estimatedDate = "",
                isReachable = false
            )
        }
        val daysRemaining = kotlin.math.ceil(remainingEpisodes / episodesPerDay).toInt()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysRemaining)
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)

        return ProjectionResult(
            episodesPerDay = episodesPerDay,
            daysRemaining = daysRemaining,
            estimatedDate = fmt.format(cal.time),
            isReachable = true
        )
    }

    /** Proiezioni multiple per ritmi diversi (3, 5, 7, 10 al giorno + attuale) */
    fun getAllProjections(
        remainingEpisodes: Int,
        currentRate: Double
    ): List<ProjectionResult> {
        val rates = mutableListOf(currentRate, 3.0, 5.0, 7.0, 10.0)
            .filter { it > 0.0 }
            .distinct()
            .sorted()
        return rates.map { projectCompletion(remainingEpisodes, it) }
    }

    private fun getAllSessions(): JSONArray {
        return try {
            JSONArray(prefs.getString("sessions", "[]"))
        } catch (_: Exception) { JSONArray() }
    }

    /** Reset completo (per debug o cambio device) */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** IMPORTANTE: imposta la data di inizio viaggio retroattivamente */
    fun setCustomStartDate(daysAgo: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        setFirstWatchMs(cal.timeInMillis)
    }
}

data class WatchSession(
    val episode: Int,
    val startedAtMs: Long,
    var lastResumedAtMs: Long,
    var activeMs: Long,
    var pauseCount: Int = 0,
    var completed: Boolean = false
)

data class ProjectionResult(
    val episodesPerDay: Double,
    val daysRemaining: Int,
    val estimatedDate: String,
    val isReachable: Boolean
)

/** Formatta durata ms in "Xh Ym" o "Ym Zs" */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
