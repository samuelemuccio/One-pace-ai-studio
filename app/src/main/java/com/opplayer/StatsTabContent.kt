package com.opplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsTabContent(
    prefs: PlaybackPreferences,
    watchTracker: WatchSessionTracker,
    watchedEpisodes: Set<Int>,
    onBack: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // === CALCOLI STATISTICHE ===
    val firstWatchMs = remember { watchTracker.getFirstWatchMs() }
    val daysSince = remember { watchTracker.getDaysSinceFirstWatch() }
    val avgActiveMs = remember { watchTracker.getAverageActiveMsPerEpisode() }
    val avgPauses = remember { watchTracker.getAveragePausesPerEpisode() }
    val totalSessions = remember { watchTracker.getTotalSessionsCount() }

    val watchedCount = watchedEpisodes.size
    val remaining = (OnePieceHelper.TOTAL_AIRING_EPISODES - watchedCount).coerceAtLeast(0)

    val currentRate = watchTracker.getEpisodesPerDay(watchedCount).toDouble()
    val projections = remember(remaining, currentRate) {
        watchTracker.getAllProjections(remaining, currentRate)
    }

    val firstWatchFmt = remember(firstWatchMs) {
        if (firstWatchMs > 0L) {
            SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(Date(firstWatchMs))
        } else "—"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(statusBarTop + 14.dp))

        // === HEADER ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppColors.Surface3)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "ANALYTICS",
                    style = AppType.Caption.copy(color = AppColors.Accent),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Statistiche di Viaggio",
                    style = AppType.Title.copy(color = AppColors.TextPrimary)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // === CARD HERO: IL VIAGGIO ===
        Surface(
            shape = AppShape.CardBig,
            color = AppColors.Surface2,
            border = BorderStroke(1.dp, AppColors.Separator),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = null,
                        tint = AppColors.Gold,
                        modifier = Modifier.size(16.dp)
                    )
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
                    "Hai iniziato la Rotta Maggiore il",
                    style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    firstWatchFmt,
                    style = AppType.Headline.copy(color = AppColors.TextPrimary)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (daysSince > 0) "$daysSince giorni di navigazione" else "Pronto a salpare",
                    style = AppType.Body.copy(color = AppColors.Success),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = AppColors.Separator)
                Spacer(Modifier.height(20.dp))

                // Metriche primarie: 2 colonne
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBox(
                        modifier = Modifier.weight(1f),
                        label = "Media per episodio",
                        value = if (avgActiveMs > 0) formatDuration(avgActiveMs) else "—",
                        sub = "tempo attivo reale"
                    )
                    StatBox(
                        modifier = Modifier.weight(1f),
                        label = "Pause medie",
                        value = if (avgPauses > 0f) String.format(Locale.ITALY, "%.1f", avgPauses) else "0",
                        sub = "per episodio"
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBox(
                        modifier = Modifier.weight(1f),
                        label = "Sessioni tracciate",
                        value = totalSessions.toString(),
                        sub = "da questa installazione"
                    )
                    StatBox(
                        modifier = Modifier.weight(1f),
                        label = "Ritmo attuale",
                        value = if (currentRate > 0) String.format(Locale.ITALY, "%.1f", currentRate) else "—",
                        sub = "episodi/giorno"
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === CARD PROIEZIONI ===
        Surface(
            shape = AppShape.CardBig,
            color = AppColors.Surface1,
            border = BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "PROIEZIONI DI FINALE",
                        style = AppType.Caption.copy(color = AppColors.Accent),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Ti mancano $remaining episodi. Al ritmo di:",
                    style = AppType.Body.copy(color = AppColors.TextSecondary)
                )

                Spacer(Modifier.height(14.dp))

                projections.forEachIndexed { index, proj ->
                    ProjectionRow(
                        proj = proj,
                        isCurrentRate = index == 0 && currentRate > 0
                    )
                    if (index < projections.size - 1) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // === CARD TEMPO RISPARMIATO (salti sigla) ===
        val savedPerEpisodeMs = 150_000L // 2m30s (150s)
        val totalSaved = (watchedEpisodes.size * savedPerEpisodeMs)
        if (watchedEpisodes.size > 20) {
            Surface(
                shape = AppShape.CardBig,
                color = AppColors.Surface1,
                border = BorderStroke(1.dp, AppColors.Success.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TEMPO RISPARMIATO",
                            style = AppType.Caption.copy(color = AppColors.Success),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        formatDuration(totalSaved),
                        style = AppType.Display.copy(color = AppColors.TextPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "saltando sigle e recap (~2m35s/episodio)",
                        style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                    )
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun StatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = AppType.Caption.copy(color = AppColors.TextTertiary)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = AppType.BigNumber.copy(color = AppColors.TextPrimary)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub,
            style = AppType.Caption.copy(color = AppColors.TextSecondary)
        )
    }
}

@Composable
private fun ProjectionRow(proj: ProjectionResult, isCurrentRate: Boolean) {
    val rateLabel = when {
        isCurrentRate -> "Al tuo ritmo"
        proj.episodesPerDay == proj.episodesPerDay.toInt().toDouble() ->
            "${proj.episodesPerDay.toInt()} al giorno"
        else -> String.format(Locale.ITALY, "%.1f al giorno", proj.episodesPerDay)
    }

    Surface(
        shape = AppShape.Card,
        color = if (isCurrentRate) AppColors.AccentSoft else AppColors.Surface3,
        border = if (isCurrentRate)
            BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.5f))
        else BorderStroke(1.dp, AppColors.Separator),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rateLabel,
                    style = AppType.Subhead.copy(
                        color = if (isCurrentRate) AppColors.Accent else AppColors.TextSecondary
                    ),
                    fontWeight = if (isCurrentRate) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    proj.estimatedDate,
                    style = AppType.Headline.copy(color = AppColors.TextPrimary)
                )
            }
            Text(
                "${proj.daysRemaining} gg",
                style = AppType.Headline.copy(color = AppColors.TextSecondary)
            )
        }
    }
}
