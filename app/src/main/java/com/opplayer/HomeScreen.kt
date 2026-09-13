package com.opplayer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    prefs: PlaybackPreferences,
    watchTracker: WatchSessionTracker,
    currentEpisodeNumber: Int,
    watchedEpisodes: Set<Int>,
    favoriteEpisodes: Set<Int>,
    dailyStreak: Int,
    savedPosition: Long,
    onPlay: (Int, Long) -> Unit,
    onEpisodeSelected: (Int) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenStreak: () -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Episodio selezionato localmente: sincronizzato con currentEpisodeNumber
    var selectedEpisode by remember(currentEpisodeNumber) { mutableIntStateOf(currentEpisodeNumber) }
    val selectedSaga = remember(selectedEpisode) { OnePieceHelper.getSagaForEpisode(selectedEpisode) }
    val selectedEpType = remember(selectedEpisode) { OnePieceHelper.getEpisodeType(selectedEpisode) }
    val isFav = favoriteEpisodes.contains(selectedEpisode)
    val isWatched = watchedEpisodes.contains(selectedEpisode)
    val selectedSavedPosition = remember(selectedEpisode, savedPosition) {
        prefs.getEpisodePositionMs(selectedEpisode)
    }

    // Memoria trio: Precedente • Centrale • Successivo
    val prevEpisode = if (selectedEpisode > 1) selectedEpisode - 1 else null
    val nextEpisode = if (selectedEpisode < OnePieceHelper.TOTAL_AIRING_EPISODES) selectedEpisode + 1 else null
    val prevPos = prevEpisode?.let { prefs.getEpisodePositionMs(it) } ?: 0L
    val isPrevWatched = prevEpisode?.let { watchedEpisodes.contains(it) } ?: false
    val nextPos = nextEpisode?.let { prefs.getEpisodePositionMs(it) } ?: 0L
    val isNextWatched = nextEpisode?.let { watchedEpisodes.contains(it) } ?: false

    fun selectEpisode(ep: Int) {
        selectedEpisode = ep
        prefs.setLastEpisode(ep)
        onEpisodeSelected(ep)
    }

    val avgMs = remember { watchTracker.getAverageFirstWatchTimeMs() }
    val daysSince = remember { watchTracker.getDaysSinceFirstWatch() }
    val thisWeekMs = remember { computeThisWeekMs(watchTracker) }

    val carouselState = rememberLazyListState()
    val sagaEpisodes = remember(selectedSaga) { selectedSaga.range.toList() }
    val currentIndex = sagaEpisodes.indexOf(selectedEpisode).coerceAtLeast(0)

    LaunchedEffect(currentIndex) {
        carouselState.animateScrollToItem(currentIndex.coerceAtLeast(0))
    }

    val upcomingEpisodes = remember(sagaEpisodes, selectedEpisode) {
        val inSaga = sagaEpisodes.filter { it > selectedEpisode }
        if (inSaga.isNotEmpty()) inSaga.take(8)
        else ((selectedEpisode + 1)..minOf(selectedEpisode + 8, OnePieceHelper.TOTAL_AIRING_EPISODES)).toList()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Bg0)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = statusBarTop + 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // === HEADER MINIMALE ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ONE PIECE",
                            style = AppType.Caption.copy(color = AppColors.Accent),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Rotta Maggiore",
                            style = AppType.Title.copy(color = AppColors.TextPrimary)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIconButton(icon = Icons.Default.Search, onClick = onOpenSearch)
                        GlassIconButton(icon = Icons.Default.Language, onClick = onOpenBrowser)
                    }
                }
            }

            // === HERO CARD (aggiornato dinamicamente dall'episodio selezionato) ===
            item {
                HeroEpisodeCard(
                    episodeNumber = selectedEpisode,
                    saga = selectedSaga,
                    epType = selectedEpType,
                    savedPosition = selectedSavedPosition,
                    isWatched = isWatched,
                    isFavorite = isFav,
                    remainingInSaga = selectedSaga.range.last - selectedEpisode,
                    onPlay = { onPlay(selectedEpisode, selectedSavedPosition) },
                    onToggleFavorite = { onToggleFavorite(selectedEpisode) }
                )
            }

            // === ROTTA EPISODI (Segmented Control Liquido) ===
            item {
                RottaEpisodiSegmented(
                    currentEpisode = selectedEpisode,
                    saga = selectedSaga,
                    watched = watchedEpisodes,
                    onSelect = { ep ->
                        if (ep in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                            selectEpisode(ep)
                        }
                    }
                )
            }

            // === SEZIONE "QUESTA SETTIMANA" (solo se ha dati) ===
            if (thisWeekMs > 0L || dailyStreak > 1) {
                item {
                    ThisWeekStrip(
                        thisWeekMs = thisWeekMs,
                        streak = dailyStreak,
                        avgMs = avgMs,
                        daysSince = daysSince,
                        onClick = onOpenStreak
                    )
                }
            }

            // === CAROUSEL EPISODI SAGA (numeretti a scorrimento orizzontale) ===
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            selectedSaga.name,
                            style = AppType.Headline.copy(color = AppColors.TextPrimary)
                        )
                        Text(
                            "${selectedSaga.range.count()} ep",
                            style = AppType.Caption.copy(color = AppColors.TextTertiary)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tocca un episodio per impostarlo nel player sopra",
                        style = AppType.Caption.copy(color = AppColors.TextTertiary),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        state = carouselState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        flingBehavior = rememberSnapFlingBehavior(carouselState)
                    ) {
                        items(sagaEpisodes, key = { it }) { ep ->
                            val epSavedPos = prefs.getEpisodePositionMs(ep)
                            EpisodePill(
                                episodeNumber = ep,
                                isSelected = ep == selectedEpisode,
                                isWatched = watchedEpisodes.contains(ep),
                                savedPosition = epSavedPos,
                                onClick = {
                                    // Seleziona l'episodio modificando il player sopra, NON avviando il video direttamente!
                                    selectEpisode(ep)
                                }
                            )
                        }
                    }
                }
            }

            // === PROSSIMI DA GUARDARE ===
            item {
                Text(
                    "Prossimi da guardare",
                    style = AppType.Headline.copy(color = AppColors.TextPrimary)
                )
            }

            items(upcomingEpisodes, key = { it }) { ep ->
                val epSavedPos = prefs.getEpisodePositionMs(ep)
                UpcomingEpisodeRow(
                    episodeNumber = ep,
                    isWatched = watchedEpisodes.contains(ep),
                    savedPosition = epSavedPos,
                    isSelected = ep == selectedEpisode,
                    onClick = {
                        // Tappando imposta l'episodio nella card sopra
                        selectEpisode(ep)
                    },
                    onPlayDirect = {
                        onPlay(ep, epSavedPos)
                    }
                )
            }
        }

        // Status bar glass overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTop + 8.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.Bg0.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ===== COMPONENTI =====

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AppColors.Glass1)
            .hapticPress(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HeroEpisodeCard(
    episodeNumber: Int,
    saga: OnePieceSaga,
    epType: EpisodeType,
    savedPosition: Long,
    isWatched: Boolean,
    isFavorite: Boolean,
    remainingInSaga: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val palette = remember(saga.name) { SagaPalette.forSaga(saga.name) }

    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val isCompleting = isWatched
    val isInProgress = savedPosition > 10_000L
    val badgeColor = when {
        isCompleting -> AppColors.Success
        isInProgress -> AppColors.Success
        else -> AppColors.TextSecondary
    }
    val badgeText = when {
        isCompleting -> "COMPLETATO"
        isInProgress -> "IN CORSO"
        else -> "DA GUARDARE"
    }

    Surface(
        shape = AppShape.CardBig,
        color = AppColors.Glass2,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.5f),
                    AppColors.GlassBorder
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .hapticPress(onClick = onPlay)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.gradientStart, palette.gradientEnd),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(800f, 1600f)
                    )
                )
                .padding(18.dp)
        ) {
            // Riga superiore: Badge stato + Saga + Tipo + Stella Preferito
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AppShape.Chip,
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isInProgress) AppColors.Success.copy(alpha = dotAlpha)
                                    else badgeColor
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            badgeText,
                            style = AppType.Caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = AppShape.Chip,
                    color = Color(epType.hexColor).copy(alpha = 0.20f)
                ) {
                    Text(
                        epType.label,
                        style = AppType.Caption.copy(
                            color = Color(epType.hexColor),
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AppColors.Glass1)
                        .hapticPress(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Preferito",
                        tint = if (isFavorite) AppColors.Gold else AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Titolo episodio ed info saga
            Text(
                "Episodio $episodeNumber",
                style = AppType.Title.copy(
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${saga.name} • ${if (remainingInSaga > 0) "$remainingInSaga rimanenti" else "Finale saga"}",
                style = AppType.Caption.copy(color = AppColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Barra di progressione dell'episodio: appare solo se l'episodio è in corso
            if (savedPosition > 10_000L && !isWatched) {
                Spacer(Modifier.height(14.dp))
                val totalEpisodeMs = 24 * 60 * 1000f
                val epProgress = (savedPosition.toFloat() / totalEpisodeMs).coerceIn(0.02f, 1f)
                val watchedMinutes = (savedPosition / 60_000L).coerceAtLeast(1L)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "In corso • da ${formatTime(savedPosition)}",
                            style = AppType.Caption.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            "${watchedMinutes}m / 24m",
                            style = AppType.Caption.copy(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    LinearProgressIndicator(
                        progress = { epProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(AppShape.Pill),
                        color = AppColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Bottone Play compatto e prominente con testo adattivo
            val playButtonText = when {
                savedPosition > 10_000L -> "Riprendi da ${formatTime(savedPosition)}"
                isWatched -> "Riguarda Episodio $episodeNumber"
                else -> "Guarda Adesso"
            }

            Surface(
                shape = AppShape.Button,
                color = AppColors.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .hapticPress(onClick = onPlay)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (savedPosition > 10_000L) Icons.Default.PlayArrow else if (isWatched) Icons.Default.Replay else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        playButtonText,
                        style = AppType.Body.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * Segmented Control "Rotta Episodi" Liquido:
 * Mostra i segmenti Precedente • Centrale (Attuale) • Successivo
 * Include la barra di progressione della saga (episodi visti e rimanenti)
 */
@Composable
private fun RottaEpisodiSegmented(
    currentEpisode: Int,
    saga: OnePieceSaga,
    watched: Set<Int>,
    onSelect: (Int) -> Unit
) {
    val prevEp = if (currentEpisode > 1) currentEpisode - 1 else null
    val nextEp = if (currentEpisode < OnePieceHelper.TOTAL_AIRING_EPISODES) currentEpisode + 1 else null

    // Conteggio episodi visti e rimanenti nella saga
    val totalInSaga = saga.range.count()
    val watchedInSaga = remember(saga, watched) {
        saga.range.count { watched.contains(it) }
    }
    val remainingInSaga = (totalInSaga - watchedInSaga).coerceAtLeast(0)
    val sagaProgress = if (totalInSaga > 0) (watchedInSaga.toFloat() / totalInSaga).coerceIn(0f, 1f) else 0f

    Surface(
        shape = AppShape.Card,
        color = AppColors.Glass1,
        border = BorderStroke(1.dp, AppColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ROTTA EPISODI",
                    style = AppType.Caption.copy(color = AppColors.TextTertiary, letterSpacing = 1.2.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Precedente • Attuale • Successivo",
                    style = AppType.Caption.copy(color = AppColors.TextTertiary, fontSize = 10.sp)
                )
            }

            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(AppShape.Button)
                    .background(AppColors.Glass0)
                    .border(0.5.dp, AppColors.Separator, AppShape.Button)
                    .padding(3.dp)
            ) {
                val segmentWidth = maxWidth / 3f

                // Active segment indicator in middle
                Box(
                    modifier = Modifier
                        .offset(x = segmentWidth)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .background(AppColors.Accent.copy(alpha = 0.22f))
                        .border(1.dp, AppColors.Accent.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PREV
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp))
                            .then(
                                if (prevEp != null) Modifier.hapticPress { onSelect(prevEp) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (prevEp != null) {
                            val isWatched = watched.contains(prevEp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isWatched) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(AppColors.Success)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    "Ep. $prevEp",
                                    style = AppType.Subhead.copy(
                                        color = AppColors.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        } else {
                            Text(
                                "—",
                                style = AppType.Subhead.copy(color = AppColors.TextTertiary)
                            )
                        }
                    }

                    // CURRENT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val isWatched = watched.contains(currentEpisode)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isWatched) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.Success)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                "Ep. $currentEpisode",
                                style = AppType.Headline.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            )
                        }
                    }

                    // NEXT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp))
                            .then(
                                if (nextEp != null) Modifier.hapticPress { onSelect(nextEp) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (nextEp != null) {
                            val isWatched = watched.contains(nextEp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Ep. $nextEp",
                                    style = AppType.Subhead.copy(
                                        color = AppColors.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (isWatched) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(AppColors.Success)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "—",
                                style = AppType.Subhead.copy(color = AppColors.TextTertiary)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Barra di progressione della saga: quanti episodi visti e quanti ne mancano
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${saga.name} • $watchedInSaga / $totalInSaga visti",
                        style = AppType.Caption.copy(
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        if (remainingInSaga == 0) "Completata ✓" else "$remainingInSaga rimanenti",
                        style = AppType.Caption.copy(
                            color = if (remainingInSaga == 0) AppColors.Success else AppColors.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { sagaProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(AppShape.Pill),
                    color = if (remainingInSaga == 0) AppColors.Success else AppColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.10f)
                )
            }
        }
    }
}

@Composable
private fun ThisWeekStrip(
    thisWeekMs: Long,
    streak: Int,
    avgMs: Long,
    daysSince: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = AppShape.Card,
        color = AppColors.Glass1,
        border = BorderStroke(0.5.dp, AppColors.GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .hapticPress(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = AppColors.Accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Questa settimana",
                    style = AppType.Caption.copy(color = AppColors.TextTertiary),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (thisWeekMs > 0) formatDuration(thisWeekMs) else "$streak giorni di streak",
                    style = AppType.Headline.copy(color = AppColors.TextPrimary)
                )
            }
            if (avgMs > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "≈ ${formatDurationShort(avgMs)}/ep",
                        style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                    )
                    Text(
                        if (daysSince > 0) "da $daysSince giorni" else "",
                        style = AppType.Caption.copy(color = AppColors.TextTertiary)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodePill(
    episodeNumber: Int,
    isSelected: Boolean,
    isWatched: Boolean,
    savedPosition: Long = 0L,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) AppColors.Accent else AppColors.Glass1
    val textColor = if (isSelected) Color.White else AppColors.TextPrimary
    val border = if (isSelected) AppColors.Accent else AppColors.GlassBorder

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(64.dp)
            .clip(AppShape.Card)
            .background(bgColor)
            .then(
                if (!isSelected) Modifier.border(0.5.dp, border, AppShape.Card)
                else Modifier
            )
            .hapticPress(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$episodeNumber",
                style = AppType.Headline.copy(color = textColor),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (isWatched && !isSelected) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(AppColors.Success)
                )
            } else if (savedPosition > 10_000L && !isSelected) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(AppColors.Gold)
                )
            }
        }
    }
}

@Composable
private fun UpcomingEpisodeRow(
    episodeNumber: Int,
    isWatched: Boolean,
    savedPosition: Long = 0L,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onPlayDirect: () -> Unit
) {
    val saga = remember(episodeNumber) { OnePieceHelper.getSagaForEpisode(episodeNumber) }
    val type = remember(episodeNumber) { OnePieceHelper.getEpisodeType(episodeNumber) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .background(if (isSelected) AppColors.Accent.copy(alpha = 0.12f) else AppColors.Glass1)
            .then(
                if (isSelected) Modifier.border(1.dp, AppColors.Accent.copy(alpha = 0.5f), AppShape.Card)
                else Modifier
            )
            .hapticPress(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AppShape.Small)
                .background(AppColors.Glass2)
                .hapticPress(onClick = onPlayDirect),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isWatched) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isWatched) AppColors.Success else AppColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Episodio $episodeNumber",
                style = AppType.Body.copy(color = AppColors.TextPrimary),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (savedPosition > 10_000L) "${saga.name} • Riprendi a ${formatTime(savedPosition)}" else saga.name,
                style = AppType.Caption.copy(
                    color = if (savedPosition > 10_000L) AppColors.Gold else AppColors.TextTertiary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = AppShape.Chip,
            color = Color(type.hexColor).copy(alpha = 0.18f)
        ) {
            Text(
                type.label,
                style = AppType.Caption.copy(color = Color(type.hexColor)),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/** Calcola i ms wall-clock della settimana corrente (lun-dom) */
private fun computeThisWeekMs(tracker: WatchSessionTracker): Long {
    return 0L
}
