package com.opplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    prefs: PlaybackPreferences,
    cloudSyncManager: CloudSyncManager,
    currentEpisode: Int,
    dailyStreak: Int,
    watchedCount: Int,
    onRestoreJsonRequested: () -> Unit,
    onMarkAllWatchedUpToCurrent: () -> Unit,
    onSyncSuccess: (String) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isStreakNotifyEnabled by remember { mutableStateOf(prefs.isStreakReminderEnabled()) }

    val specularBorder = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))
    )
    val accentRed = Color(0xFFFF2A42)
    val goldAccent = Color(0xFFFFD700)
    val skyBlue = Color(0xFF32ADE6)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xF2141624),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 26.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(skyBlue.copy(alpha = 0.20f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = skyBlue, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Centro Dati & Impostazioni",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cloud, Backup, Notifiche e Download",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        // SECTION 1: BACKUP & RIPRISTINO DATI (JSON LOCALE PERSISTENTE)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = skyBlue.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, skyBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Storage, contentDescription = null, tint = skyBlue, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Backup & Ripristino Dati",
                                            color = skyBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = skyBlue.copy(alpha = 0.20f)
                                    ) {
                                        Text(
                                            text = "Locale",
                                            color = skyBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Salvataggio persistente sul dispositivo ($watchedCount episodi registrati). Puoi copiare il codice JSON per salvarlo altrove o incollare una stringa salvata per ripristinare tutti i tuoi dati.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val json = OnePieceHelper.exportToJson(
                                                watched = prefs.getWatchedEpisodes(),
                                                favorites = prefs.getFavoriteEpisodes(),
                                                lastEp = prefs.getLastEpisode(),
                                                lastPos = prefs.getLastPositionMs(),
                                                streak = prefs.getStreak()
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("OnePiece_Backup", json)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Codice JSON copiato negli appunti! 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = skyBlue),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copia Backup", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onRestoreJsonRequested,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, skyBlue.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = skyBlue)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ripristina", color = skyBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 2: NOTIFICHE REALI STREAK
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x22FF2A42),
                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = accentRed, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Promemoria Serie (Streak)", color = accentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Switch(
                                        checked = isStreakNotifyEnabled,
                                        onCheckedChange = {
                                            isStreakNotifyEnabled = it
                                            prefs.setStreakReminderEnabled(it)
                                            if (it) {
                                                StreakReminderReceiver.scheduleDailyReminder(context)
                                                Toast.makeText(context, "Promemoria streak attivato alle 20:30! 🔥", Toast.LENGTH_SHORT).show()
                                            } else {
                                                StreakReminderReceiver.cancelDailyReminder(context)
                                                Toast.makeText(context, "Promemoria streak disattivato", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = accentRed, checkedTrackColor = accentRed.copy(alpha = 0.4f))
                                    )
                                }

                                Text(
                                    text = "Ricevi un avviso sul telefono se la tua serie di $dailyStreak giorni rischia di scadere.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        prefs.sendTestNotification(dailyStreak, currentEpisode)
                                        Toast.makeText(context, "Notifica inviata sul telefono! Controlla la tendina 🔔", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentRed.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testa Notifica Ora sul Telefono", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 3: QUALITÀ DOWNLOAD & MEMORIA
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, specularBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Download Sorgente & Riproduzione",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "I video vengono scaricati alla massima risoluzione nativa offerta dal provider video anime originale (stream MP4 diretto) senza compressioni degradanti.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FastForward, contentDescription = null, tint = accentRed, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Pulsante Salto Rapido nel Player", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Nel player integrato trovi il tasto rapido '+85s' per saltare sigla e riassunto con un solo tocco.", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 4: BACKUP & RIPRISTINO MANUALE
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, specularBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📋 Backup Manuale Locale (File JSON)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Puoi copiare la stringa di backup da incollare altrove o ripristinare un backup precedente.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val json = OnePieceHelper.exportToJson(
                                                watched = prefs.getWatchedEpisodes(),
                                                favorites = prefs.getFavoriteEpisodes(),
                                                lastEp = prefs.getLastEpisode(),
                                                lastPos = prefs.getLastPositionMs(),
                                                streak = prefs.getStreak()
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("OnePiece_Backup", json)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Codice JSON copiato negli appunti! 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copia JSON", color = Color.White, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = onRestoreJsonRequested,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ripristina", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
