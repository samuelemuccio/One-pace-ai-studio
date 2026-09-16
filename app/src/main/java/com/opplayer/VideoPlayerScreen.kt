package com.opplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun Modifier.iosSpringPress(onClick: (() -> Unit)? = null): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "springScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    currentWebUrl: String,
    audioLanguage: AudioLanguage = AudioLanguage.ITA,
    initialPositionMs: Long = 0L,
    isInPipMode: Boolean = false,
    isAdvancingNext: Boolean = false,
    isSwitchingLanguage: Boolean = false,
    onEnterPip: () -> Unit = {},
    onDownloadRequested: (String, Int) -> Unit,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onLanguageChanged: (AudioLanguage, Long) -> Unit = { _, _ -> },
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { PlaybackPreferences(context) }
    val watchTracker = remember { WatchSessionTracker(context) }

    val episodeNumber = remember(currentWebUrl) {
        OnePieceHelper.extractEpisodeNumber(currentWebUrl)
    }
    val epType = remember(episodeNumber) {
        OnePieceHelper.getEpisodeType(episodeNumber)
    }

    var skipIntervalSeconds by remember { mutableIntStateOf(prefs.getSkipStep()) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getSpeed()) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var isZoomToFill by remember { mutableStateOf(false) }

    var isLandscape by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    // Boost velocità: long-press sulla metà destra del video
    var isBoosting by remember { mutableStateOf(false) }
    var boostSpeed by remember { mutableFloatStateOf(prefs.getBoostSpeed()) }
    val isBoostEnabled = remember { prefs.isBoostEnabled() }

    // Timestamp Mediaset (risoluzione: custom utente -> JSON per ep -> default saga -> fallback)
    val mediasetTs = remember(episodeNumber) {
        MediasetTimestampProvider.getForEpisode(context, episodeNumber)
    }
    var currentOpeningEnd by remember(episodeNumber) {
        mutableLongStateOf(mediasetTs.openingEndMs)
    }
    var hasSkippedOpening by remember(episodeNumber) { mutableStateOf(false) }
    var hasSkippedRecap by remember(episodeNumber) { mutableStateOf(false) }

    var currentPos by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    var skipFeedbackText by remember { mutableStateOf<String?>(null) }
    var skipFeedbackIsForward by remember { mutableStateOf(true) }
    var skipFeedbackSeq by remember { mutableIntStateOf(0) }

    val specularBorder = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))
    )
    val accentRed = Color(0xFFFF2A42)

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Tetto a 60 Hz per il player: quando tieni premuto a lungo (boost 3x) o skippi di frequente,
        // il display LTPO dell'S25 Ultra non schizza inutilmente a 120 Hz, risparmiando preziosa batteria.
        val window = activity?.window
        val prevRefreshRate = window?.attributes?.preferredRefreshRate ?: 0f
        window?.let { w ->
            val params = w.attributes
            params.preferredRefreshRate = 60f
            w.attributes = params
        }
        onDispose {
            prefs.saveSpeed(playbackSpeed)
            prefs.saveSkipStep(skipIntervalSeconds)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w ->
                val params = w.attributes
                params.preferredRefreshRate = prevRefreshRate
                w.attributes = params
            }
        }
    }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 1_500, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playbackParameters = PlaybackParameters(playbackSpeed)
            }
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            exoPlayer.prepare()
            val targetPos = if (initialPositionMs > 0L) initialPositionMs else currentPos
            if (targetPos > 0L) {
                exoPlayer.seekTo(targetPos)
            }
            exoPlayer.playWhenReady = true
        }
    }

    // Listener nativo Media3 — niente polling. Aggiorna lo stato SOLO quando cambia.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                // Hook del tracker: pausa/riprende il conteggio del tempo
                if (isPlayingNow) watchTracker.onVideoResumed()
                else watchTracker.onVideoPaused()
            }
            override fun onPlaybackStateChanged(state: Int) {
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }
            override fun onEvents(player: Player, events: Player.Events) {
                bufferedPos = player.bufferedPosition.coerceAtLeast(0L)
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                    currentPos = player.currentPosition.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Polling a 1s SOLO quando il video sta andando — 1 wake-up/sec invece di 2.5
    LaunchedEffect(isPlaying, isDraggingSlider, exoPlayer) {
        if (!isPlaying || isDraggingSlider) return@LaunchedEffect
        while (isActive) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            onPositionChanged(currentPos)

            // Logica "visto" INTELLIGENTE:
            // posizione ≥ 22:00 AND wall-clock attivo ≥ 4 minuti
            if (watchTracker.shouldMarkAsWatched(currentPos)) {
                prefs.markEpisodeWatched(episodeNumber, true)
            }
            delay(1_000L)
        }
    }

    // Auto-hide controls after 4.5s
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4500)
            if (!showSettingsPanel && !isDraggingSlider) {
                showControls = false
            }
        }
    }

    // Auto-hide skip feedback — garanzia assoluta contro badge bloccati a schermo
    LaunchedEffect(skipFeedbackText, skipFeedbackSeq) {
        if (skipFeedbackText != null) {
            delay(1200)
            skipFeedbackText = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // Registra i callback per le azioni PiP (Play/Pause, -10s, +10s)
    DisposableEffect(Unit) {
        val act = context as? MainActivity
        act?.pipPlayPauseCallback = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        act?.pipForwardCallback = {
            val target = (exoPlayer.currentPosition + 10_000L)
                .coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.seekTo(target)
        }
        act?.pipRewindCallback = {
            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
        }
        onDispose {
            act?.pipPlayPauseCallback = null
            act?.pipForwardCallback = null
            act?.pipRewindCallback = null
        }
    }

    // Aggiorna le azioni PiP quando cambia play/pause durante PiP
    LaunchedEffect(isPlaying, isInPipMode) {
        if (isInPipMode) {
            (context as? MainActivity)?.enterPipMode(isPlaying)
        }
    }

    if (isInPipMode) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Video Surface with 100% pure black canvas for AMOLED
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap & Double Tap & Long Press Gesture Overlay
        // - Tap: toggle controlli
        // - Double tap sx/dx: skip
        // - Double tap centro: play/pause
        // - Long press metà destra: BOOST velocità
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(skipIntervalSeconds, boostSpeed, isBoostEnabled) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            // Al rilascio del dito, disattiva il boost se attivo
                            if (isBoosting) {
                                isBoosting = false
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            }
                        },
                        onLongPress = { offset ->
                            if (isBoostEnabled && offset.x > size.width * 0.5f) {
                                isBoosting = true
                                exoPlayer.playbackParameters = PlaybackParameters(boostSpeed)
                            }
                        },
                        onTap = {
                            if (showSettingsPanel) {
                                showSettingsPanel = false
                            } else {
                                showControls = !showControls
                            }
                        },
                        onDoubleTap = { offset ->
                            val skipMs = skipIntervalSeconds * 1000L
                            if (offset.x < size.width * 0.40f) {
                                val target = (exoPlayer.currentPosition - skipMs).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPos = target
                                skipFeedbackText = "-${skipIntervalSeconds}s"
                                skipFeedbackIsForward = false
                                skipFeedbackSeq++
                            } else if (offset.x > size.width * 0.60f) {
                                val target = (exoPlayer.currentPosition + skipMs).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(target)
                                currentPos = target
                                skipFeedbackText = "+${skipIntervalSeconds}s"
                                skipFeedbackIsForward = true
                                skipFeedbackSeq++
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                            }
                        }
                    )
                }
        )

        // DOUBLE-TAP SKIP ANIMATED FEEDBACK BADGE
        AnimatedVisibility(
            visible = skipFeedbackText != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(if (skipFeedbackIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 60.dp)
        ) {
            skipFeedbackText?.let { text ->
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, specularBorder),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (skipFeedbackIsForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // BOOST VISUAL FEEDBACK — minimal, piccolo testo in alto a destra
        AnimatedVisibility(
            visible = isBoosting,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text(
                text = String.format(Locale.ITALY, "%.1fx", boostSpeed),
                style = AppType.Headline.copy(
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                modifier = Modifier
                    .padding(top = 90.dp, end = 28.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.7f)
                    )
            )
        }

        // LOADING NEXT EPISODE OVERLAY (Seamless transition without exiting)
        if (isAdvancingNext) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xF0181824),
                    border = BorderStroke(1.dp, specularBorder),
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accentRed,
                            modifier = Modifier.size(42.dp),
                            strokeWidth = 3.5.dp
                        )
                        Text(
                            text = "Caricamento Episodio ${episodeNumber + 1}...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // SMART FLOATING SKIP: Compare unicamente durante l'effettiva sigla quando i controlli sono nascosti.
        // Appena la sigla termina o viene saltata, scompare istantaneamente senza rimanere a schermo per 5 minuti.
        val isBeforeOpeningFloating = !hasSkippedOpening && currentOpeningEnd > 0L && currentPos < (currentOpeningEnd - 1_500L)
        val showFloatingSkip = !showControls && !isInPipMode && isPlaying && isBeforeOpeningFloating

        AnimatedVisibility(
            visible = showFloatingSkip,
            enter = fadeIn(tween(200)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 36.dp, end = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 12.dp,
                modifier = Modifier.iosSpringPress {
                    hasSkippedOpening = true
                    val target = (currentOpeningEnd + 1_000L).coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                    exoPlayer.seekTo(target)
                    currentPos = target
                    skipFeedbackText = "Sigla Saltata"
                    skipFeedbackIsForward = true
                    skipFeedbackSeq++
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Salta Sigla",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // CINEMA FULLSCREEN CONTROLS (Frosted Glass iOS 17)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // TOP BAR: Single back arrow on the left, NO redundant X on the right
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Back button + episode info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, specularBorder),
                                modifier = Modifier.iosSpringPress(onClick = onClose)
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Episodio $episodeNumber",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = OnePieceHelper.getSagaForEpisode(episodeNumber).name,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(epType.hexColor).copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(epType.hexColor).copy(alpha = 0.65f))
                            ) {
                                Text(
                                    text = epType.label,
                                    color = Color(epType.hexColor),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Interactive Audio & Subtitles Language Toggle
                            val isItaDubbed = OnePieceHelper.isDubbedInItalian(episodeNumber)
                            val displayLang = if (audioLanguage == AudioLanguage.ITA && !isItaDubbed) AudioLanguage.SUB_ITA else audioLanguage
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x35FF2A42),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                modifier = Modifier.iosSpringPress {
                                    val nextLang = audioLanguage.opposite
                                    if (nextLang == AudioLanguage.ITA && !isItaDubbed) {
                                        Toast.makeText(
                                            context,
                                            "Ep. $episodeNumber non ancora doppiato in ITA — Disponibile in SUB-ITA 🇯🇵",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val currentPos = exoPlayer.currentPosition
                                        onLanguageChanged(nextLang, currentPos)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(displayLang.flag, fontSize = 11.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (audioLanguage == AudioLanguage.ITA && !isItaDubbed) "SUB-ITA (Inedito)" else displayLang.shortLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Right action buttons: Aspect Ratio (icon only, uniform 38dp), Download, Rotation, Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Uniform 38dp Aspect Ratio Button (Icon only: Fit / Zoom)
                            Surface(
                                shape = CircleShape,
                                color = if (isZoomToFill) accentRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (isZoomToFill) accentRed else Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.iosSpringPress {
                                    isZoomToFill = !isZoomToFill
                                }
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isZoomToFill) Icons.Default.FullscreenExit else Icons.Default.AspectRatio,
                                        contentDescription = "Rapporto Aspetto",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            if (!videoUrl.startsWith("/")) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, specularBorder),
                                    modifier = Modifier.iosSpringPress { onDownloadRequested(videoUrl, episodeNumber) }
                                ) {
                                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Download, contentDescription = "Scarica", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Picture in Picture Button
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, specularBorder),
                                modifier = Modifier.iosSpringPress { onEnterPip() }
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, specularBorder),
                                modifier = Modifier.iosSpringPress {
                                    isLandscape = !isLandscape
                                    activity?.requestedOrientation = if (isLandscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = "Ruota", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (showSettingsPanel) accentRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (showSettingsPanel) accentRed else Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.iosSpringPress { showSettingsPanel = !showSettingsPanel }
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Tune, contentDescription = "Impostazioni", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // BOTTOM BAR: Scrubber Slider + Clean Controls (no manual 10s buttons, only anime skips and next ep)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Scrubber Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (isDraggingSlider) sliderDragPosition.toLong() else currentPos),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (totalDuration > 0) {
                                    val bufferProgress = (bufferedPos.toFloat() / totalDuration).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { bufferProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .padding(horizontal = 6.dp),
                                        color = Color.White.copy(alpha = 0.35f),
                                        trackColor = Color.White.copy(alpha = 0.12f)
                                    )
                                }

                                Slider(
                                    value = if (isDraggingSlider) sliderDragPosition else currentPos.toFloat(),
                                    onValueChange = { newPos ->
                                        isDraggingSlider = true
                                        sliderDragPosition = newPos
                                    },
                                    onValueChangeFinished = {
                                        val targetPos = sliderDragPosition.toLong()
                                        exoPlayer.seekTo(targetPos)
                                        currentPos = targetPos
                                        isDraggingSlider = false
                                        if (targetPos < 20_000L) {
                                            hasSkippedOpening = false
                                            hasSkippedRecap = false
                                        }
                                    },
                                    valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(1f)),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentRed,
                                        activeTrackColor = accentRed,
                                        inactiveTrackColor = Color.Transparent
                                    )
                                )
                            }

                            Text(
                                text = formatTime(totalDuration),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Bottom Actions Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Big Play/Pause Button
                            Surface(
                                shape = CircleShape,
                                color = accentRed,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier.iosSpringPress {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    isPlaying = exoPlayer.isPlaying
                                }
                            ) {
                                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Right: Smart Anime Skips (Sigla + Recap) & Next Ep
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sigla Mediaset (timestamp intelligente da MediasetTimestampProvider)
                                val hasOpening = currentOpeningEnd > 0L
                                val isBeforeOpeningEnd = !hasSkippedOpening && hasOpening && currentPos < (currentOpeningEnd - 1_500L)
                                if (isBeforeOpeningEnd) {
                                    val skipLabel = if (currentOpeningEnd < 120_000L) {
                                        "Sigla (${currentOpeningEnd / 1000}s)"
                                    } else {
                                        "Sigla (${currentOpeningEnd / 60000}m ${(currentOpeningEnd % 60000) / 1000}s)"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, specularBorder),
                                        modifier = Modifier.iosSpringPress {
                                            hasSkippedOpening = true
                                            val target = (currentOpeningEnd + 1_000L).coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                                            exoPlayer.seekTo(target)
                                            currentPos = target
                                            skipFeedbackText = "Sigla Saltata"
                                            skipFeedbackIsForward = true
                                            skipFeedbackSeq++
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.FastForward,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = skipLabel,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Recap skip (4m 30s = 270s) - scompare automaticamente dopo i primi 5 minuti o una volta premuto
                                val canShowRecapControls = !hasSkippedRecap && currentPos < 300_000L && (totalDuration == 0L || totalDuration > 300_000L)
                                if (canShowRecapControls) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = accentRed.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f)),
                                        modifier = Modifier.iosSpringPress {
                                            hasSkippedRecap = true
                                            val target = (exoPlayer.currentPosition + 270_000L).coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                                            exoPlayer.seekTo(target)
                                            currentPos = target
                                            skipFeedbackText = "Recap Saltato (+4m 30s)"
                                            skipFeedbackIsForward = true
                                            skipFeedbackSeq++
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.FastForward, contentDescription = null, tint = accentRed, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recap (+4m 30s)", color = accentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Previous Episode Button (se disponibile)
                                if (episodeNumber > 1 && onPreviousEpisode != null) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                        modifier = Modifier.iosSpringPress {
                                            onPositionChanged(currentPos)
                                            prefs.saveEpisodePosition(episodeNumber, currentPos)
                                            onPreviousEpisode()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.SkipPrevious, contentDescription = "Episodio Precedente", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Ep. ${episodeNumber - 1}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Next Episode Button
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accentRed,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.iosSpringPress {
                                        onPositionChanged(currentPos)
                                        prefs.saveEpisodePosition(episodeNumber, currentPos)
                                        onNextEpisode()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ep. ${episodeNumber + 1}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.SkipNext, contentDescription = "Episodio Successivo", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FROSTED GLASS SETTINGS PANEL WITH CONTINUOUS SPEED SLIDER
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(330.dp),
                color = Color(0xF514141E),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = accentRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Impostazioni Cinema", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        IconButton(onClick = { showSettingsPanel = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Playback Speed with Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Velocità Riproduzione", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentRed.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f))
                        ) {
                            Text(
                                text = String.format("%.2fx", playbackSpeed),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = playbackSpeed,
                        onValueChange = { sp ->
                            playbackSpeed = (kotlin.math.round(sp * 20) / 20f).coerceIn(0.5f, 2.5f)
                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            prefs.saveSpeed(playbackSpeed)
                        },
                        valueRange = 0.5f..2.5f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = accentRed,
                            activeTrackColor = accentRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick speed presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                            val isSel = kotlin.math.abs(playbackSpeed - sp) < 0.04f
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) accentRed else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, if (isSel) accentRed else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .iosSpringPress {
                                        playbackSpeed = sp
                                        exoPlayer.playbackParameters = PlaybackParameters(sp)
                                        prefs.saveSpeed(sp)
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sp}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Traccia Audio & Sottotitoli", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isItaDubbed = OnePieceHelper.isDubbedInItalian(episodeNumber)
                        listOf(AudioLanguage.ITA, AudioLanguage.SUB_ITA).forEach { lang ->
                            val isSel = lang == audioLanguage
                            val isAvailable = lang != AudioLanguage.ITA || isItaDubbed
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) accentRed.copy(alpha = 0.85f) else Color.White.copy(alpha = if (isAvailable) 0.08f else 0.04f),
                                border = BorderStroke(1.dp, if (isSel) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = if (isAvailable) 0.15f else 0.06f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .iosSpringPress {
                                        if (lang != audioLanguage) {
                                            if (lang == AudioLanguage.ITA && !isItaDubbed) {
                                                Toast.makeText(
                                                    context,
                                                    "Ep. $episodeNumber non ancora doppiato in ITA — Disponibile in SUB-ITA 🇯🇵",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                val currentPos = exoPlayer.currentPosition
                                                onLanguageChanged(lang, currentPos)
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(lang.flag, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (lang == AudioLanguage.ITA && !isItaDubbed) "ITA (Inedito)" else lang.shortLabel,
                                        color = if (isAvailable) Color.White else Color.White.copy(alpha = 0.45f),
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Intervallo Doppio Tocco", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15, 30).forEach { sec ->
                            val isSelected = skipIntervalSeconds == sec
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accentRed else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, if (isSelected) accentRed else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .iosSpringPress {
                                        skipIntervalSeconds = sec
                                        prefs.saveSkipStep(sec)
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sec}s",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === BOOST VELOCITÀ (long-press metà destra) ===
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Boost Long-Press (metà dx)",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentRed.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f))
                        ) {
                            Text(
                                text = String.format("%.1fx", boostSpeed),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = boostSpeed,
                        onValueChange = { sp ->
                            boostSpeed = (kotlin.math.round(sp * 10) / 10f).coerceIn(1.25f, 4.0f)
                        },
                        onValueChangeFinished = {
                            prefs.setBoostSpeed(boostSpeed)
                        },
                        valueRange = 1.25f..4.0f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = accentRed,
                            activeTrackColor = accentRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1.5f, 2.0f, 2.5f, 3.0f, 4.0f).forEach { sp ->
                            val isSel = kotlin.math.abs(boostSpeed - sp) < 0.05f
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) accentRed else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, if (isSel) accentRed else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .iosSpringPress {
                                        boostSpeed = sp
                                        prefs.setBoostSpeed(sp)
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${sp}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === TIMESTAMP MEDIASET & AUTO-LEARNING ===
                    Text(
                        "Timestamp Sigla Mediaset",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Attuale fine sigla: ${formatTime(currentOpeningEnd)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentRed.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f)),
                            modifier = Modifier
                                .weight(1.3f)
                                .iosSpringPress {
                                    val now = exoPlayer.currentPosition
                                    prefs.saveCustomTimestamp(episodeNumber, "opening_end", now)
                                    currentOpeningEnd = now
                                    Toast.makeText(
                                        context,
                                        "📌 Salvato: fine sigla a ${formatTime(now)} per Ep. $episodeNumber!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "📌 Segna qui fine sigla",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .weight(1f)
                                .iosSpringPress {
                                    prefs.saveCustomTimestamp(episodeNumber, "opening_end", -1L)
                                    val defaultTs = MediasetTimestampProvider.getForEpisode(context, episodeNumber)
                                    currentOpeningEnd = defaultTs.openingEndMs
                                    Toast.makeText(
                                        context,
                                        "Timestamp ripristinato ai valori Mediaset",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ripristina",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // FLUID IN-PLAYER LANGUAGE SWITCHING OVERLAY
        AnimatedVisibility(
            visible = isSwitchingLanguage,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF0101018),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = accentRed,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cambio lingua in corso...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
