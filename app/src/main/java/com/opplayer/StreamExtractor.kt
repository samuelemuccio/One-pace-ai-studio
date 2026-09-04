package com.opplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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

    suspend fun resolveStreamUrl(episodePageUrl: String): String? = withContext(Dispatchers.IO) {
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

            resolveHost(embedUrl)
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

    private fun resolveHost(embedUrl: String): String? {
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

        // Estrazione URL finale
        val fileRegex = Regex("""["']?file["']?\s*:\s*["'](https?://[^"']+)["']""")
        val fileMatch = fileRegex.find(scriptToAnalyze)?.groupValues?.get(1)
        if (!fileMatch.isNullOrBlank()) return fileMatch

        val mixdropRegex = Regex("""wurl\s*=\s*["'](//[^"']+|https?://[^"']+)["']""")
        val mixdropMatch = mixdropRegex.find(scriptToAnalyze)?.groupValues?.get(1)
        if (!mixdropMatch.isNullOrBlank()) {
            return if (mixdropMatch.startsWith("//")) "https:$mixdropMatch" else mixdropMatch
        }

        val directUrlRegex = Regex("""(https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*)""")
        return directUrlRegex.find(scriptToAnalyze)?.groupValues?.get(1)
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

