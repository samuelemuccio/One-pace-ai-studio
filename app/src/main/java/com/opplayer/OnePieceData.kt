package com.opplayer

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class EpisodeType(val label: String, val hexColor: Long) {
    MANGA_CANON("CANON", 0xFF34C759),       // Verde iOS
    ANIME_CANON("ANIME CANON", 0xFF32ADE6), // Ciano iOS
    MIXED("MISTO", 0xFFFF9F0A),             // Arancio iOS
    FILLER("FILLER", 0xFFFF453A)            // Rosso iOS
}

data class OnePieceSaga(
    val name: String,
    val range: IntRange,
    val description: String
)

object OnePieceHelper {

    const val TOTAL_AIRING_EPISODES = 1176

    val SAGAS = listOf(
        OnePieceSaga("East Blue", 1..61, "Le origini della ciurma di Cappello di Paglia"),
        OnePieceSaga("Alabasta", 62..135, "L'ingresso nella Rotta Maggiore e la Baroque Works"),
        OnePieceSaga("Skypiea", 136..206, "L'isola nel cielo e il dio Ener"),
        OnePieceSaga("Water 7 & Enies Lobby", 207..325, "La guerra contro il Governo Mondiale e la CP9"),
        OnePieceSaga("Thriller Bark", 326..384, "Gekko Moria, Brook e Nightmare Rufy"),
        OnePieceSaga("Guerra ai Vertici", 385..516, "Arcipelago Sabaody, Impel Down e Marineford"),
        OnePieceSaga("Isola degli Uomini Pesce", 517..574, "Il raduno dopo 2 anni e l'abisso marino"),
        OnePieceSaga("Dressrosa & Punk Hazard", 575..746, "L'alleanza pirata e la caduta di Doflamingo"),
        OnePieceSaga("Zou & Whole Cake Island", 747..891, "La saga dei Quattro Imperatori e Sanji"),
        OnePieceSaga("Paese di Wano", 892..1085, "I foderi rossi, Kaido e il risveglio del Gear 5"),
        OnePieceSaga("Egghead & Elbaf", 1086..TOTAL_AIRING_EPISODES, "L'isola del futuro e la Saga Finale")
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

        val estimatedDays = if (dailyAvg > 0) (remainingEpisodes / dailyAvg).toInt() else 0
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, estimatedDays)

        val outFormat = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
        return Triple(dailyAvg, remainingEpisodes, outFormat.format(cal.time))
    }

    fun exportToJson(watched: Set<Int>, lastEp: Int, lastPos: Long): String {
        val json = JSONObject()
        json.put("lastEpisode", lastEp)
        json.put("lastPositionMs", lastPos)
        val array = JSONArray()
        watched.forEach { array.put(it) }
        json.put("watchedEpisodes", array)
        return json.toString()
    }

    fun importFromJson(jsonString: String): Triple<Set<Int>, Int, Long>? {
        return try {
            val json = JSONObject(jsonString)
            val lastEp = json.optInt("lastEpisode", 1)
            val lastPos = json.optLong("lastPositionMs", 0L)
            val array = json.optJSONArray("watchedEpisodes") ?: JSONArray()
            val set = mutableSetOf<Int>()
            for (i in 0 until array.length()) {
                set.add(array.getInt(i))
            }
            Triple(set, lastEp, lastPos)
        } catch (e: Exception) {
            null
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
