package com.opplayer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DownloadTabContent(
    downloadManagerHelper: DownloadManagerHelper,
    prefs: PlaybackPreferences,
    currentEpisodeNumber: Int,
    watchedEpisodes: Set<Int>,
    onPlayOfflineEpisode: (filePath: String, episodeNumber: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeDownloads by remember { mutableStateOf(emptyList<ActiveDownloadProgress>()) }
    var completedDownloads by remember { mutableStateOf(emptyList<LocalEpisodeItem>()) }
    var selectedQuality by remember { mutableStateOf(prefs.getPreferredDownloadQuality()) }
    var isBatchDownloading by remember { mutableStateOf(false) }

    // Polling periodico ogni 1s per aggiornare i progressi in tempo reale
    LaunchedEffect(Unit) {
        while (true) {
            activeDownloads = downloadManagerHelper.getActiveDownloads()
            completedDownloads = downloadManagerHelper.getCompletedDownloads()
            delay(1000L)
        }
    }

    val specularBorder = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))
    )
    val accentRed = Color(0xFFFF2A42)
    val goldAccent = Color(0xFFFFD700)
    val successGreen = Color(0xFF34C759)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp)
        ) {
            // Header
            item {
                Text(
                    text = "OFFLINE VAULT",
                    color = accentRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Gestione Download",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "File multimediali memorizzati in locale per la visione senza internet",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // CARD CONTROLLO QUALITÀ & BATCH DOWNLOAD (SCARICA IN MASSA)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0x99181826),
                    border = BorderStroke(1.dp, specularBorder),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Risoluzione Download",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Text(
                                text = if (selectedQuality == "720p") "Salva fino a 300MB/ep" else "Massima Fedeltà",
                                color = if (selectedQuality == "720p") successGreen else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selettore 720p vs 1080p
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selectedQuality == "720p") accentRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, if (selectedQuality == "720p") accentRed else Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedQuality = "720p"
                                        prefs.setPreferredDownloadQuality("720p")
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("720p Leggero", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("~180-220 MB • Consigliato", color = successGreen, fontSize = 10.sp)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selectedQuality == "1080p") accentRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, if (selectedQuality == "1080p") accentRed else Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedQuality = "1080p"
                                        prefs.setPreferredDownloadQuality("1080p")
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("1080p Originale", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("~450-550 MB", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Pulsante Download in Massa (Prossimi 3 episodi)
                        Button(
                            onClick = {
                                if (isBatchDownloading) return@Button
                                isBatchDownloading = true
                                coroutineScope.launch {
                                    val startEp = currentEpisodeNumber
                                    val toDownload = (startEp until (startEp + 3))
                                        .filter { it <= OnePieceHelper.TOTAL_AIRING_EPISODES }

                                    Toast.makeText(
                                        context,
                                        "Avvio download in massa per gli episodi ${toDownload.joinToString(", ")} ($selectedQuality)...",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    for (ep in toDownload) {
                                        downloadManagerHelper.startDownloadForEpisode(ep, selectedQuality)
                                    }
                                    activeDownloads = downloadManagerHelper.getActiveDownloads()
                                    isBatchDownloading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isBatchDownloading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBatchDownloading) "Preparazione download..." else "📥 Scarica Prossimi 3 Episodi ($selectedQuality)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
            }

            // SEZIONE 1: DOWNLOAD IN CORSO (TEMPO REALE)
            if (activeDownloads.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download in Corso (${activeDownloads.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentRed.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = "In tempo reale",
                                color = accentRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(activeDownloads, key = { it.downloadId }) { download ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xF0181A28),
                        border = BorderStroke(1.dp, specularBorder),
                        shadowElevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "One Piece • Episodio ${download.episodeNumber}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${download.statusText} • ${download.bytesDownloadedMb} di ${download.totalBytesMb}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    text = "${download.progressPercent}%",
                                    color = accentRed,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { (download.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = accentRed,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        downloadManagerHelper.cancelOrDeleteDownload(download.episodeNumber, download.downloadId)
                                        activeDownloads = downloadManagerHelper.getActiveDownloads()
                                        completedDownloads = downloadManagerHelper.getCompletedDownloads()
                                        Toast.makeText(context, "Download Ep. ${download.episodeNumber} annullato ed eliminato", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF453A).copy(alpha = 0.50f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Annulla Download ✕", color = Color(0xFFFF453A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // SEZIONE 2: EPISODI SCARICATI (PRONTI OFFLINE)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Archivio Locale (${completedDownloads.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    if (completedDownloads.isNotEmpty()) {
                        Text(
                            text = "Pronti offline",
                            color = successGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (completedDownloads.isEmpty() && activeDownloads.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0x66181824),
                        border = BorderStroke(1.dp, specularBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Nessun episodio scaricato",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Usa il pulsante 'Scarica Prossimi 3 Episodi' in alto o tocca 'Scarica (720p)' nella Home per salvare gli episodi e guardarli ovunque senza internet.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            items(completedDownloads, key = { it.episodeNumber }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x99181824),
                    border = BorderStroke(1.dp, specularBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Episodio ${item.episodeNumber}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = successGreen.copy(alpha = 0.20f)
                                ) {
                                    Text(
                                        text = "OFFLINE",
                                        color = successGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(text = "${item.totalBytesMb} MB • Pronto alla visione", color = Color.Gray, fontSize = 12.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Pulsante Play
                            Surface(
                                shape = CircleShape,
                                color = accentRed,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        item.filePath?.let { path ->
                                            onPlayOfflineEpisode(path, item.episodeNumber)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Guarda Offline", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Pulsante Elimina
                            IconButton(
                                onClick = {
                                    downloadManagerHelper.cancelOrDeleteDownload(item.episodeNumber, item.downloadId)
                                    completedDownloads = downloadManagerHelper.getCompletedDownloads()
                                    Toast.makeText(context, "Episodio ${item.episodeNumber} eliminato", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
