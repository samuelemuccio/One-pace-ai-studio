package com.opplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
        .graphicsLayer { scaleX = scale; scaleY = scale }
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
    initialPositionMs: Long = 0L,
    isInPipMode: Boolean = false,
    isAdvancingNext: Boolean = false,
    onDownloadRequested: (String, Int) -> Unit,
    onNextEpisode: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { PlaybackPreferences(context) }

    val episodeNumber = remember(currentWebUrl) { OnePieceHelper.extractEpisodeNumber(currentWebUrl) }
    val epType = remember(episodeNumber) { OnePieceHelper.getEpisodeType(episodeNumber) }
    val timings = remember(episodeNumber) { OnePieceHelper.getTimingsForEpisode(episodeNumber) }

    var skipIntervalSeconds by remember { mutableIntStateOf(prefs.getSkipStep()) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getSpeed()) }
    var boostMultiplier by remember { mutableFloatStateOf(prefs.getBoostMultiplier()) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var isZoomToFill by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var skipFeedbackText by remember { mutableStateOf<String?>(null) }
    var skipFeedbackIsForward by remember { mutableStateOf(true) }
    var skipFeedbackId by remember { mutableLongStateOf(0L) }
    var isBoosting by remember { mutableStateOf(false) }
    var hasMarkedWatched by remember { mutableStateOf(false) }

    val specularBorder = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f))
    )
    val accentRed = Color(0xFFFF2A42)
    val glassWhite = Color.White.copy(alpha = 0.14f)

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            prefs.saveSpeed(playbackSpeed)
            prefs.saveSkipStep(skipIntervalSeconds)
            prefs.saveBoostMultiplier(boostMultiplier)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ExoPlayer con headers anti-403
    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://onepiecepower.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
            )
        val mediaFactory = DefaultMediaSourceFactory(httpFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 1_500, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playbackParameters = PlaybackParameters(playbackSpeed)
                PlayerHolder.player = this
                PlayerHolder.currentVideoUrl = videoUrl
                PlayerHolder.currentEpisode = episodeNumber
            }
    }

    LaunchedEffect(videoUrl) {
        try { exoPlayer.stop() } catch (_: Exception) {}
        try { exoPlayer.clearMediaItems() } catch (_: Exception) {}
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
        if (initialPositionMs > 0L) exoPlayer.seekTo(initialPositionMs)
        exoPlayer.playWhenReady = true
    }

    // Loop aggiornamento posizione
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            if (!isDraggingSlider) {
                currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                totalDuration = if (dur > 0) dur else 0L
                onPositionChanged(currentPos)

                // Salva posizione ogni ~2s (ogni 4 tick da 500ms)
                if (tick % 4 == 0) {
                    prefs.savePositionForEpisode(episodeNumber, currentPos)
                }

                // Auto-marca come visto a 22:30
                if (!hasMarkedWatched && currentPos >= OnePieceHelper.WATCHED_THRESHOLD_MS) {
                    hasMarkedWatched = true
                    prefs.markEpisodeWatched(episodeNumber, true)
                }
            }
            isPlaying = exoPlayer.isPlaying
            tick++
            delay(500)
        }
    }

    // Salva posizione quando l'app va in background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                try {
                    val pos = exoPlayer.currentPosition
                    prefs.savePositionForEpisode(episodeNumber, pos)
                    prefs.saveLastPlayback(currentWebUrl, episodeNumber, pos)
                } catch (_: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val pos = exoPlayer.currentPosition
                prefs.savePositionForEpisode(episodeNumber, pos)
                prefs.saveLastPlayback(currentWebUrl, episodeNumber, pos)
            } catch (_: Exception) {}
            exoPlayer.release()
            PlayerHolder.player = null
        }
    }

    // Auto-hide controlli
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4500)
            if (!showSettingsPanel && !isDraggingSlider) showControls = false
        }
    }

    // Fix: il badge "+Xs/-Xs" rimaneva impresso.
    // Uso un id incrementale: ogni nuovo tap cancella il precedente timer.
    LaunchedEffect(skipFeedbackId) {
        if (skipFeedbackText != null) {
            delay(750)
            skipFeedbackText = null
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
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { pv ->
                pv.resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture overlay: tap, double-tap, long-press boost
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(skipIntervalSeconds, boostMultiplier, totalDuration) {
                    detectTapGestures(
                        onTap = {
                            if (showSettingsPanel) showSettingsPanel = false
                            else showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val skipMs = skipIntervalSeconds * 1000L
                            if (offset.x < size.width * 0.40f) {
                                val target = (exoPlayer.currentPosition - skipMs).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPos = target
                                skipFeedbackText = "-${skipIntervalSeconds}s"
                                skipFeedbackIsForward = false
                                skipFeedbackId++
                            } else if (offset.x > size.width * 0.60f) {
                                val target = (exoPlayer.currentPosition + skipMs)
                                    .coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                                exoPlayer.seekTo(target)
                                currentPos = target
                                skipFeedbackText = "+${skipIntervalSeconds}s"
                                skipFeedbackIsForward = true
                                skipFeedbackId++
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                            }
                        },
                        onPress = { offset ->
                            // Long-press lato destro → boost
                            if (offset.x > size.width * 0.60f) {
                                val prevSpeed = exoPlayer.playbackParameters.speed
                                try {
                                    exoPlayer.setPlaybackSpeed(boostMultiplier)
                                    isBoosting = true
                                } catch (_: Exception) {}
                                tryAwaitRelease()
                                try {
                                    exoPlayer.setPlaybackSpeed(prevSpeed)
                                    isBoosting = false
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
        )

        // Badge boost
        AnimatedVisibility(
            visible = isBoosting,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentRed.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${boostMultiplier}x BOOST",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Feedback skip (fix: ora usa id, non si blocca)
        skipFeedbackText?.let { text ->
            Surface(
                modifier = Modifier
                    .align(if (skipFeedbackIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 60.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (skipFeedbackIsForward) Icons.Default.FastForward
                        else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Overlay caricamento prossimo episodio
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
                        CircularProgressIndicator(color = accentRed, modifier = Modifier.size(42.dp), strokeWidth = 3.5.dp)
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

        // Controlli
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.90f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = glassWhite,
                                border = BorderStroke(1.dp, specularBorder),
                                modifier = Modifier.iosSpringPress(onClick = onClose)
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Episodio $episodeNumber", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(OnePieceHelper.getSagaForEpisode(episodeNumber).name, color = Color.LightGray, fontSize = 11.sp)
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
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isZoomToFill) accentRed.copy(alpha = 0.35f) else glassWhite,
                                border = BorderStroke(1.dp, if (isZoomToFill) accentRed else Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.iosSpringPress { isZoomToFill = !isZoomToFill }
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
                                    color = glassWhite,
                                    border = BorderStroke(1.dp, specularBorder),
                                    modifier = Modifier.iosSpringPress { onDownloadRequested(videoUrl, episodeNumber) }
                                ) {
                                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Download, contentDescription = "Scarica", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = glassWhite,
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
                                color = if (showSettingsPanel) accentRed.copy(alpha = 0.35f) else glassWhite,
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

                // Bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.96f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                        exoPlayer.seekTo(sliderDragPosition.toLong())
                                        currentPos = sliderDragPosition.toLong()
                                        isDraggingSlider = false
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
                            Text(formatTime(totalDuration), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Skip opening (dinamico per fascia episodio)
                                val introSec = (timings.introEndMs / 1000).toInt()
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = glassWhite,
                                    border = BorderStroke(1.dp, specularBorder),
                                    modifier = Modifier.iosSpringPress {
                                        val target = (exoPlayer.currentPosition + timings.introEndMs)
                                            .coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                                        exoPlayer.seekTo(target)
                                        currentPos = target
                                        skipFeedbackText = "Opening Saltata (+${introSec / 60}m ${introSec % 60}s)"
                                        skipFeedbackIsForward = true
                                        skipFeedbackId++
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Opening",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Skip recap
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accentRed.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f)),
                                    modifier = Modifier.iosSpringPress {
                                        val recapDelta = (timings.recapEndMs - exoPlayer.currentPosition).coerceAtLeast(0L)
                                        val target = (exoPlayer.currentPosition + recapDelta)
                                            .coerceAtMost(if (totalDuration > 0) totalDuration else Long.MAX_VALUE)
                                        exoPlayer.seekTo(target)
                                        currentPos = target
                                        skipFeedbackText = "Recap Saltato"
                                        skipFeedbackIsForward = true
                                        skipFeedbackId++
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FastForward, contentDescription = null, tint = accentRed, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Recap", color = accentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accentRed,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.iosSpringPress(onClick = onNextEpisode)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Ep. ${episodeNumber + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pannello impostazioni player
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
                    .width(340.dp),
                color = Color(0xF014141E),
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
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

                    // Velocità riproduzione
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
                                text = String.format(Locale.US, "%.2fx", playbackSpeed),
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

                    Spacer(modifier = Modifier.height(24.dp))

                    // Boost multiplier (long-press destro)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Boost Long-Press (destra)", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tieni premuto a destra per accelerare", color = Color.Gray, fontSize = 10.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentRed.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f))
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2fx", boostMultiplier),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = boostMultiplier,
                        onValueChange = { v ->
                            boostMultiplier = (kotlin.math.round(v * 4) / 4f).coerceIn(1.25f, 4.0f)
                            prefs.saveBoostMultiplier(boostMultiplier)
                        },
                        valueRange = 1.25f..4f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = accentRed,
                            activeTrackColor = accentRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

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

                    Text("Tempistiche Sigla (Ep. $episodeNumber)", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, specularBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Opening termina a: ${formatTime(timings.introEndMs)}", color = Color.White, fontSize = 11.sp)
                            Text("Recap termina a: ${formatTime(timings.recapEndMs)}", color = Color.White, fontSize = 11.sp)
                            Text("Ending: ${timings.outroStartMs?.let { formatTime(it) } ?: "assente"}", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// Import mancante per verticalScroll
private val rememberScrollState = androidx.compose.foundation.rememberScrollState
