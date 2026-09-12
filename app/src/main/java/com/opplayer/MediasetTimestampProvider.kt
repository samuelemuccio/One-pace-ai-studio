package com.opplayer

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Provider dei timestamp per la versione Mediaset italiana di One Piece.
 *
 * Ordine di risoluzione (priorità decrescente):
 * 1. Override custom salvato dall'utente in SharedPreferences (auto-apprendimento)
 * 2. Override specifico per episodio nel JSON asset
 * 3. Default per saga nel JSON asset
 * 4. Fallback hardcoded (3m20s sigla + 4m30s recap) — comportamento attuale
 */
object MediasetTimestampProvider {

    private const val TAG = "MediasetTS"
    private const val ASSET_FILE = "mediaset_timestamps.json"

    data class EpisodeTimestamps(
        val recapEndMs: Long,
        val openingStartMs: Long,
        val openingEndMs: Long,
        val endingStartMs: Long
    ) {
        val hasOpening: Boolean get() = openingEndMs > openingStartMs && openingStartMs >= 0
        val hasEnding: Boolean get() = endingStartMs > 0
    }

    private var cachedJson: JSONObject? = null

    private fun loadAsset(context: Context): JSONObject? {
        cachedJson?.let { return it }
        return try {
            val json = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            cachedJson = obj
            obj
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile caricare $ASSET_FILE", e)
            null
        }
    }

    fun getForEpisode(context: Context, episodeNumber: Int): EpisodeTimestamps {
        val prefs = PlaybackPreferences(context)

        // 1. Override custom (auto-learning)
        val customOpeningEnd = prefs.getCustomTimestamp(episodeNumber, "opening_end")
        if (customOpeningEnd > 0L) {
            return EpisodeTimestamps(
                recapEndMs = prefs.getCustomTimestamp(episodeNumber, "recap_end").coerceAtLeast(0L),
                openingStartMs = prefs.getCustomTimestamp(episodeNumber, "opening_start").coerceAtLeast(0L),
                openingEndMs = customOpeningEnd,
                endingStartMs = prefs.getCustomTimestamp(episodeNumber, "ending_start")
            )
        }

        // 2-3. JSON asset
        val json = loadAsset(context)
        if (json != null) {
            try {
                val episodes = json.optJSONObject("episodes")
                val perEp = episodes?.optJSONObject(episodeNumber.toString())
                if (perEp != null) {
                    return EpisodeTimestamps(
                        recapEndMs = perEp.optLong("recap_end_ms", 0L),
                        openingStartMs = perEp.optLong("opening_start_ms", 0L),
                        openingEndMs = perEp.optLong("opening_end_ms", 150_000L),
                        endingStartMs = perEp.optLong("ending_start_ms", -1L)
                    )
                }

                val saga = OnePieceHelper.getSagaForEpisode(episodeNumber)
                val defaults = json.optJSONObject("saga_defaults")?.optJSONObject(saga.name)
                if (defaults != null) {
                    return EpisodeTimestamps(
                        recapEndMs = defaults.optLong("recap_end_ms", 0L),
                        openingStartMs = defaults.optLong("opening_start_ms", 0L),
                        openingEndMs = defaults.optLong("opening_end_ms", 150_000L),
                        endingStartMs = defaults.optLong("ending_start_ms", -1L)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore parsing JSON timestamp", e)
            }
        }

        // 4. Fallback (sigla 2m30s = 150.000 ms)
        return EpisodeTimestamps(
            recapEndMs = 0L,
            openingStartMs = 0L,
            openingEndMs = 150_000L,
            endingStartMs = -1L
        )
    }
}
