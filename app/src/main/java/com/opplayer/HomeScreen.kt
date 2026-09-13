package com.opplayer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
    onToggleWatched: (Int) -> Unit = {},
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalStreakBadge(
                            streak = dailyStreak,
                            onClick = onOpenStreak
                        )
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

            // === ROTTA EPISODI (Ruota 3D Liquida Liquid Glass) ===
            item {
                RottaEpisodiWheel3D(
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
                    Spacer(Modifier.height(14.dp))
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
                                    // Seleziona l'episodio modificando il player sopra
                                    selectEpisode(ep)
                                },
                                onToggleWatched = {
                                    onToggleWatched(ep)
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
                    },
                    onToggleWatched = {
                        onToggleWatched(ep)
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
            // Barra di progressione dell'episodio: posizionata in alto nella card, appare solo se l'episodio è in corso
            if (savedPosition > 10_000L && !isWatched) {
                val totalEpisodeMs = 24 * 60 * 1000f
                val epProgress = (savedPosition.toFloat() / totalEpisodeMs).coerceIn(0.02f, 1f)
                val watchedMinutes = (savedPosition / 60_000L).coerceAtLeast(1L)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "In corso • da ${formatTime(savedPosition)}",
                            style = AppType.Caption.copy(
                                color = Color.White.copy(alpha = 0.90f),
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
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )
                }
            }

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
 * Badge Streak Minimale in alto con fiammella animata e numerino pulito.
 */
@Composable
fun MinimalStreakBadge(
    streak: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "minimalStreak")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )
    val flameRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameRotation"
    )

    Surface(
        shape = AppShape.Pill,
        color = Color(0x24FF4500),
        border = BorderStroke(
            0.8.dp,
            Brush.linearGradient(
                listOf(
                    Color(0xFFFF5722).copy(alpha = 0.85f),
                    Color(0xFFFF9800).copy(alpha = 0.45f)
                )
            )
        ),
        modifier = modifier.hapticPress(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Streak $streak giorni",
                tint = Color(0xFFFF5722),
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = flameScale
                        scaleY = flameScale
                        rotationZ = flameRotation
                    }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$streak",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Ruota 3D "Rotta Episodi" in stile Liquid Glass iOS:
 * - Scorrimento orizzontale a ruota cilindrica 3D
 * - Lente 3D centrale fissa (liquid glass con bordo speculare e riflesso glare)
 * - Distorsione ottica ai bordi: rotazione Y 3D, scala prospettica e trasparenza graduata
 * - Solo numeri grandi (senza scritte superflue "ROTTA EPISODI", "Precedente", etc.)
 * - Feedback aptico dinamico ad ogni scatto sotto la lente
 * - Barra di progressione della saga (episodi visti e rimanenti)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RottaEpisodiWheel3D(
    currentEpisode: Int,
    saga: OnePieceSaga,
    watched: Set<Int>,
    onSelect: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hapticTick = rememberHapticTick()
    val density = LocalDensity.current
    val totalEpisodes = OnePieceHelper.TOTAL_AIRING_EPISODES

    val itemWidthDp = 76.dp
    val itemWidthPx = with(density) { itemWidthDp.toPx() }

    val initialIndex = (currentEpisode - 1).coerceIn(0, totalEpisodes - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Sincronizza lo scorrimento se currentEpisode cambia da fuori
    LaunchedEffect(currentEpisode) {
        val targetIdx = (currentEpisode - 1).coerceIn(0, totalEpisodes - 1)
        if (!listState.isScrollInProgress) {
            val currentScrollUnit = listState.firstVisibleItemIndex +
                (if (itemWidthPx > 0f) listState.firstVisibleItemScrollOffset / itemWidthPx else 0f)
            val currentCentered = kotlin.math.round(currentScrollUnit).toInt()
            if (currentCentered != targetIdx) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    // Calcolo continuo e matematicamente esatto della posizione centrale (sub-pixel precision)
    val currentScrollUnit by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex + (if (itemWidthPx > 0f) listState.firstVisibleItemScrollOffset / itemWidthPx else 0f)
        }
    }

    val centeredIndex by remember {
        derivedStateOf {
            kotlin.math.round(currentScrollUnit).toInt().coerceIn(0, totalEpisodes - 1)
        }
    }

    // Feedback aptico al passaggio di ogni numero sotto la lente
    var lastHapticIndex by remember { mutableIntStateOf(initialIndex) }
    LaunchedEffect(centeredIndex) {
        if (centeredIndex != lastHapticIndex) {
            hapticTick()
            lastHapticIndex = centeredIndex
        }
    }

    // Quando lo scorrimento finisce, aggiorna l'episodio selezionato
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val selectedEp = centeredIndex + 1
            if (selectedEp != currentEpisode && selectedEp in 1..totalEpisodes) {
                onSelect(selectedEp)
            }
        }
    }

    // Conteggio episodi visti e rimanenti nella saga
    val totalInSaga = saga.range.count()
    val watchedInSaga = remember(saga, watched) {
        saga.range.count { watched.contains(it) }
    }
    val remainingInSaga = (totalInSaga - watchedInSaga).coerceAtLeast(0)
    val sagaProgress = if (totalInSaga > 0) (watchedInSaga.toFloat() / totalInSaga).coerceIn(0f, 1f) else 0f

    val hazeState = LocalHazeState.current

    Surface(
        shape = AppShape.Card,
        color = AppColors.Glass1,
        border = BorderStroke(1.dp, AppColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 14.dp)
        ) {
            // Contenitore ruota 3D Liquid Glass
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.Glass0),
                contentAlignment = Alignment.Center
            ) {
                val viewportWidth = maxWidth
                val horizontalPadding = (viewportWidth - itemWidthDp) / 2

                // Carosello orizzontale a ruota 3D continua e simmetrica
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    flingBehavior = rememberSnapFlingBehavior(listState)
                ) {
                    items(totalEpisodes) { index ->
                        val epNumber = index + 1
                        val isEpWatched = watched.contains(epNumber)
                        val isCentered = (centeredIndex == index)

                        Box(
                            modifier = Modifier
                                .width(itemWidthDp)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val unit = listState.firstVisibleItemIndex +
                                        (if (itemWidthPx > 0f) listState.firstVisibleItemScrollOffset / itemWidthPx else 0f)
                                    val diff = index - unit
                                    val absDiff = kotlin.math.abs(diff)
                                    this.rotationY = (-diff * 28f).coerceIn(-60f, 60f)
                                    // Ingrandimento ottico a lente convessa al passaggio sotto il centro
                                    val lensZoom = (1.26f - absDiff * 0.34f).coerceIn(0.70f, 1.26f)
                                    this.scaleX = lensZoom
                                    this.scaleY = lensZoom
                                    this.alpha = (1.0f - absDiff * 0.35f).coerceIn(0.18f, 1.0f)
                                    // cameraDistance va in pixel reali: 2000f per una prospettiva morbida in stile iOS
                                    this.cameraDistance = 2000f
                                }
                                .hapticPress {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(index)
                                    }
                                    onSelect(epNumber)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Cifra dell'episodio: SEMPRE centrata verticalmente e orizzontalmente
                            Text(
                                text = "$epNumber",
                                fontSize = 24.sp,
                                fontWeight = if (isCentered) FontWeight.Black else FontWeight.Bold,
                                color = when {
                                    isCentered -> Color.White
                                    isEpWatched -> Color(0xFF81C784) // Verde chiaro smeraldo: chiarissimo a colpo d'occhio
                                    else -> AppColors.TextPrimary.copy(alpha = 0.85f)
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.align(Alignment.Center)
                            )

                            // Pallino verde dell'episodio visto: ancorato in basso al centro
                            // Non altera di mezzo pixel la posizione verticale della cifra!
                            if (isEpWatched) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .align(Alignment.BottomCenter)
                                        .offset(y = (-12).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF66BB6A))
                                )
                            }
                        }
                    }
                }

                // Lente convessa Liquid Glass centrale posizionata SOPRA il carosello:
                // avvolge il numero al centro con riflessi ottici, glare e smusso a prisma
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Black.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.90f),
                                        Color.White.copy(alpha = 0.25f),
                                        AppColors.Accent.copy(alpha = 0.70f),
                                        Color.White.copy(alpha = 0.45f)
                                    )
                                )
                            ),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    // Glare speculare superiore a calotta convessa
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodePill(
    episodeNumber: Int,
    isSelected: Boolean,
    isWatched: Boolean,
    savedPosition: Long = 0L,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastActionWasMarkWatched by remember { mutableStateOf<Boolean?>(null) }

    val bgColor = when {
        isSelected -> AppColors.Accent
        isWatched -> Color(0x1F4CAF50)
        else -> AppColors.Glass1
    }
    val textColor = when {
        isSelected -> Color.White
        isWatched -> Color(0xFF81C784)
        else -> AppColors.TextPrimary
    }
    val border = when {
        isSelected -> AppColors.Accent
        isWatched -> Color(0x4D4CAF50)
        else -> AppColors.GlassBorder
    }

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
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val willBeWatched = !isWatched
                    lastActionWasMarkWatched = willBeWatched
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWatched()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Cifra dell'episodio: SEMPRE rigorosamente centrata, identica altezza su tutte le card
        Text(
            "$episodeNumber",
            style = AppType.Headline.copy(color = textColor),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // Pallino di stato ancorato in basso al centro: non altera la posizione del numero
        if (!isSelected) {
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AppColors.Success)
                )
            } else if (savedPosition > 10_000L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AppColors.Gold)
                )
            }
        }

        // Overlay pop animato con icona corretta (spunta verde se confermo, x grigia se tolgo)
        if (lastActionWasMarkWatched != null) {
            val isConfirmedWatched = lastActionWasMarkWatched == true
            LaunchedEffect(lastActionWasMarkWatched) {
                kotlinx.coroutines.delay(650)
                lastActionWasMarkWatched = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AppShape.Card)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConfirmedWatched) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = if (isConfirmedWatched) "Segnato come visto" else "Rimosso dai visti",
                    tint = if (isConfirmedWatched) AppColors.Success else Color(0xFFB0BEC5),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpcomingEpisodeRow(
    episodeNumber: Int,
    isWatched: Boolean,
    savedPosition: Long = 0L,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onPlayDirect: () -> Unit,
    onToggleWatched: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWatched()
                }
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AppShape.Small)
                .background(AppColors.Glass2)
                .combinedClickable(
                    onClick = onPlayDirect,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleWatched()
                    }
                ),
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
