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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    watchTracker: WatchSessionTracker? = null,
    onOpenStats: (() -> Unit)? = null,
    onRestoreJsonRequested: () -> Unit,
    onSyncSuccess: (String) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isStreakNotifyEnabled by remember { mutableStateOf(prefs.isStreakReminderEnabled()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Scrim)
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
                shape = AppShape.CardBig,
                color = AppColors.Surface2,
                border = BorderStroke(1.dp, AppColors.Separator)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                ) {
                    // Header iOS style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Surface3),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = AppColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PREFERENZE",
                                    style = AppType.Caption.copy(color = AppColors.Accent),
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Centro Impostazioni",
                                    style = AppType.Headline.copy(color = AppColors.TextPrimary)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppColors.Surface3)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Chiudi",
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        // SECTION 1: BACKUP & RIPRISTINO DATI
                        Text(
                            text = "DATI E BACKUP",
                            style = AppType.Caption.copy(color = AppColors.TextTertiary),
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        Surface(
                            shape = AppShape.Card,
                            color = AppColors.Surface1,
                            border = BorderStroke(1.dp, AppColors.Separator),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = AppColors.Sky,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Backup Locale (JSON)",
                                            style = AppType.Headline.copy(color = AppColors.TextPrimary)
                                        )
                                    }
                                    Surface(
                                        shape = AppShape.Chip,
                                        color = AppColors.Sky.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$watchedCount ep",
                                            style = AppType.Caption.copy(color = AppColors.Sky),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Copia la stringa di salvataggio negli appunti per esportarla altrove o incolla un salvataggio precedente.",
                                    style = AppType.Subhead.copy(color = AppColors.TextSecondary)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface3),
                                        shape = AppShape.Button
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = AppColors.TextPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copia", style = AppType.Subhead.copy(color = AppColors.TextPrimary), fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = onRestoreJsonRequested,
                                        modifier = Modifier.weight(1f),
                                        shape = AppShape.Button,
                                        border = BorderStroke(1.dp, AppColors.Separator)
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = AppColors.TextPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ripristina", style = AppType.Subhead.copy(color = AppColors.TextPrimary), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 2: PROMEMORIA SERIE (STREAK)
                        Text(
                            text = "NOTIFICHE",
                            style = AppType.Caption.copy(color = AppColors.TextTertiary),
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        Surface(
                            shape = AppShape.Card,
                            color = AppColors.Surface1,
                            border = BorderStroke(1.dp, AppColors.Separator),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AppColors.Gold, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Promemoria Serie",
                                            style = AppType.Headline.copy(color = AppColors.TextPrimary)
                                        )
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
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AppColors.Accent,
                                            uncheckedThumbColor = AppColors.TextSecondary,
                                            uncheckedTrackColor = AppColors.Surface3
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Ricevi un avviso sul telefono se la tua serie di $dailyStreak giorni rischia di scadere.",
                                    style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = {
                                        prefs.sendTestNotification(dailyStreak, currentEpisode)
                                        Toast.makeText(context, "Notifica inviata sul telefono! 🔔", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShape.Button,
                                    border = BorderStroke(1.dp, AppColors.Separator)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testa Notifica sul Telefono", style = AppType.Subhead.copy(color = AppColors.TextPrimary), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
