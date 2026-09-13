package com.opplayer

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

enum class SagaEpisodeFilter(val label: String) {
    ALL("Tutti"),
    CANON("Canon"),
    ANIME_CANON("Anime Canon"),
    MIXED("Mixed"),
    FILLER("Filler"),
    UNWATCHED("Da vedere"),
    FAVORITES("Preferiti")
}

enum class SagaWatchFilter(val label: String) {
    UNWATCHED("Da vedere"),
    ALL("Tutte"),
    COMPLETED("Completate")
}

@Composable
fun RegistroScreen(
    prefs: PlaybackPreferences,
    watchTracker: WatchSessionTracker,
    downloadManagerHelper: DownloadManagerHelper,
    watchedEpisodes: Set<Int>,
    favoriteEpisodes: Set<Int>,
    dailyStreak: Int,
    onToggleWatched: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onBatchMarkWatched: (List<Int>, Boolean) -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onDownloadEpisode: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Stato per apertura del dettaglio saga (pop-up con lista episodi, filtri, download)
    var selectedSagaForDetail by remember { mutableStateOf<OnePieceSaga?>(null) }
    var sagaWatchFilter by remember { mutableStateOf(SagaWatchFilter.UNWATCHED) }

    val totalWatched = watchedEpisodes.size
    val totalEpisodes = OnePieceHelper.TOTAL_AIRING_EPISODES
    val percentage = (totalWatched.toFloat() / totalEpisodes * 100f)
    val pirateRank = remember(totalWatched) { OnePieceHelper.getPirateRank(totalWatched) }
    val (bountyValue, bountyText) = remember(totalWatched) { OnePieceHelper.calculateBounty(totalWatched) }
    val (calculatedDailyAvg, remainingEpisodes, estimatedDate) = remember(totalWatched) { OnePieceHelper.calculateStats(totalWatched) }
    val episodesPerDay = calculatedDailyAvg.toFloat()

    // Statistiche tempo reale dal WatchSessionTracker
    val totalWatchMs = remember { watchTracker.getTotalWatchTimeMs() }
    val avgWatchMs = remember { watchTracker.getAverageFirstWatchTimeMs() }
    val validSessionsCount = remember { watchTracker.getTotalValidSessionsCount() }

    // Conteggi e filtro saghe (default: Da vedere)
    val completedSagasCount = remember(watchedEpisodes) {
        OnePieceHelper.SAGAS.count { saga ->
            saga.range.all { watchedEpisodes.contains(it) }
        }
    }
    val uncompletedSagasCount = OnePieceHelper.SAGAS.size - completedSagasCount

    val displayedSagas = remember(sagaWatchFilter, watchedEpisodes) {
        OnePieceHelper.SAGAS.filter { saga ->
            val completed = saga.range.all { watchedEpisodes.contains(it) }
            when (sagaWatchFilter) {
                SagaWatchFilter.UNWATCHED -> !completed
                SagaWatchFilter.ALL -> true
                SagaWatchFilter.COMPLETED -> completed
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Bg0)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = statusBarTop + 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === HEADER ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DIARIO DI BORDO",
                            style = AppType.Caption.copy(color = AppColors.Gold),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Rotta & Statistiche",
                            style = AppType.Title.copy(color = AppColors.TextPrimary)
                        )
                    }
                    GlassIconButton(icon = Icons.Default.Settings, onClick = onOpenSettings)
                }
            }

            // === DASHBOARD STATISTICHE INTEGRATA ===
            item {
                GlobalProgressDashboardCard(
                    totalWatched = totalWatched,
                    totalEpisodes = totalEpisodes,
                    percentage = percentage,
                    pirateRank = pirateRank,
                    bountyText = bountyText,
                    remaining = remainingEpisodes,
                    estimatedDate = estimatedDate,
                    episodesPerDay = episodesPerDay
                )
            }

            // === STRIP METRICHE TEMPO REALE ===
            item {
                RealTimeMetricsRow(
                    totalWatchFormatted = if (totalWatchMs > 0) formatDuration(totalWatchMs) else "—",
                    avgWatchFormatted = if (avgWatchMs > 0) formatDuration(avgWatchMs) else "—",
                    streak = dailyStreak,
                    sessions = validSessionsCount
                )
            }

            // === SEZIONE SAGHE ===
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "SAGHE DELLA ROTTA MAGGIORE",
                            style = AppType.Caption.copy(color = AppColors.TextTertiary),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${displayedSagas.size} mostrate",
                            style = AppType.Caption.copy(color = AppColors.Gold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filtro saghe: Da vedere (default), Tutte, Completate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SagaWatchFilter.values().forEach { filter ->
                            val isSelected = filter == sagaWatchFilter
                            val count = when (filter) {
                                SagaWatchFilter.UNWATCHED -> uncompletedSagasCount
                                SagaWatchFilter.ALL -> OnePieceHelper.SAGAS.size
                                SagaWatchFilter.COMPLETED -> completedSagasCount
                            }
                            Surface(
                                shape = AppShape.Pill,
                                color = if (isSelected) AppColors.Accent else AppColors.Surface1,
                                border = if (isSelected) null else BorderStroke(1.dp, AppColors.Separator),
                                modifier = Modifier.clickable { sagaWatchFilter = filter }
                            ) {
                                Text(
                                    text = "${filter.label} ($count)",
                                    style = AppType.Caption.copy(
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (displayedSagas.isEmpty()) {
                item {
                    Surface(
                        shape = AppShape.Card,
                        color = AppColors.Surface1,
                        border = BorderStroke(1.dp, AppColors.Separator),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (sagaWatchFilter == SagaWatchFilter.UNWATCHED)
                                    "Grande Pirata! Hai completato tutte le saghe disponibili! 👑🏴‍☠️"
                                else
                                    "Nessuna saga completata finora.",
                                style = AppType.Subhead.copy(color = AppColors.TextSecondary),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(displayedSagas, key = { it.name }) { saga ->
                    val totalInSaga = saga.range.count()
                    val watchedInSaga = saga.range.count { watchedEpisodes.contains(it) }
                    val completed = watchedInSaga == totalInSaga
                    val progress = watchedInSaga.toFloat() / totalInSaga

                    SagaCardItem(
                        saga = saga,
                        watched = watchedInSaga,
                        total = totalInSaga,
                        progress = progress,
                        completed = completed,
                        onClick = {
                            selectedSagaForDetail = saga
                        }
                    )
                }
            }
        }

        // === POP-UP / MODAL DETTAGLIO SAGA (TUTTI GLI EPISODI, FILTRI, DOWNLOAD, VISTO) ===
        selectedSagaForDetail?.let { saga ->
            SagaDetailDialog(
                saga = saga,
                watchedEpisodes = watchedEpisodes,
                favoriteEpisodes = favoriteEpisodes,
                downloadManagerHelper = downloadManagerHelper,
                onDismiss = { selectedSagaForDetail = null },
                onToggleWatched = onToggleWatched,
                onToggleFavorite = onToggleFavorite,
                onBatchMarkWatched = onBatchMarkWatched,
                onPlayEpisode = { ep ->
                    selectedSagaForDetail = null
                    onPlayEpisode(ep)
                },
                onDownloadEpisode = onDownloadEpisode
            )
        }
    }
}

// ==========================================
// COMPONENTI DASHBOARD STATISTICHE INTEGRATA
// ==========================================

@Composable
private fun GlobalProgressDashboardCard(
    totalWatched: Int,
    totalEpisodes: Int,
    percentage: Float,
    pirateRank: PirateRank,
    bountyText: String,
    remaining: Int,
    estimatedDate: String,
    episodesPerDay: Float
) {
    Surface(
        shape = AppShape.CardBig,
        color = AppColors.Glass1,
        border = BorderStroke(0.5.dp, AppColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Rango Pirata & Taglia Beli
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pirateRank.rankBadge, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            pirateRank.title.uppercase(),
                            style = AppType.Caption.copy(
                                color = AppColors.Gold,
                                fontWeight = FontWeight.Black
                            ),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "\"${pirateRank.quote}\"",
                            style = AppType.Caption.copy(
                                color = AppColors.TextTertiary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = AppShape.Chip,
                    color = AppColors.GoldSoft
                ) {
                    Text(
                        bountyText,
                        style = AppType.Caption.copy(
                            color = AppColors.Gold,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Grande Contatore Avanzamento
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "$totalWatched",
                        style = AppType.BigNumber.copy(color = AppColors.TextPrimary, fontSize = 42.sp)
                    )
                    Text(
                        "su $totalEpisodes episodi completati",
                        style = AppType.Subhead.copy(color = AppColors.TextSecondary)
                    )
                }

                Text(
                    String.format(Locale.ITALY, "%.1f%%", percentage),
                    style = AppType.Headline.copy(
                        color = AppColors.Accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Barra di avanzamento liquid glass
            LinearProgressIndicator(
                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(AppShape.Pill),
                color = AppColors.Accent,
                trackColor = AppColors.Glass2
            )

            Spacer(Modifier.height(14.dp))

            // Proiezioni & Ritmo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rimanenti", style = AppType.Caption.copy(color = AppColors.TextTertiary))
                    Text("$remaining ep", style = AppType.Body.copy(color = AppColors.TextPrimary, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ritmo", style = AppType.Caption.copy(color = AppColors.TextTertiary))
                    Text("${String.format(Locale.ITALY, "%.1f", episodesPerDay)} ep/g", style = AppType.Body.copy(color = AppColors.TextPrimary, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Completamento", style = AppType.Caption.copy(color = AppColors.TextTertiary))
                    Text(estimatedDate, style = AppType.Body.copy(color = AppColors.TextPrimary, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun RealTimeMetricsRow(
    totalWatchFormatted: String,
    avgWatchFormatted: String,
    streak: Int,
    sessions: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Ore a schermo effettive
        MetricSmallCard(
            modifier = Modifier.weight(1f),
            label = "Tempo Reale",
            value = totalWatchFormatted,
            icon = Icons.Default.Schedule,
            tint = AppColors.Purple
        )

        // Media per episodio
        MetricSmallCard(
            modifier = Modifier.weight(1f),
            label = "Media / Ep",
            value = avgWatchFormatted,
            icon = Icons.Default.Speed,
            tint = AppColors.Sky
        )

        // Streak consecutivi
        MetricSmallCard(
            modifier = Modifier.weight(1f),
            label = "Streak",
            value = "$streak gg",
            icon = Icons.Default.LocalFireDepartment,
            tint = Color(0xFFFF3B30)
        )
    }
}

@Composable
private fun MetricSmallCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Surface(
        shape = AppShape.Card,
        color = AppColors.Glass1,
        border = BorderStroke(0.5.dp, AppColors.GlassBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = AppType.Body.copy(
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
            Text(
                label,
                style = AppType.Caption.copy(color = AppColors.TextTertiary, fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}

// ==========================================
// RIGA SAGA NELLA LISTA PRINCIPALE
// ==========================================

@Composable
private fun SagaCardItem(
    saga: OnePieceSaga,
    watched: Int,
    total: Int,
    progress: Float,
    completed: Boolean,
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cerchio progresso con anello colorato
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    // Track grigio
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    // Arco di progresso
                    val ringColor = when {
                        completed -> AppColors.Success
                        progress >= 0.5f -> AppColors.Accent
                        progress > 0f -> AppColors.Warning
                        else -> Color.White.copy(alpha = 0.15f)
                    }
                    if (progress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = (progress * 360f).coerceIn(0f, 359.9f),
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - stroke,
                                size.height - stroke
                            )
                        )
                    }
                }
                if (completed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = AppColors.Success,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        "${(progress * 100).toInt()}",
                        style = AppType.Caption.copy(color = AppColors.TextPrimary),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    saga.name,
                    style = AppType.Body.copy(color = AppColors.TextPrimary),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Episodi ${saga.range.first} - ${saga.range.last} • $watched / $total visti",
                    style = AppType.Caption.copy(color = AppColors.TextTertiary)
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Apri episodi",
                tint = AppColors.TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =========================================================================
// MODAL DETTAGLIO SAGA: TUTTI GLI EPISODI, FILTRI, VISTO, DOWNLOAD, PLAY
// =========================================================================

@Composable
fun SagaDetailDialog(
    saga: OnePieceSaga,
    watchedEpisodes: Set<Int>,
    favoriteEpisodes: Set<Int>,
    downloadManagerHelper: DownloadManagerHelper,
    onDismiss: () -> Unit,
    onToggleWatched: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onBatchMarkWatched: (List<Int>, Boolean) -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onDownloadEpisode: (Int) -> Unit
) {
    var activeFilter by remember { mutableStateOf(SagaEpisodeFilter.ALL) }

    val allEpisodes = remember(saga) { saga.range.toList() }
    val watchedInSaga = remember(allEpisodes, watchedEpisodes) {
        allEpisodes.count { watchedEpisodes.contains(it) }
    }
    val allWatched = watchedInSaga == allEpisodes.size

    // Filtra episodi in base alla selezione
    val filteredEpisodes = remember(allEpisodes, activeFilter, watchedEpisodes, favoriteEpisodes) {
        when (activeFilter) {
            SagaEpisodeFilter.ALL -> allEpisodes
            SagaEpisodeFilter.CANON -> allEpisodes.filter { OnePieceHelper.getEpisodeType(it) == EpisodeType.MANGA_CANON }
            SagaEpisodeFilter.ANIME_CANON -> allEpisodes.filter { OnePieceHelper.getEpisodeType(it) == EpisodeType.ANIME_CANON }
            SagaEpisodeFilter.MIXED -> allEpisodes.filter { OnePieceHelper.getEpisodeType(it) == EpisodeType.MIXED }
            SagaEpisodeFilter.FILLER -> allEpisodes.filter { OnePieceHelper.getEpisodeType(it) == EpisodeType.FILLER }
            SagaEpisodeFilter.UNWATCHED -> allEpisodes.filter { !watchedEpisodes.contains(it) }
            SagaEpisodeFilter.FAVORITES -> allEpisodes.filter { favoriteEpisodes.contains(it) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = AppColors.Bg0,
            border = BorderStroke(1.dp, AppColors.GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(saga.tagColor).copy(alpha = 0.15f),
                                AppColors.Bg0
                            )
                        )
                    )
            ) {
                // Drag handle iOS style
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(AppShape.Pill)
                        .background(Color.White.copy(alpha = 0.30f))
                        .align(Alignment.CenterHorizontally)
                )

                // Header Pop-up
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "DETTAGLIO SAGA",
                                style = AppType.Caption.copy(color = AppColors.Gold),
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                saga.name,
                                style = AppType.Headline.copy(
                                    color = AppColors.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Chiudi Pop-up
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.Glass1)
                                .hapticPress(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Episodi ${saga.range.first} - ${saga.range.last} (${allEpisodes.size} ep) • $watchedInSaga visti",
                        style = AppType.Caption.copy(color = AppColors.TextSecondary)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Azione rapida: Segna tutti visti / Rimuovi tutti
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = AppShape.Chip,
                            color = AppColors.Glass1,
                            modifier = Modifier.hapticPress {
                                onBatchMarkWatched(allEpisodes, !allWatched)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (allWatched) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = if (allWatched) AppColors.TextSecondary else AppColors.Success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (allWatched) "Deseleziona tutti" else "Segna tutta come vista",
                                    style = AppType.Caption.copy(
                                        color = if (allWatched) AppColors.TextSecondary else AppColors.Success,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Riga Filtri orizzontale
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(SagaEpisodeFilter.values()) { filter ->
                            val isSelected = filter == activeFilter
                            Surface(
                                shape = AppShape.Pill,
                                color = if (isSelected) AppColors.Accent else AppColors.Glass1,
                                border = if (isSelected) null else BorderStroke(0.5.dp, AppColors.GlassBorder),
                                modifier = Modifier.hapticPress { activeFilter = filter }
                            ) {
                                Text(
                                    filter.label,
                                    style = AppType.Caption.copy(
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = AppColors.GlassBorder, thickness = 0.5.dp)

                // Lista Episodi
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nessun episodio corrisponde al filtro selezionato",
                                    style = AppType.Subhead.copy(color = AppColors.TextTertiary)
                                )
                            }
                        }
                    } else {
                        items(filteredEpisodes, key = { it }) { ep ->
                            val isWatched = watchedEpisodes.contains(ep)
                            val isFav = favoriteEpisodes.contains(ep)
                            val isDownloaded = remember(ep) { downloadManagerHelper.isEpisodeDownloaded(ep) }

                            SagaEpisodeRowItem(
                                episodeNumber = ep,
                                isWatched = isWatched,
                                isFavorite = isFav,
                                isDownloaded = isDownloaded,
                                onToggleWatched = { onToggleWatched(ep) },
                                onToggleFavorite = { onToggleFavorite(ep) },
                                onDownload = { onDownloadEpisode(ep) },
                                onPlay = { onPlayEpisode(ep) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SagaEpisodeRowItem(
    episodeNumber: Int,
    isWatched: Boolean,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    onToggleWatched: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onPlay: () -> Unit
) {
    val epType = remember(episodeNumber) { OnePieceHelper.getEpisodeType(episodeNumber) }

    Surface(
        shape = AppShape.Card,
        color = if (isWatched) AppColors.Glass0 else AppColors.Glass1,
        border = BorderStroke(0.5.dp, if (isWatched) AppColors.GlassBorder.copy(alpha = 0.15f) else AppColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark Visto/Non Visto interattivo
            EpisodeInteractiveCheckMark(
                isWatched = isWatched,
                onToggle = onToggleWatched
            )

            Spacer(Modifier.width(10.dp))

            // Info Episodio
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ep. $episodeNumber",
                        style = AppType.Body.copy(
                            color = if (isWatched) AppColors.TextSecondary else AppColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = AppShape.Chip,
                        color = Color(epType.hexColor).copy(alpha = 0.18f)
                    ) {
                        Text(
                            epType.label,
                            style = AppType.Caption.copy(
                                color = Color(epType.hexColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Azioni: Preferito, Download, Play
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Preferito
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
                        tint = if (isFavorite) AppColors.Gold else AppColors.TextTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Download
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDownloaded) AppColors.Success.copy(alpha = 0.15f) else AppColors.Glass1)
                        .hapticPress(onClick = onDownload),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isDownloaded) Icons.Default.FileDownloadDone else Icons.Default.Download,
                        contentDescription = if (isDownloaded) "Scaricato" else "Scarica",
                        tint = if (isDownloaded) AppColors.Success else AppColors.TextTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Play Button
                Surface(
                    shape = AppShape.Button,
                    color = AppColors.Accent,
                    modifier = Modifier
                        .height(34.dp)
                        .hapticPress(onClick = onPlay)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Play",
                            style = AppType.Caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

// Checkmark animato con feedback elastico per visto/non visto
@Composable
private fun EpisodeInteractiveCheckMark(
    isWatched: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isWatched) 1.0f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "checkScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (isWatched) AppColors.Success else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (isWatched) AppColors.Success else AppColors.TextTertiary.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isWatched) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Visto",
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color = AppColors.TextPrimary
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AppColors.Glass1)
            .hapticPress(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
