package com.opplayer

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ActiveDownloadProgress(
    val downloadId: Long,
    val episodeNumber: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: Int,
    val statusText: String,
    val progressPercent: Int,
    val downloadedMb: String,
    val totalMb: String,
    val remainingMb: String = "",
    val remainingPercent: Int = 100 - progressPercent,
    val bytesDownloadedMb: String = downloadedMb,
    val totalBytesMb: String = totalMb
)

data class LocalEpisodeItem(
    val episodeNumber: Int,
    val file: File,
    val sizeMb: String,
    val lastModified: Long,
    val filePath: String = file.absolutePath,
    val totalBytesMb: String = sizeMb,
    val downloadId: Long? = null
)

class DownloadManagerHelper(private val context: Context) {

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("op_downloads_map", Context.MODE_PRIVATE)

    fun getMoviesDir(): File? {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    }

    private fun getActiveMap(): MutableMap<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        val all = prefs.all
        for ((k, v) in all) {
            val id = k.toLongOrNull()
            val ep = (v as? Int) ?: (v as? String)?.toIntOrNull()
            if (id != null && ep != null) {
                result[id] = ep
            }
        }
        return result
    }

    private fun saveActiveMap(map: Map<Long, Int>) {
        val editor = prefs.edit().clear()
        for ((k, v) in map) {
            editor.putInt(k.toString(), v)
        }
        editor.apply()
    }

    suspend fun startDownloadForEpisode(
        epNumber: Int,
        quality: String = "720p",
        directUrl: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val dir = getMoviesDir() ?: return@withContext Result.failure(Exception("Cartella file non accessibile"))
            val targetFile = File(dir, "OnePiece_Ep_$epNumber.mp4")

            if (targetFile.exists() && targetFile.length() > 2_000_000L) {
                return@withContext Result.failure(Exception("Episodio $epNumber già scaricato! 💾"))
            }

            val resolvedUrl = directUrl ?: StreamExtractor.resolveStreamUrl(
                OnePieceHelper.buildEpisodeUrl("", epNumber),
                preferredQuality = quality
            )
            if (resolvedUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Impossibile estrarre stream per Ep. $epNumber"))
            }

            // Cancella eventuale file residuo incompleto
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
                setTitle("One Piece - Episodio $epNumber")
                setDescription("Download episodio offline")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(targetFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = dm.enqueue(request)

            val currentMap = getActiveMap()
            currentMap[downloadId] = epNumber
            saveActiveMap(currentMap)

            Result.success(downloadId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun cancelOrDeleteDownload(epNumber: Int, downloadId: Long? = null): Boolean {
        var removed = false
        val activeMap = getActiveMap()

        // 1. Ferma e cancella il task dal DownloadManager di Android
        if (downloadId != null) {
            try { dm.remove(downloadId) } catch (e: Exception) { e.printStackTrace() }
            activeMap.remove(downloadId)
            removed = true
        }

        // Cerca tutti i downloadId associati all'episodio
        val idsToRemove = activeMap.filterValues { it == epNumber }.keys.toList()
        for (id in idsToRemove) {
            try { dm.remove(id) } catch (e: Exception) { e.printStackTrace() }
            activeMap.remove(id)
            removed = true
        }
        saveActiveMap(activeMap)

        // 2. Cancella il file parziale o completo dal disco
        val dir = getMoviesDir()
        if (dir != null) {
            val targetFile = File(dir, "OnePiece_Ep_$epNumber.mp4")
            if (targetFile.exists()) {
                targetFile.delete()
                removed = true
            }
            // Elimina anche eventuali file temporanei o parziali
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith("OnePiece_Ep_${epNumber}.") || f.name.contains("Ep_$epNumber")) {
                    f.delete()
                    removed = true
                }
            }
        }
        return removed
    }

    fun getActiveDownloads(): List<ActiveDownloadProgress> {
        val activeMap = getActiveMap()
        if (activeMap.isEmpty()) return emptyList()

        val list = mutableListOf<ActiveDownloadProgress>()
        val ids = activeMap.keys.toLongArray()
        if (ids.isEmpty()) return emptyList()

        val query = DownloadManager.Query().setFilterById(*ids)
        var cursor: Cursor? = null
        val foundIdsInCursor = mutableSetOf<Long>()
        val idsToPurge = mutableListOf<Long>()

        try {
            cursor = dm.query(query)
            val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                foundIdsInCursor.add(id)
                val ep = activeMap[id] ?: continue
                val bytesSoFar = if (bytesCol >= 0) cursor.getLong(bytesCol) else 0L
                val totalBytes = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                val status = if (statusCol >= 0) cursor.getInt(statusCol) else DownloadManager.STATUS_RUNNING

                val percent = if (totalBytes > 0) {
                    ((bytesSoFar * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else 0

                val statusText = when (status) {
                    DownloadManager.STATUS_PENDING -> "In attesa..."
                    DownloadManager.STATUS_PAUSED -> "In pausa"
                    DownloadManager.STATUS_RUNNING -> "Download in corso ($percent%)"
                    DownloadManager.STATUS_SUCCESSFUL -> "Completato"
                    DownloadManager.STATUS_FAILED -> "Errore / Annullato"
                    else -> "In scaricamento..."
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    idsToPurge.add(id)
                } else {
                    val remainingBytes = (totalBytes - bytesSoFar).coerceAtLeast(0L)
                    val remainingMbStr = if (totalBytes > 0) {
                        String.format("%.1f MB rimanenti", remainingBytes / (1024f * 1024f))
                    } else "Calcolo..."

                    list.add(
                        ActiveDownloadProgress(
                            downloadId = id,
                            episodeNumber = ep,
                            bytesDownloaded = bytesSoFar,
                            totalBytes = totalBytes,
                            status = status,
                            statusText = statusText,
                            progressPercent = percent,
                            downloadedMb = String.format("%.1f MB", bytesSoFar / (1024f * 1024f)),
                            totalMb = if (totalBytes > 0) String.format("%.1f MB", totalBytes / (1024f * 1024f)) else "Calcolo...",
                            remainingMb = remainingMbStr,
                            remainingPercent = (100 - percent).coerceIn(0, 100)
                        )
                    )
                }
            }

            // Qualsiasi ID salvato nelle preferenze che DownloadManager non riconosce più (perché cancellato) va rimosso
            val missingIds = activeMap.keys.filter { !foundIdsInCursor.contains(it) }
            idsToPurge.addAll(missingIds)

            if (idsToPurge.isNotEmpty()) {
                for (cid in idsToPurge) {
                    activeMap.remove(cid)
                }
                saveActiveMap(activeMap)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return list.sortedBy { it.episodeNumber }
    }

    fun getCompletedDownloads(): List<LocalEpisodeItem> {
        val dir = getMoviesDir() ?: return emptyList()
        val files = dir.listFiles { _, name -> name.startsWith("OnePiece_Ep_") && name.endsWith(".mp4") } ?: return emptyList()
        return files.mapNotNull { f ->
            val num = Regex("""OnePiece_Ep_(\d+)\.mp4""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (num != null && f.length() > 500_000L) {
                val mb = String.format("%.1f MB", f.length() / (1024f * 1024f))
                LocalEpisodeItem(num, f, mb, f.lastModified())
            } else null
        }.sortedBy { it.episodeNumber }
    }

    fun isEpisodeDownloaded(epNumber: Int): Boolean {
        val dir = getMoviesDir() ?: return false
        val file = File(dir, "OnePiece_Ep_$epNumber.mp4")
        return file.exists() && file.length() > 1_000_000L
    }

    fun getDownloadedFile(epNumber: Int): File? {
        val dir = getMoviesDir() ?: return null
        val file = File(dir, "OnePiece_Ep_$epNumber.mp4")
        return if (file.exists() && file.length() > 1_000_000L) file else null
    }
}
