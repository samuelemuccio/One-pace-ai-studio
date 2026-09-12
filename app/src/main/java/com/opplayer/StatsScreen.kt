package com.opplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    prefs: PlaybackPreferences,
    watchTracker: WatchSessionTracker,
    watchedEpisodes: Set<Int>,
    onBack: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val firstWatchMs = remember { watchTracker.getFirstWatchMs() }
    val daysSince = remember { watchTracker.getDaysSinceFirstWatch() }
    val avgMs = remember { watchTracker.getAverageFirstWatchTimeMs() }
    val avgPauses = remember { watchTracker.getAveragePausesPerEpisode() }
    val sessionsCount = remember { watchTracker.getTotalValidSessionsCount() }
    val trackedEps = remember { watchTracker.getTrackedUniqueEpisodes() }

    val watched = watchedEpisodes.size
    val remaining = (OnePieceHelper.TOTAL_AIRING_EPISODES - watched).coerceAtLeast(0)
    val projections = remember(remaining, avgMs) {
        if (avgMs > 0) watchTracker.getAllProjections(remaining, avgMs) else emptyList()
    }
    val totalRemainingMs = remaining.toLong() * avgMs

    val firstDateFmt = remember(firstWatchMs) {
        if (firstWatchMs > 0L) SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(Date(firstWatchMs))
        else "—"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg0)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = statusBarTop + 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header con back
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.Glass1)
                        .hapticPress(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "ANALYTICS",
                        style = AppType.Caption.copy(color = AppColors.Purple),
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Statistiche",
                        style = AppType.Title.copy(color = AppColors.TextPrimary)
                    )
                }
            }
        }

        // HERO: tempo medio per episodio
        item {
            Surface(
                shape = AppShape.CardBig,
                color = AppColors.Glass1,
                border = BorderStroke(0.5.dp, AppColors.GlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "MEDIA PER EPISODIO",
                        style = AppType.Caption.copy(color = AppColors.Purple),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (avgMs > 0) formatDuration(avgMs) else "—",
                        style = AppType.BigNumber.copy(color = AppColors.TextPrimary, fontSize = 44.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "tempo reale medio (wall-clock, esclusa pausa)",
                        style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                    )
                    if (avgPauses > 0.1f) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Media ${String.format(Locale.ITALY, "%.1f", avgPauses)} pause per episodio",
                            style = AppType.Caption.copy(color = AppColors.TextTertiary)
                        )
                    }
                }
            }
        }

        // RIGA: giorni + sessions + ep tracciati
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Giorni",
                    value = daysSince.toString(),
                    icon = Icons.Default.CalendarToday,
                    tint = AppColors.Sky
                )
                MiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Sessioni",
                    value = sessionsCount.toString(),
                    icon = Icons.Default.Timer,
                    tint = AppColors.Purple
                )
                MiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Ep tracciati",
                    value = trackedEps.toString(),
                    icon = Icons.Default.BarChart,
                    tint = AppColors.Success
                )
            }
        }

        // CARD: inizio viaggio
        item {
            Surface(
                shape = AppShape.Card,
                color = AppColors.Glass1,
                border = BorderStroke(0.5.dp, AppColors.GlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = AppColors.Gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "IL TUO VIAGGIO",
                            style = AppType.Caption.copy(color = AppColors.Gold),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Hai iniziato il",
                        style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        firstDateFmt,
                        style = AppType.Headline.copy(color = AppColors.TextPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (daysSince > 0) "$daysSince giorni di navigazione" else "",
                        style = AppType.Subhead.copy(color = AppColors.Success)
                    )
                }
            }
        }

        // PROIEZIONI
        if (projections.isNotEmpty() && avgMs > 0) {
            item {
                Surface(
                    shape = AppShape.CardBig,
                    color = AppColors.Glass1,
                    border = BorderStroke(0.5.dp, AppColors.GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "PROIEZIONI DI FINALE",
                                style = AppType.Caption.copy(color = AppColors.Accent),
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Ti mancano $remaining episodi (~${formatDurationShort(totalRemainingMs)} totali)",
                            style = AppType.Body.copy(color = AppColors.TextSecondary)
                        )

                        Spacer(Modifier.height(16.dp))

                        projections.forEachIndexed { i, p ->
                            ProjectionRow(p)
                            if (i < projections.size - 1) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // TEMPO RISPARMIATO
        if (watched > 20 && avgMs > 0) {
            item {
                val savedPerEp = 155_000L // 2m35s
                val totalSaved = watched * savedPerEp
                Surface(
                    shape = AppShape.CardBig,
                    color = AppColors.Glass1,
                    border = BorderStroke(0.5.dp, AppColors.GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "TEMPO RISPARMIATO",
                                style = AppType.Caption.copy(color = AppColors.Success),
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            formatDuration(totalSaved),
                            style = AppType.BigNumber.copy(color = AppColors.TextPrimary)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "saltando sigle e recap (~2m35s/ep)",
                            style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = AppShape.Card,
        color = AppColors.Glass1,
        border = BorderStroke(0.5.dp, AppColors.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = AppType.Headline.copy(color = AppColors.TextPrimary, fontSize = 20.sp)
            )
            Text(
                label,
                style = AppType.Caption.copy(color = AppColors.TextTertiary)
            )
        }
    }
}

@Composable
private fun ProjectionRow(p: ProjectionResult) {
    val label = when (p.hoursPerDay) {
        0.5 -> "30 min al giorno"
        1.0 -> "1 ora al giorno"
        2.0 -> "2 ore al giorno"
        3.0 -> "3 ore al giorno"
        5.0 -> "5 ore al giorno"
        else -> "${p.hoursPerDay} h/giorno"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .background(AppColors.Glass0)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = AppType.Subhead.copy(color = AppColors.TextSecondary)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                p.estimatedDate,
                style = AppType.Headline.copy(color = AppColors.TextPrimary)
            )
        }
        Text(
            "${p.daysRemaining} gg",
            style = AppType.Subhead.copy(color = AppColors.TextTertiary)
        )
    }
}
