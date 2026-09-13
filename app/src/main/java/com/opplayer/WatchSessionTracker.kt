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
 * Tracker wall-clock del tempo di visione.
 *
 * PRINCIPIO CHIAVE: misura il TEMPO REALE (wall clock) in cui il video è in play.
 * NON misura i minuti di contenuto video consumati.
 * NON divide per la velocità di riproduzione.
 *
 * Esempio: video 24 min guardato in 2x con skip = 8 minuti wall clock → activeMs = 8 min.
 * Se in pausa 3 min → non contati.
 * Se app in background 10 min → non contati.
 */
class WatchSessionTracker(private val context: Context) {

    private val prefs = context.getSharedPreferences("op_watch_sessions", Context.MODE_PRIVATE)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)

    private val _activeSession = MutableStateFlow<WatchSession?>(null)
    val activeSession: StateFlow<WatchSession?> = _activeSession

    // === COSTANTI DI BUSINESS ===
    companion object {
        const val MIN_SESSION_MS       = 60_000L        // 1 min — sotto questo la sessione è scartata
        const val MIN_WATCH_FOR_SEEN_MS = 4 * 60_000L   // 4 min reali per marcare "visto"
        const val SEEN_POSITION_MS      = 22 * 60_000L  // 22:00 posizione minima per "visto"
    }

    // === CICLO DI VITA DELLA SESSIONE ===

    fun startEpisode(episode: Int) {
        // Se c'era una sessione aperta, chiudila
        val previous = _activeSession.value
        if (previous != null && previous.episode != episode) {
            endEpisode(markCompleted = false)
        }
        val now = System.currentTimeMillis()
        _activeSession.value = WatchSession(
            episode = episode,
            startedAtMs = now,
            lastResumedAtMs = now,
            activeMs = 0L,
            sessionIndex = getNextSessionIndex(episode)
        )

        if (prefs.getLong("first_watch_ms", 0L) == 0L) {
            prefs.edit().putLong("first_watch_ms", OnePieceHelper.getStartDateMs()).apply()
        }
    }

    /** Da chiamare quando il video va in play */
    fun onVideoResumed() {
        _activeSession.value?.let {
            if (it.lastResumedAtMs == 0L) it.lastResumedAtMs = System.currentTimeMillis()
        }
    }

    /** Da chiamare quando il video va in pausa */
    fun onVideoPaused() {
        _activeSession.value?.let {
            val now = System.currentTimeMillis()
            if (it.lastResumedAtMs > 0L) {
                it.activeMs += (now - it.lastResumedAtMs).coerceAtLeast(0L)
                it.lastResumedAtMs = 0L
                it.pauseCount += 1
            }
        }
    }

    fun onResume() = onVideoResumed()
    fun onPause() = onVideoPaused()

    /** Da chiamare quando si chiude il player */
    fun endEpisode(markCompleted: Boolean, force: Boolean = false) {
        val session = _activeSession.value ?: return
        if (session.lastResumedAtMs > 0L) {
            session.activeMs += (System.currentTimeMillis() - session.lastResumedAtMs).coerceAtLeast(0L)
            session.lastResumedAtMs = 0L
        }
        session.completed = markCompleted

        if (session.activeMs >= MIN_SESSION_MS) {
            persistSession(session)
        }
        _activeSession.value = null
    }

    // === LOGICA "VISTO" INTELLIGENTE ===

    /**
     * Ritorna true se l'episodio può essere considerato "visto".
     * Condizioni: posizione video ≥ 22:00 AND wall-clock ≥ 4 minuti.
     */
    fun shouldMarkAsWatched(playerPositionMs: Long): Boolean {
        val session = _activeSession.value ?: return false
        return playerPositionMs >= SEEN_POSITION_MS &&
               session.activeMs >= MIN_WATCH_FOR_SEEN_MS
    }

    // === PERSISTENZA ===

    private fun persistSession(s: WatchSession) {
        val arr = getAllSessions()
        val obj = JSONObject().apply {
            put("ep", s.episode)
            put("start", s.startedAtMs)
            put("activeMs", s.activeMs)
            put("pauses", s.pauseCount)
            put("completed", s.completed)
            put("sessionIndex", s.sessionIndex)
        }
        arr.put(obj)
        prefs.edit().putString("sessions", arr.toString()).apply()
    }

    private fun getNextSessionIndex(episode: Int): Int {
        val arr = getAllSessions()
        var count = 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optInt("ep", -1) == episode) count++
        }
        return count
    }

    private fun getAllSessions(): JSONArray {
        return try { JSONArray(prefs.getString("sessions", "[]")) }
        catch (_: Exception) { JSONArray() }
    }

    // === STATISTICHE ===

    fun getTotalWatchTimeMs(): Long {
        val arr = getAllSessions()
        var sum = 0L
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ms = o.optLong("activeMs", 0L)
            if (ms >= MIN_SESSION_MS) sum += ms
        }
        return sum
    }

    fun getFirstWatchMs(): Long = prefs.getLong("first_watch_ms", 0L)
    fun setFirstWatchMs(ms: Long) { prefs.edit().putLong("first_watch_ms", ms).apply() }

    fun getDaysSinceFirstWatch(): Int {
        val first = getFirstWatchMs().let { if (it > 0L) it else OnePieceHelper.getStartDateMs() }
        val diff = (System.currentTimeMillis() - first).coerceAtLeast(86_400_000L)
        return (diff / 86_400_000L).toInt().coerceAtLeast(1)
    }

    /**
     * Media wall-clock delle sessioni dove l'episodio è stato visto (completed=true).
     * Solo PRIMA visione per episodio (esclude rewatch).
     */
    fun getAverageFirstWatchTimeMs(): Long {
        val arr = getAllSessions()
        if (arr.length() == 0) return 0L

        // FIX: prima cerchiamo sessioni "completed" (visto confermato).
        // Se non ce ne sono abbastanza (es. appena installato), allarghiamo
        // il criterio alle prime visioni con almeno MIN_SESSION_MS attivi.
        val strict = mutableMapOf<Int, Long>()
        val loose  = mutableMapOf<Int, Long>()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ep = o.optInt("ep", -1)
            val idx = o.optInt("sessionIndex", 0)
            val completed = o.optBoolean("completed", false)
            val activeMs = o.optLong("activeMs", 0L)
            if (ep < 0 || idx != 0) continue
            if (activeMs < MIN_SESSION_MS) continue

            loose[ep] = activeMs
            if (completed) strict[ep] = activeMs
        }

        val source = if (strict.size >= 3) strict else loose
        if (source.isEmpty()) return 0L
        return source.values.sum() / source.size
    }

    fun getAverageActiveMsPerEpisode(): Long = getAverageFirstWatchTimeMs()

    /** Media pause per episodio (solo prime visioni completate) */
    fun getAveragePausesPerEpisode(): Float {
        val arr = getAllSessions()
        if (arr.length() == 0) return 0f
        var total = 0
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optInt("sessionIndex", 0) != 0) continue
            if (!o.optBoolean("completed", false)) continue
            total += o.optInt("pauses", 0)
            count++
        }
        return if (count > 0) total.toFloat() / count else 0f
    }

    /** Numero totale di sessioni valide (≥ 1 min) */
    fun getTotalValidSessionsCount(): Int {
        val arr = getAllSessions()
        var count = 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optLong("activeMs", 0L) >= MIN_SESSION_MS) count++
        }
        return count
    }

    fun getTotalSessionsCount(): Int = getTotalValidSessionsCount()

    fun getEpisodesPerDay(watchedCount: Int): Float {
        val days = getDaysSinceFirstWatch()
        return if (days > 0) watchedCount.toFloat() / days else 0f
    }

    /** Numero di episodi diversi effettivamente tracciati */
    fun getTrackedUniqueEpisodes(): Int {
        val arr = getAllSessions()
        val set = mutableSetOf<Int>()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optLong("activeMs", 0L) >= MIN_SESSION_MS) {
                set.add(arr.getJSONObject(i).optInt("ep", -1))
            }
        }
        set.remove(-1)
        return set.size
    }

    /** Proiezione di fine serie in base a ore giornaliere */
    fun projectCompletion(
        remainingEpisodes: Int,
        avgMsPerEpisode: Long,
        hoursPerDay: Double
    ): ProjectionResult {
        if (avgMsPerEpisode <= 0L || remainingEpisodes <= 0 || hoursPerDay <= 0.0) {
            return ProjectionResult(hoursPerDay, Int.MAX_VALUE, "", false)
        }
        val totalMs = remainingEpisodes.toLong() * avgMsPerEpisode
        val totalHours = totalMs / 3_600_000.0
        val daysRemaining = kotlin.math.ceil(totalHours / hoursPerDay).toInt()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysRemaining)
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
        return ProjectionResult(hoursPerDay, daysRemaining, fmt.format(cal.time), true)
    }

    fun projectCompletion(
        remainingEpisodes: Int,
        episodesPerDay: Double
    ): ProjectionResult {
        val avgMs = getAverageFirstWatchTimeMs().let { if (it > 0) it else 15 * 60_000L }
        val hoursPerDay = (episodesPerDay * avgMs) / 3_600_000.0
        return projectCompletion(remainingEpisodes, avgMs, hoursPerDay)
    }

    /** Proiezioni multiple */
    fun getAllProjections(remainingEpisodes: Int, avgMsPerEpisode: Long): List<ProjectionResult> {
        return listOf(0.5, 1.0, 2.0, 3.0, 5.0).map {
            projectCompletion(remainingEpisodes, avgMsPerEpisode, it)
        }
    }

    fun getAllProjections(remainingEpisodes: Int, currentRate: Double): List<ProjectionResult> {
        return getAllProjections(remainingEpisodes, getAverageFirstWatchTimeMs())
    }

    fun clearAll() { prefs.edit().clear().apply() }

    /** Imposta data inizio viaggio retroattivamente */
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
    var completed: Boolean = false,
    val sessionIndex: Int = 0
)

data class ProjectionResult(
    val hoursPerDay: Double,
    val daysRemaining: Int,
    val estimatedDate: String,
    val isReachable: Boolean,
    val episodesPerDay: Double = hoursPerDay
)

/** Formatta durata ms in "Xh Ym" o "Ym Zs" */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
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

/** Formatta durata compatta "Xm" */
fun formatDurationShort(ms: Long): String {
    if (ms <= 0) return "—"
    val min = ms / 60_000
    return if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m"
}
