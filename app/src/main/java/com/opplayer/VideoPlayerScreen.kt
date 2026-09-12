package com.opplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
    initialPositionMs: Long = 0L,
    isInPipMode: Boolean = false,
    isAdvancingNext: Boolean = false,
    onEnterPip: () -> Unit = {},
    onDownloadRequested: (String, Int) -> Unit,
    onNextEpisode: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { PlaybackPreferences(context) }

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
    var currentPos by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    var skipFeedbackText by remember { mutableStateOf<String?>(null) }
    var skipFeedbackIsForward by remember { mutableStateOf(true) }

    val specularBorder = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))
    )
    val accentRed = Color(0xFFFF2A42)

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            prefs.saveSpeed(playbackSpeed)
            prefs.saveSkipStep(skipIntervalSeconds)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
        if (initialPositionMs > 0L) {
            exoPlayer.seekTo(initialPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDraggingSlider) {
                currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                onPositionChanged(currentPos)

                // Mark episode as watched if user reaches 21 minutes (ignoring outro) or 85% of total
                val isPast21Min = currentPos >= 21 * 60 * 1000L
                val isPast85Percent = totalDuration > 30_000L && (currentPos.toDouble() / totalDuration >= 0.85)
                if (isPast21Min || isPast85Percent) {
                    prefs.markEpisodeWatched(episodeNumber, true)
                }
            }
            isPlaying = exoPlayer.isPlaying
            delay(400)
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

    // Auto-hide skip feedback
    LaunchedEffect(skipFeedbackText) {
        if (skipFeedbackText != null) {
            delay(750)
            skipFeedbackText = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
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

        // Tap & Double Tap Gesture Overlay (No volume/brightness gestures)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(skipIntervalSeconds) {
                    detectTapGestures(
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
                            } else if (offset.x > size.width * 0.60f) {
                                val target = (exoPlayer.currentPosition + skipMs).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(target)
                                currentPos = target
                                skipFeedbackText = "+${skipIntervalSeconds}s"
                                skipFeedbackIsForward = true
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                            }
                        }
                    )
                }
        )

        // DOUBLE-TAP SKIP ANIMATED FEEDBACK BADGE
        skipFeedbackText?.let { text ->
            Surface(
                modifier = Modifier
                    .align(if (skipFeedbackIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 60.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.78f),
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
                                // Sigla Ita skip (3m 20s = 200s)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, specularBorder),
                                    modifier = Modifier.iosSpringPress {
                                        val target = (exoPlayer.currentPosition + 200_000L).coerceAtMost(totalDuration)
                                        exoPlayer.seekTo(target)
                                        currentPos = target
                                        skipFeedbackText = "Sigla Saltata (+3m 20s)"
                                        skipFeedbackIsForward = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sigla (3m 20s)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Recap skip (4m 30s = 270s)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accentRed.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, accentRed.copy(alpha = 0.50f)),
                                    modifier = Modifier.iosSpringPress {
                                        val target = (exoPlayer.currentPosition + 270_000L).coerceAtMost(totalDuration)
                                        exoPlayer.seekTo(target)
                                        currentPos = target
                                        skipFeedbackText = "Recap Saltato (+4m 30s)"
                                        skipFeedbackIsForward = true
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

                                // Next Episode Button
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
                                        Text(
                                            text = "Ep. ${episodeNumber + 1}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                }
            }
        }
    }
}
