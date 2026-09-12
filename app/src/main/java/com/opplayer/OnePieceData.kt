package com.opplayer

import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class EpisodeType(val label: String, val hexColor: Long) {
    MANGA_CANON("Canon", 0xFF30D15B),       // Emerald Green Canon (#30D15B)
    ANIME_CANON("Anime Canon", 0xFF32ADE6), // Cyan Anime Canon
    MIXED("Mixed", 0xFFFF9F0A),             // Mixed Orange (#FF9F0A)
    FILLER("Filler", 0xFF8E8E93)            // Filler Grey (#8E8E93)
}

data class OnePieceSaga(
    val name: String,
    val range: IntRange,
    val description: String,
    val tagColor: Long = 0xFFE50914
)

data class PirateRank(
    val title: String,
    val minEpisodes: Int,
    val quote: String,
    val rankBadge: String
)

data class BackupData(
    val watchedEpisodes: Set<Int>,
    val favoriteEpisodes: Set<Int>,
    val lastEpisode: Int,
    val lastPositionMs: Long,
    val dailyStreak: Int,
    val lastWatchDate: String,
    val bountyBeli: Long
)

object OnePieceHelper {

    const val TOTAL_AIRING_EPISODES = 1176

    val SAGAS = listOf(
        OnePieceSaga("East Blue Saga", 1..61, "Romance Dawn, Orange Town, Syrup Village, Baratie, Arlong Park, Loguetown", 0xFF34C759),
        OnePieceSaga("Arabasta Saga", 62..135, "Reverse Mountain, Whiskey Peak, Little Garden, Drum Island, Arabasta", 0xFFFF9F0A),
        OnePieceSaga("Sky Island Saga", 136..206, "Jaya, Skypiea, G-8 Arc", 0xFF32ADE6),
        OnePieceSaga("Water 7 Saga", 207..325, "Long Ring Long Land, Water 7, Enies Lobby, Post-Enies Lobby", 0xFFFF3B30),
        OnePieceSaga("Thriller Bark Saga", 326..384, "Thriller Bark, Spa Island", 0xFFAF52DE),
        OnePieceSaga("Summit War: Sabaody & Amazon Lily", 385..421, "Sabaody Archipelago, Amazon Lily", 0xFFFF2D55),
        OnePieceSaga("Summit War: Impel Down", 422..456, "The great underwater prison breakout", 0xFFFF3838),
        OnePieceSaga("Summit War: Marineford", 457..489, "The Paramount War at Marineford", 0xFFE02424),
        OnePieceSaga("Summit War: Post-War", 490..516, "Luffy & Ace childhood flashback, 3D2Y code", 0xFFFF6482),
        OnePieceSaga("Fish-Man Island Saga", 517..574, "Return to Sabaody, Fish-Man Island", 0xFF00C7BE),
        OnePieceSaga("Dressrosa: Punk Hazard", 575..628, "Caesar Clown, Trafalgar Law alliance, Punk Hazard", 0xFFFF9500),
        OnePieceSaga("Dressrosa Saga", 629..746, "Corrida Colosseum, Doflamingo, Gear Fourth", 0xFFFF7B00),
        OnePieceSaga("Four Emperors: Zou", 747..782, "Zou Island, Mink Tribe, Road Poneglyph", 0xFF30D158),
        OnePieceSaga("Four Emperors: Whole Cake Island", 783..877, "Sanji rescue, Big Mom tea party, Katakuri duel", 0xFFFF2A42),
        OnePieceSaga("Levely Arc", 878..891, "World Conference of monarchs at Mariejois", 0xFFFFD700),
        OnePieceSaga("Wano Country Saga", 892..1085, "Oden flashback, Onigashima raid, Gear Fifth", 0xFFFFCC00),
        OnePieceSaga("Egghead Saga & Future", 1086..TOTAL_AIRING_EPISODES, "Future Island Egghead, Dr. Vegapunk, Final Saga", 0xFF5856D6)
    )

    private val fillerEpisodes = setOf(
        54, 55, 56, 57, 58, 59, 60,
        98, 99, 102,
        *(131..143).toList().toTypedArray(),
        *(196..206).toList().toTypedArray(),
        *(220..225).toList().toTypedArray(),
        *(279..283).toList().toTypedArray(),
        291, 292, 303,
        *(317..319).toList().toTypedArray(),
        *(326..336).toList().toTypedArray(),
        *(382..384).toList().toTypedArray(),
        406, 407,
        *(426..429).toList().toTypedArray(),
        457, 458, 492, 542,
        *(575..578).toList().toTypedArray(),
        590, 626, 627,
        *(747..750).toList().toTypedArray(),
        *(780..782).toList().toTypedArray(),
        895, 896, 907, 1029, 1030
    )

    private val mixedEpisodes = setOf(
        45, 46, 47, 61, 68, 69, 101, 226, 354, 421, 489, 520, 574,
        625, 628, 633, 653, 657, 679, 690, 731, 738, 751, 777, 778,
        789, 803, 807, 878, 879, 881, 882, 883, 884, 885, 887, 888,
        889, 890, 924, 988, 989, 991
    )

    private val animeCanonEpisodes = setOf(
        50, 51, 93,
        *(213..216).toList().toTypedArray(),
        *(418..420).toList().toTypedArray(),
        *(453..456).toList().toTypedArray(),
        *(497..499).toList().toTypedArray(),
        506, 737, 775, 1084
    )

    fun getEpisodeType(episodeNumber: Int): EpisodeType {
        return when {
            fillerEpisodes.contains(episodeNumber) -> EpisodeType.FILLER
            mixedEpisodes.contains(episodeNumber) -> EpisodeType.MIXED
            animeCanonEpisodes.contains(episodeNumber) -> EpisodeType.ANIME_CANON
            else -> EpisodeType.MANGA_CANON
        }
    }

    fun getSagaForEpisode(episodeNumber: Int): OnePieceSaga {
        return SAGAS.firstOrNull { episodeNumber in it.range } ?: SAGAS.first()
    }

    fun extractEpisodeNumber(url: String): Int {
        val regex = Regex("""pagine/(\d+)""")
        val match = regex.find(url)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    fun buildEpisodeUrl(currentActiveUrl: String, targetEpisode: Int): String {
        val regex = Regex("""pagine/(\d+)""")
        val match = regex.find(currentActiveUrl)
        return if (match != null) {
            val len = match.groupValues[1].length
            val formatted = targetEpisode.toString().padStart(len, '0')
            currentActiveUrl.replace(regex, "pagine/$formatted")
        } else {
            val formatted = targetEpisode.toString().padStart(3, '0')
            "https://onepiecepower.com/anime18/onepiece/ita3/pagine/$formatted"
        }
    }

    fun calculateStats(watchedCount: Int): Triple<Double, Int, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val startDate = sdf.parse("2026-03-01") ?: Date()
        val now = Date()
        val diffMs = (now.time - startDate.time).coerceAtLeast(0L)
        val daysElapsed = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(1L).toInt()

        val dailyAvg = watchedCount.toDouble() / daysElapsed
        val remainingEpisodes = (TOTAL_AIRING_EPISODES - watchedCount).coerceAtLeast(0)

        val estimatedDays = if (dailyAvg > 0.05) (remainingEpisodes / dailyAvg).toInt() else 365
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, estimatedDays)

        val outFormat = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
        return Triple(dailyAvg, remainingEpisodes, outFormat.format(cal.time))
    }

    fun calculateBounty(watchedCount: Int): Pair<Long, String> {
        val ratio = (watchedCount.toFloat() / TOTAL_AIRING_EPISODES).coerceIn(0f, 1f)
        val bounty = when {
            watchedCount < 61 -> (ratio * 30_000_000L).toLong()
            watchedCount < 135 -> 30_000_000L + ((watchedCount - 61).toDouble() / (135 - 61) * 70_000_000L).toLong()
            watchedCount < 325 -> 100_000_000L + ((watchedCount - 135).toDouble() / (325 - 135) * 200_000_000L).toLong()
            watchedCount < 516 -> 300_000_000L + ((watchedCount - 325).toDouble() / (516 - 325) * 100_000_000L).toLong()
            watchedCount < 746 -> 400_000_000L + ((watchedCount - 516).toDouble() / (746 - 516) * 100_000_000L).toLong()
            watchedCount < 891 -> 500_000_000L + ((watchedCount - 746).toDouble() / (891 - 746) * 1_000_000_000L).toLong()
            watchedCount < 1085 -> 1_500_000_000L + ((watchedCount - 891).toDouble() / (1085 - 891) * 1_500_000_000L).toLong()
            else -> 3_000_000_000L + ((watchedCount - 1085).toDouble() / (TOTAL_AIRING_EPISODES - 1085) * 500_000_000L).toLong()
        }

        val symbols = DecimalFormatSymbols(Locale.ITALY)
        val df = DecimalFormat("#,###", symbols)
        return Pair(bounty, "฿ ${df.format(bounty)}")
    }

    fun formatBountyShort(bounty: Long): String {
        return when {
            bounty >= 1_000_000_000L -> String.format(Locale.US, "฿ %.2fB", bounty / 1_000_000_000.0)
            bounty >= 1_000_000L -> String.format(Locale.US, "฿ %.1fM", bounty / 1_000_000.0)
            else -> "฿ $bounty"
        }
    }

    fun formatBeli(bounty: Long): String = formatBountyShort(bounty)

    fun getPirateRank(watchedCount: Int): PirateRank {
        return when {
            watchedCount < 61 -> PirateRank("Mozzo dell'East Blue", 0, "Il mare chiama un nuovo avventuriero", "⚓")
            watchedCount < 135 -> PirateRank("Pirata della Rotta Maggiore", 61, "La Baroque Works non fa più paura", "⚔️")
            watchedCount < 207 -> PirateRank("Navigatore del Cielo", 135, "L'oro di Shandora risplende", "☁️")
            watchedCount < 326 -> PirateRank("Dichiaratore di Guerra alla Marina", 206, "Bruciate quella bandiera!", "🔥")
            watchedCount < 517 -> PirateRank("Supernova di Sabaody", 325, "La peggiore delle generazioni", "⭐")
            watchedCount < 747 -> PirateRank("Guerriero del Nuovo Mondo", 516, "La gabbia per uccelli è spezzata", "⚡")
            watchedCount < 892 -> PirateRank("Quinto Imperatore dei Mari", 746, "Nessuno può fermare la fuga da Big Mom", "👑")
            watchedCount < 1086 -> PirateRank("Guerriero della Liberazione (Gear 5)", 891, "Il suono dei tamburi riecheggia", "🥁")
            else -> PirateRank("Re dei Pirati Incoronato", 1086, "Hai conquistato l'intera Rotta Maggiore!", "🏆")
        }
    }

    fun exportToJson(
        watched: Set<Int>,
        favorites: Set<Int>,
        lastEp: Int,
        lastPos: Long,
        streak: Int = 8,
        lastWatchDate: String = "",
        bountyBeli: Long = 0L
    ): String {
        val json = JSONObject()
        json.put("version", 2)
        json.put("lastEpisode", lastEp)
        json.put("lastPositionMs", lastPos)
        json.put("dailyStreak", streak)
        json.put("lastWatchDate", lastWatchDate)
        json.put("bountyBeli", bountyBeli)

        val watchedArray = JSONArray()
        watched.forEach { watchedArray.put(it) }
        json.put("watchedEpisodes", watchedArray)

        val favArray = JSONArray()
        favorites.forEach { favArray.put(it) }
        json.put("favoriteEpisodes", favArray)

        return json.toString(2)
    }

    fun importFromJson(jsonString: String): BackupData? {
        return try {
            val json = JSONObject(jsonString)
            val lastEp = json.optInt("lastEpisode", 539)
            val lastPos = json.optLong("lastPositionMs", 0L)
            val streak = json.optInt("dailyStreak", 8)
            val lastWatchDate = json.optString("lastWatchDate", "")
            val bountyBeli = json.optLong("bountyBeli", 0L)

            val watchedArray = json.optJSONArray("watchedEpisodes") ?: JSONArray()
            val watchedSet = mutableSetOf<Int>()
            for (i in 0 until watchedArray.length()) {
                watchedSet.add(watchedArray.getInt(i))
            }

            val favArray = json.optJSONArray("favoriteEpisodes") ?: JSONArray()
            val favSet = mutableSetOf<Int>()
            for (i in 0 until favArray.length()) {
                favSet.add(favArray.getInt(i))
            }

            BackupData(
                watchedEpisodes = watchedSet,
                favoriteEpisodes = favSet,
                lastEpisode = lastEp,
                lastPositionMs = lastPos,
                dailyStreak = streak,
                lastWatchDate = lastWatchDate,
                bountyBeli = bountyBeli
            )
        } catch (e: Exception) {
            null
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatBeli(amount: Long): String {
    return OnePieceHelper.formatBountyShort(amount)
}
