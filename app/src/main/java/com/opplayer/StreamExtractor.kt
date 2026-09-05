package com.opplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

object StreamExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    data class ExtractedSource(
        val url: String,
        val label: String = "",
        val resolution: Int = 0
    )

    suspend fun resolveStreamUrl(episodePageUrl: String, preferredQuality: String = "720p"): String? = withContext(Dispatchers.IO) {
        try {
            val pageReq = Request.Builder()
                .url(episodePageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://onepiecepower.com/")
                .build()

            val pageHtml = client.newCall(pageReq).execute().use { it.body?.string() } ?: return@withContext null
            val doc = Jsoup.parse(pageHtml, episodePageUrl)

            // 1. Check iframes with src or data-src
            var embedUrl = doc.select("iframe[src]").map { it.absUrl("src") }
                .firstOrNull { isKnownVideoHost(it) }

            if (embedUrl.isNullOrBlank()) {
                embedUrl = doc.select("iframe[data-src]").map { it.attr("data-src") }
                    .firstOrNull { isKnownVideoHost(it) }
            }

            // 2. Check links or scripts matching video host patterns
            if (embedUrl.isNullOrBlank()) {
                val hostRegex = Regex("""https?://[a-zA-Z0-9.-]*(?:supervideo|mixdrop|streamtape|dropload|wolfstream)[a-zA-Z0-9.-]*/[a-zA-Z0-9_/?&=.-]+""")
                embedUrl = hostRegex.find(pageHtml)?.value
            }

            // 3. Check for direct mp4 or m3u8 in page
            if (embedUrl.isNullOrBlank()) {
                val directMediaRegex = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
                val direct = directMediaRegex.find(pageHtml)?.groupValues?.get(1)
                if (!direct.isNullOrBlank()) return@withContext direct
                return@withContext null
            }

            resolveHost(embedUrl, preferredQuality)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isKnownVideoHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("supervideo") || lower.contains("mixdrop") ||
               lower.contains("streamtape") || lower.contains("dropload") || lower.contains("wolfstream")
    }

    private fun resolveHost(embedUrl: String, preferredQuality: String = "720p"): String? {
        val req = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", embedUrl)
            .build()

        val embedHtml = client.newCall(req).execute().use { it.body?.string() } ?: return null

        val packedBlock = findPackedBlock(embedHtml)
        val scriptToAnalyze = if (packedBlock != null) {
            unpackDeanEdwards(packedBlock) ?: embedHtml
        } else {
            embedHtml
        }

        val wants720 = preferredQuality.contains("720")
        val sources = mutableListOf<ExtractedSource>()

        // 1. Estrazione da blocco sources: [...]
        val sourcesBlockRegex = Regex("""sources\s*:\s*\[([\s\S]*?)\]""")
        val sourcesBlock = sourcesBlockRegex.find(scriptToAnalyze)?.groupValues?.get(1)
        if (sourcesBlock != null) {
            val itemRegex = Regex("""\{([^{}]+)\}""")
            itemRegex.findAll(sourcesBlock).forEach { match ->
                val itemStr = match.groupValues[1]
                val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+)["']""")
                val file = fileRegex.find(itemStr)?.groupValues?.get(1)
                if (!file.isNullOrBlank()) {
                    val labelRegex = Regex("""label\s*:\s*["']([^"']+)["']""")
                    val label = labelRegex.find(itemStr)?.groupValues?.get(1) ?: ""
                    val res = when {
                        label.contains("1080") || file.contains("1080") -> 1080
                        label.contains("720") || file.contains("720") -> 720
                        label.contains("480") || file.contains("480") -> 480
                        else -> 0
                    }
                    sources.add(ExtractedSource(file, label, res))
                }
            }
        }

        // 2. Se non sono state trovate fonti strutturate in sources, estrai tutte le occorrenze di file: "..."
        if (sources.isEmpty()) {
            val allFilesRegex = Regex("""["']?file["']?\s*:\s*["'](https?://[^"']+)["']""")
            allFilesRegex.findAll(scriptToAnalyze).forEach { match ->
                val file = match.groupValues[1]
                val start = (match.range.first - 80).coerceAtLeast(0)
                val end = (match.range.last + 80).coerceAtMost(scriptToAnalyze.length)
                val window = scriptToAnalyze.substring(start, end).lowercase()
                val res = when {
                    window.contains("1080") || file.contains("1080") -> 1080
                    window.contains("720") || file.contains("720") -> 720
                    window.contains("480") || file.contains("480") -> 480
                    else -> 0
                }
                sources.add(ExtractedSource(file, "", res))
            }
        }

        // 3. Mixdrop regex fallback
        if (sources.isEmpty()) {
            val mixdropRegex = Regex("""wurl\s*=\s*["'](//[^"']+|https?://[^"']+)["']""")
            val mixdropMatch = mixdropRegex.find(scriptToAnalyze)?.groupValues?.get(1)
            if (!mixdropMatch.isNullOrBlank()) {
                val resolved = if (mixdropMatch.startsWith("//")) "https:$mixdropMatch" else mixdropMatch
                sources.add(ExtractedSource(resolved, "Mixdrop", 1080))
            }
        }

        // 4. Fallback a direct URLs di mp4 o m3u8 nel codice
        if (sources.isEmpty()) {
            val directUrlRegex = Regex("""(https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*)""")
            directUrlRegex.findAll(scriptToAnalyze).forEach { m ->
                val f = m.groupValues[1]
                val res = if (f.contains("720")) 720 else if (f.contains("1080")) 1080 else 0
                sources.add(ExtractedSource(f, "", res))
            }
        }

        if (sources.isNotEmpty()) {
            if (wants720) {
                // Cerchiamo prima esplicitamente la 720p
                val exact720 = sources.firstOrNull { it.resolution == 720 || it.url.contains("720") || it.label.contains("720") }
                if (exact720 != null) {
                    return resolvePlaylistOrDirect(exact720.url, 720)
                }
                // Se ci sono più fonti (es. 1080p e 720p), la seconda solitamente è 720p
                if (sources.size >= 2) {
                    val candidate = sources[1]
                    return resolvePlaylistOrDirect(candidate.url, 720)
                }
                // Se c'è solo un file e contiene 1080, prova a verificare se esiste la variante 720 sul server CDN
                val single = sources.first()
                if (single.url.contains("1080")) {
                    val candidate720Url = single.url.replace("1080", "720")
                    if (isUrlValid(candidate720Url)) {
                        return candidate720Url
                    }
                }
                return resolvePlaylistOrDirect(single.url, 720)
            } else {
                // 1080p richiesto: prendi 1080 o la prima/migliore qualità
                val exact1080 = sources.firstOrNull { it.resolution == 1080 || it.url.contains("1080") || it.label.contains("1080") }
                val target = exact1080 ?: sources.first()
                return resolvePlaylistOrDirect(target.url, 1080)
            }
        }

        return null
    }

    private fun isUrlValid(testUrl: String): Boolean {
        return try {
            val headReq = Request.Builder()
                .url(testUrl)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(headReq).execute().use { resp ->
                resp.isSuccessful || resp.code in 200..399
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun resolvePlaylistOrDirect(url: String, targetRes: Int): String {
        if (!url.contains(".m3u8")) {
            return url
        }
        // Se è una playlist HLS master, estraiamo il sotto-stream alla risoluzione desiderata
        try {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val content = client.newCall(req).execute().use { it.body?.string() } ?: return url
            if (content.contains("#EXT-X-STREAM-INF")) {
                val lines = content.lines()
                var bestLine: String? = null
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val isTarget = if (targetRes == 720) {
                            line.contains("720") || line.contains("1280x720")
                        } else {
                            line.contains("1080") || line.contains("1920x1080")
                        }
                        val nextLine = lines.getOrNull(i + 1)?.trim()
                        if (nextLine != null && !nextLine.startsWith("#")) {
                            if (isTarget) {
                                bestLine = nextLine
                                break
                            } else if (bestLine == null) {
                                bestLine = nextLine
                            }
                        }
                    }
                }
                if (bestLine != null) {
                    return if (bestLine.startsWith("http")) {
                        bestLine
                    } else {
                        URI.create(url).resolve(bestLine).toString()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return url
    }

    private fun findPackedBlock(html: String): String? {
        val regex = Regex("""eval\(function\(p,a,c,k,e,[dr]\).+?\.split\('\|'\)\)\)""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(html)?.value
    }

    private fun unpackDeanEdwards(packed: String): String? {
        return try {
            val argsRegex = Regex("""\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
            val match = argsRegex.find(packed) ?: return null
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toInt()
            val count = match.groupValues[3].toInt()
            val dict = match.groupValues[4].split("|")

            fun base62Decode(str: String): Int {
                var res = 0
                for (ch in str) {
                    val v = when (ch) {
                        in '0'..'9' -> ch - '0'
                        in 'a'..'z' -> ch - 'a' + 10
                        in 'A'..'Z' -> ch - 'A' + 36
                        else -> 0
                    }
                    res = res * radix + v
                }
                return res
            }

            val tokenRegex = Regex("""\b\w+\b""")
            tokenRegex.replace(payload) { tokenMatch ->
                val word = tokenMatch.value
                val idx = try { base62Decode(word) } catch (e: Exception) { -1 }
                if (idx in 0 until count && idx < dict.size && dict[idx].isNotEmpty()) {
                    dict[idx]
                } else {
                    word
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

