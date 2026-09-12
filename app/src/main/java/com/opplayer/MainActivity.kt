package com.opplayer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class DownloadedFileItem(
    val episodeNumber: Int,
    val file: File,
    val sizeMb: String
)

@Composable
fun Modifier.iosSpringClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "springClick"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun AnimatedStreakBadge(
    streak: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streakFlame")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x33FF3B30),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF3B30).copy(alpha = glowAlpha),
                    Color(0xFFFF9500).copy(alpha = glowAlpha)
                )
            )
        ),
        modifier = modifier.iosSpringClick(onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Streak attiva",
                tint = Color(0xFFFF3B30),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = flameScale
                        scaleY = flameScale
                    }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$streak ${if (streak == 1) "giorno" else "gg"}",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun EpisodeCheckMark(
    isWatched: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isWatched) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 380f
        ),
        label = "checkScale"
    )
    val checkColor by animateColorAsState(
        targetValue = if (isWatched) Color(0xFF30D15B) else Color.Transparent,
        animationSpec = tween(180),
        label = "checkColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isWatched) Color(0xFF30D15B) else Color.White.copy(alpha = 0.28f),
        animationSpec = tween(180),
        label = "checkBorder"
    )

    Box(
        modifier = modifier
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
                .background(checkColor)
                .border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isWatched,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(140)),
                exit = scaleOut(tween(100)) + fadeOut(tween(100))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isWatched) "Visto" else "Non visto",
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun LogPoseCompassIcon(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logPoseNeedle")
    val needleAngle by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needleOscillation"
    )

    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val centerOffset = androidx.compose.ui.geometry.Offset(cx, cy)
            val radius = this.size.minDimension / 2f

            // Outer Brass / Gold Metallic Bezel
            val goldBezelBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFFDF00),
                    Color(0xFFD4AF37),
                    Color(0xFF8B6508),
                    Color(0xFFFFDF00),
                    Color(0xFFD4AF37),
                    Color(0xFFFFDF00)
                ),
                center = centerOffset
            )
            drawCircle(
                brush = goldBezelBrush,
                radius = radius,
                center = centerOffset
            )

            // Inner dark oceanic glass dome
            val innerGlassRadius = radius * 0.82f
            val glassBrush = Brush.radialGradient(
                listOf(
                    Color(0xFF1E3A5F),
                    Color(0xFF0F1E32),
                    Color(0xFF070D18)
                ),
                center = centerOffset,
                radius = innerGlassRadius
            )
            drawCircle(
                brush = glassBrush,
                radius = innerGlassRadius,
                center = centerOffset
            )

            // Glass specular curved highlight arc (top left)
            drawArc(
                color = Color.White.copy(alpha = 0.55f),
                startAngle = 195f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - innerGlassRadius * 0.75f, cy - innerGlassRadius * 0.75f),
                size = androidx.compose.ui.geometry.Size(innerGlassRadius * 1.5f, innerGlassRadius * 1.5f),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Rotating Log Pose magnetic needle
            val baseAngle = -35f + needleAngle
            drawContext.canvas.save()
            drawContext.transform.rotate(baseAngle, centerOffset)

            val needleHalfWidth = 1.8.dp.toPx()
            val needleLen = innerGlassRadius * 0.85f

            // North Pointer (Ruby Red)
            val northPath = Path().apply {
                moveTo(cx, cy - needleLen.toFloat())
                lineTo(cx + needleHalfWidth.toFloat(), cy)
                lineTo(cx - needleHalfWidth.toFloat(), cy)
                close()
            }
            drawPath(northPath, color = Color(0xFFFF2A42))

            // South Pointer (Silver/White)
            val southPath = Path().apply {
                moveTo(cx, cy + needleLen.toFloat())
                lineTo(cx + needleHalfWidth.toFloat(), cy)
                lineTo(cx - needleHalfWidth.toFloat(), cy)
                close()
            }
            drawPath(southPath, color = Color(0xFFE2E8F0))

            // Center Brass Pivot Rivet
            drawCircle(
                color = Color(0xFFFFD700),
                radius = 2.0.dp.toPx(),
                center = centerOffset
            )
            drawCircle(
                color = Color(0xFF221105),
                radius = 0.8.dp.toPx(),
                center = centerOffset
            )

            drawContext.canvas.restore()

            // Outer fine specular rim stroke
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = radius - 0.5.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
    }
}

class MainActivity : ComponentActivity() {

    private var isInPipModeState = mutableStateOf(false)
    private var isPlayerActive = false

    fun enterPipMode() {
        if (isPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(true)
                    }
                }
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }

    private fun getDownloadedFilesList(): List<DownloadedFileItem> {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return emptyList()
        val files = dir.listFiles { _, name -> name.startsWith("OnePiece_Ep_") && name.endsWith(".mp4") } ?: return emptyList()
        return files.mapNotNull { f ->
            val num = Regex("""OnePiece_Ep_(\d+)\.mp4""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (num != null) {
                val mb = String.format("%.1f MB", f.length() / (1024f * 1024f))
                DownloadedFileItem(num, f, mb)
            } else null
        }.sortedBy { it.episodeNumber }
    }

    private fun downloadEpisodeOffline(videoUrl: String, epNumber: Int) {
        val targetDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return
        val targetFile = File(targetDir, "OnePiece_Ep_$epNumber.mp4")

        if (targetFile.exists() && targetFile.length() > 1_000_000L) {
            Toast.makeText(this, "Episodio $epNumber già presente in locale! 💾", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(videoUrl)).apply {
                setTitle("One Piece - Ep. $epNumber")
                setDescription("Download episodio offline")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(targetFile))
                setAllowedOverMetered(true)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download Episodio $epNumber avviato 📥", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore avvio download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidVideoStream(url: String): Boolean {
        val lower = url.lowercase()
        val isMedia = lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mpd")
        if (!isMedia) return false

        val adKeywords = listOf(
            "googleads", "doubleclick", "adservice", "traffic", "banner",
            "popunder", "adsystem", "adnxs", "taboola", "outbrain",
            "analytics", "tracker", "promo", "pre-roll", "adserver",
            "syndication", "bidder", "creative"
        )
        return adKeywords.none { lower.contains(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Promemoria Rotta Maggiore"
            val descriptionText = "Notifiche per mantenere la serie e continuare gli episodi di One Piece"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("op_streak_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showEpisodeReminderNotification(currentEpisode: Int, streakDays: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val saga = OnePieceHelper.getSagaForEpisode(currentEpisode)
        val builder = NotificationCompat.Builder(this, "op_streak_channel")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("🏴‍☠️ Salpa di nuovo! Serie: $streakDays gg")
            .setContentText("Luffy ti aspetta all'Episodio $currentEpisode (${saga.name})!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Mantieni la tua streak pirata attiva ($streakDays giorni)! Prosegui l'avventura all'Episodio $currentEpisode nella saga '${saga.name}'."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        StreakReminderReceiver.scheduleDailyReminder(this)
        val prefs = PlaybackPreferences(this)

        setContent {
            MaterialTheme {
                var isOpeningSplashVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(950L)
                    isOpeningSplashVisible = false
                }

                val savedEpisode = remember { prefs.getLastEpisode() }
                val defaultStartUrl = OnePieceHelper.buildEpisodeUrl("", savedEpisode)
                val savedUrl = remember { prefs.getLastUrl() ?: defaultStartUrl }
                val savedPosition = remember { prefs.getLastPositionMs() }

                var currentEpisodeNumber by remember { mutableIntStateOf(savedEpisode) }
                var currentWebUrl by remember { mutableStateOf(savedUrl) }
                var activeVideoUrl by remember { mutableStateOf<String?>(null) }
                var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }
                var startFromPosition by remember { mutableLongStateOf(0L) }
                var isAutoAdvancing by remember { mutableStateOf(false) }

                // Dynamic loading states
                var isResolvingStream by remember { mutableStateOf(false) }
                var resolvingEpisodeNumber by remember { mutableIntStateOf(savedEpisode) }
                var isPlayerAdvancingNext by remember { mutableStateOf(false) }

                var currentTab by remember { mutableIntStateOf(0) }
                var isBrowserOpen by remember { mutableStateOf(false) }
                var watchedEpisodes by remember { mutableStateOf(prefs.getWatchedEpisodes()) }
                var favoriteEpisodes by remember { mutableStateOf(prefs.getFavoriteEpisodes()) }
                var downloadedList by remember { mutableStateOf(getDownloadedFilesList()) }

                // Gamification & Rotta State
                var dailyStreak by remember { mutableIntStateOf(prefs.getStreak()) }
                val pirateRank by remember(watchedEpisodes) { derivedStateOf { OnePieceHelper.getPirateRank(watchedEpisodes.size) } }
                var selectedSagaIndex by remember {
                    mutableIntStateOf(
                        OnePieceHelper.SAGAS.indexOfFirst { savedEpisode in it.range }.coerceAtLeast(0)
                    )
                }
                var selectedFilterTag by remember { mutableStateOf("Tutti") }

                // Dialogs
                var showImportDialog by remember { mutableStateOf(false) }
                var importInputText by remember { mutableStateOf("") }
                var selectedEpisodeForDetail by remember { mutableStateOf<Int?>(null) }
                var showQuickJumpDialog by remember { mutableStateOf(false) }
                var quickJumpInput by remember { mutableStateOf("") }
                var showClearHistoryConfirmDialog by remember { mutableStateOf(false) }
                var showStreakReminderDialog by remember { mutableStateOf(false) }
                var showCloudSyncDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                val downloadManagerHelper = remember { DownloadManagerHelper(this@MainActivity) }

                // Cloud & Persistent Vault Manager
                val cloudSyncManager = remember { CloudSyncManager(this@MainActivity) }
                var cloudUserEmail by remember { mutableStateOf(cloudSyncManager.getUserEmail()) }
                var cloudLastSyncText by remember { mutableStateOf(cloudSyncManager.getLastSyncDateFormatted()) }
                var isAutoSyncEnabled by remember { mutableStateOf(cloudSyncManager.isAutoSyncEnabled()) }
                val coroutineScope = rememberCoroutineScope()

                // Auto-restore on startup if app was reinstalled or cache cleared
                LaunchedEffect(Unit) {
                    val restored = cloudSyncManager.checkAutoRestoreOnStartup(prefs)
                    if (restored) {
                        watchedEpisodes = prefs.getWatchedEpisodes()
                        favoriteEpisodes = prefs.getFavoriteEpisodes()
                        currentEpisodeNumber = prefs.getLastEpisode()
                        dailyStreak = prefs.getStreak()
                        cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                        Toast.makeText(this@MainActivity, "Salvataggio ripristinato automaticamente dal dispositivo! 🏴‍☠️", Toast.LENGTH_LONG).show()
                    }
                }

                // Continuous persistent auto-save to survive uninstallation
                LaunchedEffect(watchedEpisodes, currentEpisodeNumber, dailyStreak) {
                    if (isAutoSyncEnabled) {
                        val json = OnePieceHelper.exportToJson(
                            watched = watchedEpisodes,
                            favorites = favoriteEpisodes,
                            lastEp = currentEpisodeNumber,
                            lastPos = savedPosition,
                            streak = dailyStreak
                        )
                        cloudSyncManager.saveLocalPersistentVault(json)
                        cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                    }
                }

                // Notification permission launcher for Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        showEpisodeReminderNotification(currentEpisodeNumber, dailyStreak)
                        Toast.makeText(this@MainActivity, "Promemoria attivato con successo! 🏴‍☠️", Toast.LENGTH_SHORT).show()
                    }
                }

                // Check and trigger reminder if enabled
                LaunchedEffect(dailyStreak) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            showEpisodeReminderNotification(currentEpisodeNumber, dailyStreak)
                        }
                    } else {
                        showEpisodeReminderNotification(currentEpisodeNumber, dailyStreak)
                    }
                }

                val specularBorder = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
                )
                val accentRed = Color(0xFFFF2A42)
                val goldAccent = Color(0xFFFFD700)

                isPlayerActive = activeVideoUrl != null

                LaunchedEffect(activeVideoUrl) {
                    if (activeVideoUrl != null) {
                        webViewInstance?.onPause()
                        webViewInstance?.evaluateJavascript(
                            """
                            (function() {
                                var media = document.querySelectorAll('video, audio');
                                for (var i = 0; i < media.length; i++) {
                                    media[i].pause();
                                    media[i].muted = true;
                                }
                            })();
                            """.trimIndent(), null
                        )
                    } else {
                        webViewInstance?.onResume()
                    }
                }

                BackHandler(enabled = true) {
                    when {
                        activeVideoUrl != null -> {
                            activeVideoUrl = null
                            isPlayerAdvancingNext = false
                        }
                        selectedEpisodeForDetail != null -> {
                            selectedEpisodeForDetail = null
                        }
                        showQuickJumpDialog -> {
                            showQuickJumpDialog = false
                        }
                        showImportDialog -> {
                            showImportDialog = false
                        }
                        showClearHistoryConfirmDialog -> {
                            showClearHistoryConfirmDialog = false
                        }
                        isBrowserOpen -> {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                isBrowserOpen = false
                            }
                        }
                        currentTab != 0 -> {
                            currentTab = 0
                        }
                        webViewInstance?.canGoBack() == true -> {
                            webViewInstance?.goBack()
                        }
                        else -> {
                            finish()
                        }
                    }
                }

                // UNIFIED ROBUST STREAM LAUNCHER
                fun playEpisode(targetEp: Int, fromPos: Long = 0L) {
                    currentEpisodeNumber = targetEp
                    startFromPosition = fromPos
                    val epUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, targetEp)
                    currentWebUrl = epUrl

                    val updatedStreak = prefs.recordWatchForStreak()
                    dailyStreak = updatedStreak

                    // 1. Controlla subito se c'è un file offline scaricato in locale
                    val localFile = downloadedList.firstOrNull { it.episodeNumber == targetEp }
                    if (localFile != null && localFile.file.exists()) {
                        activeVideoUrl = localFile.file.absolutePath
                        prefs.saveLastPlayback(localFile.file.absolutePath, targetEp, fromPos)
                        Toast.makeText(this@MainActivity, "Avvio da memoria locale 💾", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // 2. Se detectedVideoUrl corrisponde già all'episodio
                    if (detectedVideoUrl != null && isValidVideoStream(detectedVideoUrl!!) && currentWebUrl.contains("pagine/$targetEp")) {
                        activeVideoUrl = detectedVideoUrl
                        prefs.saveLastPlayback(epUrl, targetEp, fromPos)
                        return
                    }

                    // 3. Risoluzione rapida in parallelo: sia StreamExtractor che WebView!
                    isResolvingStream = true
                    resolvingEpisodeNumber = targetEp
                    isAutoAdvancing = true

                    webViewInstance?.loadUrl(epUrl)

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val directStream = StreamExtractor.resolveStreamUrl(epUrl)
                            if (!directStream.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    detectedVideoUrl = directStream
                                    activeVideoUrl = directStream
                                    isResolvingStream = false
                                    isAutoAdvancing = false
                                    prefs.saveLastPlayback(epUrl, targetEp, fromPos)
                                }
                                return@launch
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Timeout di sicurezza
                        delay(9000)
                        withContext(Dispatchers.Main) {
                            if (isResolvingStream && activeVideoUrl == null) {
                                isResolvingStream = false
                                Toast.makeText(
                                    this@MainActivity,
                                    "Caricamento in corso dalla sorgente web...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                Scaffold(
                    containerColor = Color(0xFF070709),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF070709))
                    ) {
                        // Background WebView for scraping & video detection
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.setSupportMultipleWindows(false)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        settings.safeBrowsingEnabled = false
                                    }
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.databaseEnabled = true
                                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            url?.let {
                                                if (it.contains("onepiecepower.com") && it.contains("pagine/")) {
                                                    currentWebUrl = it
                                                    val ep = OnePieceHelper.extractEpisodeNumber(it)
                                                    currentEpisodeNumber = ep
                                                    prefs.saveLastPlayback(it, ep, 0L)
                                                }
                                            }

                                            view?.evaluateJavascript(
                                                """
                                                (function() {
                                                    function triggerPlay() {
                                                        var badSelectors = ['[class*="modal"]', '[id*="modal"]', '[class*="popup"]', '[id*="popup"]', '[id*="vpn"]', '.toggl'];
                                                        badSelectors.forEach(function(s) {
                                                            document.querySelectorAll(s).forEach(function(el) { el.remove(); });
                                                        });

                                                        var elements = document.querySelectorAll('div, a, button, span, img');
                                                        for (var i = 0; i < elements.length; i++) {
                                                            var el = elements[i];
                                                            var t = (el.innerText || el.textContent || '').toUpperCase();
                                                            if (t.indexOf('CLICCA PER AVVIARE') !== -1 || t.indexOf('AVVIA EPISODIO') !== -1) {
                                                                el.click();
                                                                if (el.parentElement) el.parentElement.click();
                                                                break;
                                                            }
                                                        }

                                                        document.querySelectorAll('.jw-display-icon-display, .vjs-big-play-button, .play-button, video').forEach(function(b) {
                                                            try { b.click(); } catch(e){}
                                                        });
                                                    }
                                                    triggerPlay();
                                                    setTimeout(triggerPlay, 500);
                                                    setTimeout(triggerPlay, 1200);
                                                })();
                                                """.trimIndent(), null
                                            )
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val reqUrl = request?.url?.toString() ?: return false
                                            return !reqUrl.contains("onepiecepower.com")
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val reqUrl = request?.url?.toString() ?: return null
                                            if (isValidVideoStream(reqUrl)) {
                                                view?.post {
                                                    detectedVideoUrl = reqUrl
                                                    if (isAutoAdvancing || isResolvingStream || isPlayerAdvancingNext) {
                                                        activeVideoUrl = reqUrl
                                                        isAutoAdvancing = false
                                                        isResolvingStream = false
                                                        isPlayerAdvancingNext = false
                                                    }
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }
                                    loadUrl(currentWebUrl)
                                    webViewInstance = this
                                }
                            },
                            modifier = if (isBrowserOpen) {
                                Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(top = 56.dp)
                                    .navigationBarsPadding()
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )

                        if (!isBrowserOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF070709))
                            )

                            // FLUID TAB TRANSITIONS
                            AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally(
                                        initialOffsetX = { it / 3 },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 3 },
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        )
                                } else {
                                    (slideInHorizontally(
                                        initialOffsetX = { -it / 3 },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                targetOffsetX = { it / 3 },
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        )
                                }
                            },
                            label = "tabTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { tab ->
                            when (tab) {
                                0 -> {
                                    // TAB 0: CINEMA HUB
                                    val currentSaga = OnePieceHelper.getSagaForEpisode(currentEpisodeNumber)
                                    val epType = OnePieceHelper.getEpisodeType(currentEpisodeNumber)
                                    val sagaEpisodes = remember(currentSaga) { currentSaga.range.toList() }
                                    val targetIndex = remember(currentEpisodeNumber, sagaEpisodes) {
                                        sagaEpisodes.indexOf(currentEpisodeNumber).coerceAtLeast(0)
                                    }
                                    val carouselState = rememberLazyListState()
                                    val isHeroFavorited = favoriteEpisodes.contains(currentEpisodeNumber)

                                    val upcomingEpisodes = remember(sagaEpisodes, currentEpisodeNumber) {
                                        val nextInSaga = sagaEpisodes.filter { it > currentEpisodeNumber }
                                        if (nextInSaga.isNotEmpty()) {
                                            nextInSaga.take(12)
                                        } else {
                                            ((currentEpisodeNumber + 1)..minOf(currentEpisodeNumber + 12, OnePieceHelper.TOTAL_AIRING_EPISODES)).toList()
                                        }
                                    }

                                    LaunchedEffect(targetIndex) {
                                        carouselState.scrollToItem(targetIndex)
                                    }

                                    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0x35FF2A42),
                                                        Color(0xFF070709)
                                                    ),
                                                    startY = 0f,
                                                    endY = 700f
                                                )
                                            )
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 18.dp),
                                            contentPadding = PaddingValues(top = statusBarTop + 14.dp, bottom = 120.dp)
                                        ) {
                                            item {
                                                // Header with Title & Action Controls (Streak, Search, In-App Browser)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "ONE PIECE • CINEMA",
                                                            color = accentRed,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 11.sp,
                                                            letterSpacing = 2.sp
                                                        )
                                                        Text(
                                                            text = "Rotta Maggiore",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 24.sp
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        // Animated Flame Streak Badge
                                                        AnimatedStreakBadge(
                                                            streak = dailyStreak,
                                                            onClick = { showStreakReminderDialog = true }
                                                        )

                                                        // Quick Search Button (Independent 36dp touch target)
                                                        IconButton(
                                                            onClick = { showQuickJumpDialog = true },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.12f))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Search,
                                                                contentDescription = "Cerca Episodio",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(19.dp)
                                                            )
                                                        }

                                                        // In-App Browser Icon (Independent 36dp touch target)
                                                        IconButton(
                                                            onClick = { isBrowserOpen = true },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.12f))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Language,
                                                                contentDescription = "Sito Web Browser",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(19.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(18.dp))

                                                // HERO EPISODE GLASS CARD (iOS 17 Liquid Glass)
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .iosSpringClick {
                                                            playEpisode(currentEpisodeNumber, savedPosition)
                                                        },
                                                    shape = RoundedCornerShape(28.dp),
                                                    color = Color(0xDB181822),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    shadowElevation = 16.dp
                                                ) {
                                                    Column(modifier = Modifier.padding(22.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = currentSaga.name.uppercase(),
                                                                    color = goldAccent,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    letterSpacing = 1.2.sp
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
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
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    val isFav = prefs.toggleFavorite(currentEpisodeNumber)
                                                                    favoriteEpisodes = prefs.getFavoriteEpisodes()
                                                                    Toast.makeText(
                                                                        this@MainActivity,
                                                                        if (isFav) "Ep. $currentEpisodeNumber salvato tra i preferiti! ⭐" else "Rimosso dai preferiti",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                },
                                                                modifier = Modifier.size(48.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isHeroFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                                                    contentDescription = "Preferito",
                                                                    tint = if (isHeroFavorited) goldAccent else Color.Gray,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        Text(
                                                            text = "Episodio $currentEpisodeNumber",
                                                            color = Color.White,
                                                            fontSize = 28.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = currentSaga.description,
                                                            color = Color(0xFFA0A0AA),
                                                            fontSize = 13.sp,
                                                            lineHeight = 18.sp
                                                        )

                                                        Spacer(modifier = Modifier.height(18.dp))

                                                        // Action Row: Play + Direct Offline Download
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Surface(
                                                                shape = RoundedCornerShape(18.dp),
                                                                color = accentRed,
                                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                                shadowElevation = 10.dp,
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .iosSpringClick {
                                                                        playEpisode(currentEpisodeNumber, savedPosition)
                                                                    }
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(vertical = 15.dp),
                                                                    horizontalArrangement = Arrangement.Center,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(
                                                                        text = if (savedPosition > 10_000L) "Riprendi ${formatTime(savedPosition)}" else "Guarda Adesso",
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 15.sp
                                                                    )
                                                                }
                                                            }

                                                            // Quick Direct Download Button (Icon only)
                                                            Surface(
                                                                shape = RoundedCornerShape(18.dp),
                                                                color = Color.White.copy(alpha = 0.08f),
                                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                                                modifier = Modifier
                                                                    .size(54.dp)
                                                                    .iosSpringClick {
                                                                        val preferredQuality = prefs.getPreferredDownloadQuality()
                                                                        coroutineScope.launch {
                                                                            downloadManagerHelper.startDownloadForEpisode(currentEpisodeNumber, preferredQuality)
                                                                        }
                                                                        Toast.makeText(
                                                                            this@MainActivity,
                                                                            "Download Ep. $currentEpisodeNumber ($preferredQuality) avviato! 📥",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.fillMaxSize()
                                                                ) {
                                                                    Icon(Icons.Default.Download, contentDescription = "Scarica episodio", tint = Color.White, modifier = Modifier.size(22.dp))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(26.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Episodi ${currentSaga.name}",
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${currentSaga.range.count()} ep.",
                                                        color = Color.Gray,
                                                        fontSize = 12.sp
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(14.dp))

                                                // Smooth Horizontal Carousel (Apple Squircle G2)
                                                LazyRow(
                                                    state = carouselState,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(sagaEpisodes) { ep ->
                                                        val isSelected = ep == currentEpisodeNumber
                                                        val isWatched = watchedEpisodes.contains(ep)
                                                        val isFav = favoriteEpisodes.contains(ep)
                                                        val itemType = OnePieceHelper.getEpisodeType(ep)

                                                        Surface(
                                                            shape = RoundedCornerShape(22.dp),
                                                            color = if (isSelected) Color(0xFF181218) else Color(0xFF111115),
                                                            border = if (isSelected) {
                                                                BorderStroke(1.5.dp, Color(0xFFFF2A54))
                                                            } else {
                                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                            },
                                                            shadowElevation = if (isSelected) 10.dp else 0.dp,
                                                            modifier = Modifier
                                                                .width(120.dp)
                                                                .height(142.dp)
                                                                .iosSpringClick {
                                                                    playEpisode(ep)
                                                                }
                                                        ) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(12.dp),
                                                                verticalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                // Top Row: Tag pill with icon + status indicator
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(7.dp),
                                                                        color = Color(itemType.hexColor).copy(alpha = 0.22f),
                                                                        border = BorderStroke(1.dp, Color(itemType.hexColor).copy(alpha = 0.70f))
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(
                                                                                text = itemType.label.uppercase(),
                                                                                color = Color(itemType.hexColor),
                                                                                fontSize = 8.sp,
                                                                                fontWeight = FontWeight.Black
                                                                            )
                                                                            if (itemType == EpisodeType.MIXED) {
                                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(itemType.hexColor), modifier = Modifier.size(9.dp))
                                                                            } else if (itemType == EpisodeType.FILLER) {
                                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                                Icon(Icons.Default.Flag, contentDescription = null, tint = Color(itemType.hexColor), modifier = Modifier.size(9.dp))
                                                                            }
                                                                        }
                                                                    }

                                                                    // Only show watched checkmark if not actively in progress (prevents state contradiction)
                                                                    if (isWatched && !isSelected) {
                                                                        Surface(
                                                                            shape = CircleShape,
                                                                            color = Color(0xFF30D15B).copy(alpha = 0.22f),
                                                                            border = BorderStroke(1.dp, Color(0xFF30D15B))
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Check,
                                                                                contentDescription = "Visto",
                                                                                tint = Color(0xFF30D15B),
                                                                                modifier = Modifier
                                                                                    .size(16.dp)
                                                                                    .padding(2.dp)
                                                                            )
                                                                        }
                                                                    } else if (isFav) {
                                                                        Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(14.dp))
                                                                    }
                                                                }

                                                                // Center: Big Bold Episode Number (No redundant "Ep." below)
                                                                Box(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    contentAlignment = Alignment.CenterStart
                                                                ) {
                                                                    Text(
                                                                        text = "$ep",
                                                                        color = Color.White,
                                                                        fontSize = 34.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        letterSpacing = (-0.5).sp
                                                                    )
                                                                }

                                                                // Bottom: Active CTA or clean status
                                                                if (isSelected) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = Color(0xFF280B13),
                                                                        border = BorderStroke(1.dp, Color(0xFFFF2A54).copy(alpha = 0.70f)),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(vertical = 4.dp),
                                                                            horizontalArrangement = Arrangement.Center,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF2A54), modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(3.dp))
                                                                            Text(
                                                                                text = "CONTINUA",
                                                                                color = Color.White,
                                                                                fontSize = 9.sp,
                                                                                fontWeight = FontWeight.Black
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    Text(
                                                                        text = if (isWatched) "Visto" else "",
                                                                        color = if (isWatched) Color(0xFF30D15B) else Color.Transparent,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(28.dp))

                                                Text(
                                                    text = "Prossimi da guardare",
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }

                                            items(upcomingEpisodes) { ep ->
                                                val isWatched = watchedEpisodes.contains(ep)
                                                val isFav = favoriteEpisodes.contains(ep)
                                                val episodeType = OnePieceHelper.getEpisodeType(ep)
                                                val epSaga = OnePieceHelper.getSagaForEpisode(ep)

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0x99121217),
                                                    border = BorderStroke(1.dp, specularBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .iosSpringClick {
                                                                playEpisode(ep)
                                                            }
                                                            .padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = Color.White.copy(alpha = 0.08f),
                                                                modifier = Modifier.size(38.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.PlayArrow,
                                                                        contentDescription = null,
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column {
                                                                Text(
                                                                    text = "Episodio $ep",
                                                                    color = Color.White,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Text(
                                                                    text = "${epSaga.name} • 24 min",
                                                                    color = Color(0xFFA0A0AA),
                                                                    fontSize = 12.sp
                                                                )
                                                            }
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = Color(episodeType.hexColor).copy(alpha = 0.20f),
                                                                border = BorderStroke(1.dp, Color(episodeType.hexColor).copy(alpha = 0.60f))
                                                            ) {
                                                                Text(
                                                                    text = episodeType.label,
                                                                    color = Color(episodeType.hexColor),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            if (isFav) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(15.dp))
                                                            }
                                                            if (isWatched) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            item {
                                                Spacer(modifier = Modifier.height(20.dp))
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(22.dp),
                                                    color = Color(0x66181822),
                                                    border = BorderStroke(1.dp, specularBorder)
                                                ) {
                                                    Column(modifier = Modifier.padding(18.dp)) {
                                                        Text("Bussola della Rotta 🧭", color = goldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = "Sei attualmente nella saga di ${currentSaga.name}. Ti mancano ${currentSaga.range.last - currentEpisodeNumber} episodi al termine di questo arco narrativo.",
                                                            color = Color.LightGray,
                                                            fontSize = 12.sp,
                                                            lineHeight = 17.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    // TAB 1: REGISTRO DI BORDO 3D COMPLETAMENTE RIORGANIZZATO
                                    val stats = remember(watchedEpisodes) {
                                        OnePieceHelper.calculateStats(watchedEpisodes.size)
                                    }
                                    var filterSelection by remember { mutableIntStateOf(0) }

                                    // Dynamic Pirate Rank Title based on episodes watched (Typography badge, no playful emoji)
                                    val pirateRank = remember(watchedEpisodes.size) {
                                        when (watchedEpisodes.size) {
                                            in 0..61 -> "Mozzo dell'East Blue"
                                            in 62..206 -> "Pirata della Rotta Maggiore"
                                            in 207..516 -> "Supernova dei Pirati"
                                            in 517..891 -> "Veterano del Nuovo Mondo"
                                            in 892..1085 -> "Grande Flotta di Cappello di Paglia"
                                            else -> "Imperatore dei Mari"
                                        }
                                    }

                                    // Watch time stats: 23.5 minutes per episode
                                    val totalMinutesSpent = watchedEpisodes.size * 23.5
                                    val hoursSpent = (totalMinutesSpent / 60).toInt()
                                    val minsRemaining = (totalMinutesSpent % 60).toInt()
                                    val percentageWatched = (watchedEpisodes.size.toFloat() / OnePieceHelper.TOTAL_AIRING_EPISODES * 100f)

                                    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                                    fun episodeMatchesFilter(ep: Int, filter: Int): Boolean {
                                        val t = OnePieceHelper.getEpisodeType(ep)
                                        return when (filter) {
                                            1 -> watchedEpisodes.contains(ep)
                                            2 -> !watchedEpisodes.contains(ep)
                                            3 -> favoriteEpisodes.contains(ep)
                                            4 -> t == EpisodeType.MANGA_CANON
                                            5 -> t == EpisodeType.FILLER
                                            6 -> t == EpisodeType.MIXED
                                            7 -> t == EpisodeType.ANIME_CANON
                                            else -> true
                                        }
                                    }

                                    val visibleSagas = remember(filterSelection, watchedEpisodes, favoriteEpisodes) {
                                        if (filterSelection == 0) {
                                            OnePieceHelper.SAGAS
                                        } else {
                                            OnePieceHelper.SAGAS.filter { saga ->
                                                saga.range.any { ep -> episodeMatchesFilter(ep, filterSelection) }
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF070709))
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 18.dp),
                                            contentPadding = PaddingValues(top = statusBarTop + 14.dp, bottom = 120.dp)
                                        ) {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f, fill = false)
                                                            .padding(end = 12.dp)
                                                    ) {
                                                        Text("DIARIO DI BORDO", color = accentRed, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                                                        Text("Registro Saghe", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    IconButton(
                                                        onClick = { showSettingsDialog = true },
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.10f))
                                                    ) {
                                                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni", tint = Color.White, modifier = Modifier.size(19.dp))
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // MODERN REDESIGNED HERO STATS DASHBOARD
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color(0xF214141E),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    shadowElevation = 16.dp
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                Brush.linearGradient(
                                                                    colors = listOf(
                                                                        Color(0x33FF2A42),
                                                                        Color(0x15FFD700),
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            )
                                                            .padding(20.dp)
                                                    ) {
                                                        // Top Rank & Category Tag
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    Icons.Default.Explore,
                                                                    contentDescription = null,
                                                                    tint = goldAccent,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = "PROIEZIONE DI ROTTA",
                                                                    color = goldAccent,
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = 11.sp,
                                                                    letterSpacing = 1.5.sp
                                                                )
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0x33FFD700),
                                                                border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.40f))
                                                            ) {
                                                                Text(
                                                                    text = pirateRank,
                                                                    color = Color.White,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(14.dp))

                                                        // Spotlight metric: Data Pareggio
                                                        Text("Data Pareggio Stimata", color = Color.LightGray, fontSize = 11.sp)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = stats.third,
                                                            color = Color.White,
                                                            fontSize = 22.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = "Al ritmo di ${"%.2f".format(stats.first)} episodi al giorno",
                                                            color = Color(0xFF30D15B),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // Global Progress Bar with clean metadata
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Progresso Globale Serie", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(
                                                                "${watchedEpisodes.size} di ${OnePieceHelper.TOTAL_AIRING_EPISODES} ep. (${String.format(Locale.ITALIAN, "%.1f", percentageWatched)}%)",
                                                                color = accentRed,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        LinearProgressIndicator(
                                                            progress = { (percentageWatched / 100f).coerceIn(0f, 1f) },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(7.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            color = accentRed,
                                                            trackColor = Color.White.copy(alpha = 0.12f)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Dual Modern Secondary Cards (Episodi Visti & Tempo Navigato)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Card 1: Episodi Visti
                                                    Surface(
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(18.dp),
                                                        color = Color(0xF212131C),
                                                        border = BorderStroke(1.dp, Color(0xFF30D15B).copy(alpha = 0.25f)),
                                                        shadowElevation = 8.dp
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    Icons.Default.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF30D15B),
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Episodi Visti", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = "${watchedEpisodes.size}",
                                                                color = Color.White,
                                                                fontSize = 22.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "${OnePieceHelper.TOTAL_AIRING_EPISODES - watchedEpisodes.size} rimanenti",
                                                                color = Color.Gray,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    // Card 2: Tempo Speso
                                                    Surface(
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(18.dp),
                                                        color = Color(0xF212131C),
                                                        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.25f)),
                                                        shadowElevation = 8.dp
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    Icons.Default.Schedule,
                                                                    contentDescription = null,
                                                                    tint = goldAccent,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Tempo Speso", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = "${hoursSpent}h ${minsRemaining}m",
                                                                color = goldAccent,
                                                                fontSize = 18.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "a ~23.5m / ep.",
                                                                color = Color.Gray,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(18.dp))

                                                // COMPACT COLORED FILTER CHIPS
                                                data class FilterOption(val label: String, val activeColor: Color, val tagColor: Color)
                                                val filterOptions = listOf(
                                                    FilterOption("Tutti ${OnePieceHelper.TOTAL_AIRING_EPISODES}", accentRed, Color.White),
                                                    FilterOption("Visti ${watchedEpisodes.size}", Color(0xFF34C759), Color(0xFF34C759)),
                                                    FilterOption("Da vedere ${OnePieceHelper.TOTAL_AIRING_EPISODES - watchedEpisodes.size}", Color(0xFFFF9500), Color(0xFFFF9500)),
                                                    FilterOption("Preferiti ${favoriteEpisodes.size}", goldAccent, goldAccent),
                                                    FilterOption("Canon", Color(EpisodeType.MANGA_CANON.hexColor), Color(EpisodeType.MANGA_CANON.hexColor)),
                                                    FilterOption("Filler", Color(EpisodeType.FILLER.hexColor), Color(EpisodeType.FILLER.hexColor)),
                                                    FilterOption("Mixed", Color(EpisodeType.MIXED.hexColor), Color(EpisodeType.MIXED.hexColor)),
                                                    FilterOption("Anime Canon", Color(EpisodeType.ANIME_CANON.hexColor), Color(EpisodeType.ANIME_CANON.hexColor))
                                                )

                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(filterOptions.indices.toList()) { i ->
                                                        val option = filterOptions[i]
                                                        val isSelected = filterSelection == i
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (isSelected) option.activeColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isSelected) option.activeColor else option.tagColor.copy(alpha = 0.25f)
                                                            ),
                                                            modifier = Modifier.iosSpringClick { filterSelection = i }
                                                        ) {
                                                            Text(
                                                                text = option.label,
                                                                color = if (isSelected) option.activeColor else Color.LightGray,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(14.dp))
                                            }

                                            // SAGAS 3D ACCORDION WITH UPSTREAM FILTERING
                                            if (visibleSagas.isEmpty()) {
                                                item {
                                                    Surface(
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = Color(0xF0141620),
                                                        border = BorderStroke(1.dp, specularBorder),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 24.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(28.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                text = if (filterSelection == 2) "🎉" else "⚓",
                                                                fontSize = 36.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            Text(
                                                                text = if (filterSelection == 2) "Tutte le saghe sono state completate!" else "Nessuna saga trovata",
                                                                color = Color.White,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = if (filterSelection == 2) "Hai visto tutti gli episodi disponibili per questa categoria." else "Prova a selezionare un altro filtro per visualizzare le saghe.",
                                                                color = Color.Gray,
                                                                fontSize = 12.sp,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                items(visibleSagas, key = { it.name }) { saga ->
                                                    val totalInSaga = saga.range.count()
                                                    val watchedInSaga = saga.range.count { watchedEpisodes.contains(it) }
                                                    val isCompleted = watchedInSaga == totalInSaga
                                                    val sagaPercent = (watchedInSaga.toFloat() / totalInSaga.toFloat())
                                                    var isExpanded by remember { mutableStateOf(false) }
                                                    val arrowRotation by animateFloatAsState(
                                                        targetValue = if (isExpanded) 180f else 0f,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 320f),
                                                        label = "arrowRot"
                                                    )

                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 5.dp),
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = Color(0xF0121217),
                                                        border = if (isCompleted) {
                                                            BorderStroke(1.dp, Color(0xFF30D15B).copy(alpha = 0.35f))
                                                        } else {
                                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        },
                                                        shadowElevation = 2.dp
                                                    ) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .iosSpringClick { isExpanded = !isExpanded },
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Text(text = saga.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                                        if (isCompleted) {
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Icon(
                                                                                Icons.Default.CheckCircle,
                                                                                contentDescription = "Completata",
                                                                                tint = Color(0xFF30D15B),
                                                                                modifier = Modifier.size(15.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                    Text(text = "Ep. ${saga.range.first} - ${saga.range.last} • ${saga.description.take(45)}...", color = Color.Gray, fontSize = 11.sp)
                                                                }

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = "${(sagaPercent * 100).toInt()}%",
                                                                        color = if (isCompleted) Color(0xFF30D15B) else Color.White,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Icon(
                                                                        Icons.Default.KeyboardArrowDown,
                                                                        contentDescription = null,
                                                                        tint = Color.Gray,
                                                                        modifier = Modifier
                                                                            .size(20.dp)
                                                                            .rotate(arrowRotation)
                                                                    )
                                                                }
                                                            }

                                                            // Micro progress bar inside saga card without duplicate numbers
                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("Progresso Saga", color = Color.Gray, fontSize = 10.sp)
                                                                Text("$watchedInSaga / $totalInSaga ep.", color = if (isCompleted) Color(0xFF30D15B) else Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            LinearProgressIndicator(
                                                                progress = { sagaPercent },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(4.dp)
                                                                    .clip(RoundedCornerShape(2.dp)),
                                                                color = if (isCompleted) Color(0xFF30D15B) else accentRed,
                                                                trackColor = Color.White.copy(alpha = 0.10f)
                                                            )

                                                            AnimatedVisibility(
                                                                visible = isExpanded,
                                                                enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = 320f)),
                                                                exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.90f, stiffness = 350f))
                                                            ) {
                                                                Column {
                                                                    Spacer(modifier = Modifier.height(14.dp))
                                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                                                                    Spacer(modifier = Modifier.height(10.dp))

                                                                    // Quick Action: Mark whole saga
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.End
                                                                    ) {
                                                                        Surface(
                                                                            shape = RoundedCornerShape(10.dp),
                                                                            color = Color.White.copy(alpha = 0.08f),
                                                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                                            modifier = Modifier.iosSpringClick {
                                                                                val markAll = !isCompleted
                                                                                saga.range.forEach { prefs.markEpisodeWatched(it, markAll) }
                                                                                watchedEpisodes = prefs.getWatchedEpisodes()
                                                                                Toast.makeText(
                                                                                    this@MainActivity,
                                                                                    if (markAll) "Saga ${saga.name} completata!" else "Saga azzerata",
                                                                                    Toast.LENGTH_SHORT
                                                                                ).show()
                                                                            }
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Icon(
                                                                                    Icons.Default.DoneAll,
                                                                                    contentDescription = null,
                                                                                    tint = if (isCompleted) Color.Gray else Color(0xFF30D15B),
                                                                                    modifier = Modifier.size(14.dp)
                                                                                )
                                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                                Text(
                                                                                    text = if (isCompleted) "Deseleziona tutta la saga" else "Segna saga come vista",
                                                                                    color = if (isCompleted) Color.Gray else Color(0xFF30D15B),
                                                                                    fontSize = 11.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                    Spacer(modifier = Modifier.height(8.dp))

                                                                    val filteredEpisodes = remember(saga, filterSelection, watchedEpisodes, favoriteEpisodes) {
                                                                        saga.range.filter { ep -> episodeMatchesFilter(ep, filterSelection) }
                                                                    }

                                                                    filteredEpisodes.forEach { ep ->
                                                                        val isWatched = watchedEpisodes.contains(ep)
                                                                        val isFav = favoriteEpisodes.contains(ep)
                                                                        val itemEpType = OnePieceHelper.getEpisodeType(ep)

                                                                        val rowAlpha by animateFloatAsState(
                                                                            targetValue = if (isWatched) 0.50f else 1.0f,
                                                                            animationSpec = tween(220),
                                                                            label = "epRowAlpha"
                                                                        )

                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .padding(vertical = 3.dp)
                                                                                .graphicsLayer { alpha = rowAlpha },
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                modifier = Modifier
                                                                                    .weight(1f)
                                                                                    .iosSpringClick {
                                                                                        selectedEpisodeForDetail = ep
                                                                                    }
                                                                            ) {
                                                                                Text(
                                                                                    text = "Episodio $ep",
                                                                                    color = if (isWatched) Color(0xFF8E8E93) else Color.White,
                                                                                    fontSize = 14.sp,
                                                                                    fontWeight = if (isWatched) FontWeight.Normal else FontWeight.SemiBold
                                                                                )
                                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(6.dp),
                                                                                    color = Color(itemEpType.hexColor).copy(alpha = 0.20f),
                                                                                    border = BorderStroke(1.dp, Color(itemEpType.hexColor).copy(alpha = 0.60f))
                                                                                ) {
                                                                                    Text(
                                                                                        text = itemEpType.label,
                                                                                        color = Color(itemEpType.hexColor),
                                                                                        fontSize = 9.sp,
                                                                                        fontWeight = FontWeight.ExtraBold,
                                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                                                    )
                                                                                }
                                                                                if (isFav) {
                                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                                    Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(13.dp))
                                                                                }
                                                                            }

                                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        val q = prefs.getPreferredDownloadQuality()
                                                                                        coroutineScope.launch {
                                                                                            downloadManagerHelper.startDownloadForEpisode(ep, q)
                                                                                        }
                                                                                        Toast.makeText(this@MainActivity, "Download Ep. $ep ($q) avviato!", Toast.LENGTH_SHORT).show()
                                                                                    },
                                                                                    modifier = Modifier.size(28.dp)
                                                                                ) {
                                                                                    Icon(Icons.Default.Download, contentDescription = "Scarica", tint = Color(0xFF32ADE6), modifier = Modifier.size(16.dp))
                                                                                }

                                                                                IconButton(
                                                                                    onClick = { playEpisode(ep) },
                                                                                    modifier = Modifier.size(28.dp)
                                                                                ) {
                                                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Guarda", tint = accentRed, modifier = Modifier.size(18.dp))
                                                                                }

                                                                                // Custom Animated CheckMark
                                                                                EpisodeCheckMark(
                                                                                    isWatched = isWatched,
                                                                                    onToggle = {
                                                                                        val newStatus = !isWatched
                                                                                        prefs.markEpisodeWatched(ep, newStatus)
                                                                                        watchedEpisodes = prefs.getWatchedEpisodes()
                                                                                    }
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
                                        }
                                    }
                                }

                                2 -> {
                                    // TAB 2: DOWNLOAD OFFLINE MODERNO (720p/1080p, Batch Download, Progress Real-Time, Cancel Reale)
                                    DownloadTabContent(
                                        downloadManagerHelper = downloadManagerHelper,
                                        prefs = prefs,
                                        currentEpisodeNumber = currentEpisodeNumber,
                                        watchedEpisodes = watchedEpisodes,
                                        onPlayOfflineEpisode = { filePath, epNum ->
                                            activeVideoUrl = filePath
                                            currentEpisodeNumber = epNum
                                            currentWebUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, epNum)
                                        },
                                        onOpenSettings = { showSettingsDialog = true }
                                    )
                                }
                            }
                        }
                    }

                        // IN-APP BROWSER OVERLAY (Aperto tramite pulsante nella Home)
                        if (isBrowserOpen) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Top Chrome Bar
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .height(56.dp),
                                    color = Color(0xF2101016),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (webViewInstance?.canGoBack() == true) {
                                                    webViewInstance?.goBack()
                                                } else {
                                                    isBrowserOpen = false
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.10f))
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Indietro",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "One Piece Power",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = currentWebUrl,
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { webViewInstance?.reload() },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.10f))
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Ricarica",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        IconButton(
                                            onClick = { isBrowserOpen = false },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.10f))
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Chiudi Browser",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Floating Quick Player Launch
                                AnimatedVisibility(
                                    visible = activeVideoUrl == null && detectedVideoUrl != null,
                                    enter = slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ),
                                    exit = slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = accentRed,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                        shadowElevation = 14.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .iosSpringClick {
                                                activeVideoUrl = detectedVideoUrl
                                                isBrowserOpen = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Apri nel Player Cinema 🎬", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // STREAM RESOLUTION FLOATING SPINNER
                        if (isResolvingStream) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = Color(0xF2161622),
                                    border = BorderStroke(1.dp, specularBorder),
                                    shadowElevation = 24.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = accentRed,
                                            modifier = Modifier.size(46.dp),
                                            strokeWidth = 3.5.dp
                                        )
                                        Text(
                                            text = "Ricerca stream Episodio $resolvingEpisodeNumber...",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Bypass annunci & connessione al server",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // FLOATING GLASS CAPSULE NAVIGATION DOCK (Apple Squircle, Obsidian Black)
                        if (activeVideoUrl == null && !isBrowserOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                // Soft gradient scrim to prevent text overlap underneath dock
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color(0x99070709),
                                                    Color(0xF5070709)
                                                )
                                            )
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(32.dp),
                                        color = Color(0xF8111116),
                                        border = BorderStroke(1.dp, specularBorder),
                                        shadowElevation = 24.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        data class NavItem(val tabIndex: Int, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                                        val items = remember {
                                            listOf(
                                                NavItem(0, "Cinema", Icons.Default.PlayCircle),
                                                NavItem(1, "Registro", Icons.Default.Explore),
                                                NavItem(2, "Download", Icons.Default.FileDownload)
                                            )
                                        }

                                        // LIQUID TEARDROP DROPLET PHYSICS WITH VOLUME CONSERVATION
                                        val targetPosition = currentTab.toFloat()

                                        // 'head' launches forward with swift spring in the direction of movement
                                        val headPos by animateFloatAsState(
                                            targetValue = targetPosition,
                                            animationSpec = spring(
                                                dampingRatio = 0.74f,
                                                stiffness = 400f
                                            ),
                                            label = "fluidHead"
                                        )
                                        // 'tail' trails behind with elastic fluid inertia
                                        val tailPos by animateFloatAsState(
                                            targetValue = targetPosition,
                                            animationSpec = spring(
                                                dampingRatio = 0.58f,
                                                stiffness = 160f
                                            ),
                                            label = "fluidTail"
                                        )

                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            val totalWidth = maxWidth
                                            val tabCount = items.size
                                            val tabWidth = totalWidth / tabCount
                                            val basePillWidth = 74.dp
                                            val basePillHeight = 44.dp

                                            val minP = minOf(headPos, tailPos)
                                            val maxP = maxOf(headPos, tailPos)
                                            val delta = headPos - tailPos // positive = moving right, negative = moving left

                                            // Calculate dynamic liquid horizontal stretch bounds
                                            val leftX = (tabWidth * (minP + 0.5f)) - (basePillWidth / 2f)
                                            val rightX = (tabWidth * (maxP + 0.5f)) + (basePillWidth / 2f)
                                            val liquidWidth = (rightX - leftX).coerceAtLeast(basePillWidth)

                                            // FLUID VOLUME CONSERVATION:
                                            // As horizontal width stretches from velocity, vertical height squashes down
                                            val stretchRatio = (liquidWidth.value / basePillWidth.value).coerceAtLeast(1f)
                                            val liquidHeight = (basePillHeight.value / kotlin.math.sqrt(stretchRatio.toDouble()).toFloat())
                                                .coerceIn(26f, basePillHeight.value).dp

                                            // Droplet asymmetry: leading front is a bulbous dome, trailing tail tapers
                                            val speedMagnitude = kotlin.math.abs(delta).coerceIn(0f, 1.5f)
                                            val isMovingRight = delta >= 0f

                                            val leadCorner = 24.dp
                                            val trailCorner = (24f - (speedMagnitude * 10f)).coerceAtLeast(12f).dp

                                            val dropletShape = RoundedCornerShape(
                                                topStart = if (isMovingRight) trailCorner else leadCorner,
                                                bottomStart = if (isMovingRight) trailCorner else leadCorner,
                                                topEnd = if (isMovingRight) leadCorner else trailCorner,
                                                bottomEnd = if (isMovingRight) leadCorner else trailCorner
                                            )

                                            // THE VISIBLE GLOWING LIQUID DROPLET CAPSULE
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = leftX)
                                                    .width(liquidWidth)
                                                    .height(liquidHeight)
                                                    .align(Alignment.CenterStart)
                                                    .clip(dropletShape)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.32f),
                                                                Color(0xFFFF3333).copy(alpha = 0.22f),
                                                                accentRed.copy(alpha = 0.36f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        width = 1.2.dp,
                                                        brush = Brush.horizontalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.90f),
                                                                Color(0xFFFF5555).copy(alpha = 0.65f),
                                                                accentRed.copy(alpha = 0.95f)
                                                            )
                                                        ),
                                                        shape = dropletShape
                                                    )
                                            ) {
                                                // Glossy liquid specular sheen along top edge
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.75f)
                                                        .height(2.dp)
                                                        .align(Alignment.TopCenter)
                                                        .padding(top = 3.dp)
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(
                                                                    Color.Transparent,
                                                                    Color.White.copy(alpha = 0.55f),
                                                                    Color.Transparent
                                                                )
                                                            )
                                                        )
                                                )
                                            }

                                            // Interactive Row containing the nav icons
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                items.forEach { item ->
                                                    val isSelected = currentTab == item.tabIndex
                                                    val iconScale by animateFloatAsState(
                                                        targetValue = if (isSelected) 1.16f else 1.0f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.65f,
                                                            stiffness = 350f
                                                        ),
                                                        label = "navIconScale"
                                                    )
                                                    val iconAlpha by animateFloatAsState(
                                                        targetValue = if (isSelected) 1.0f else 0.45f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.8f,
                                                            stiffness = 300f
                                                        ),
                                                        label = "navIconAlpha"
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                            .iosSpringClick {
                                                                if (item.tabIndex == 1) {
                                                                    watchedEpisodes = prefs.getWatchedEpisodes()
                                                                    favoriteEpisodes = prefs.getFavoriteEpisodes()
                                                                }
                                                                if (item.tabIndex == 2) {
                                                                    downloadedList = getDownloadedFilesList()
                                                                }
                                                                currentTab = item.tabIndex
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (item.tabIndex == 1) {
                                                            LogPoseCompassIcon(
                                                                isSelected = isSelected,
                                                                iconSize = 23.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = item.icon,
                                                                contentDescription = item.label,
                                                                tint = if (isSelected) Color.White else Color.White.copy(alpha = iconAlpha),
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                                            )
                                                        }

                                                        if (item.tabIndex == 2 && downloadedList.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopCenter)
                                                                    .padding(top = 8.dp, start = 22.dp)
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(accentRed)
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

                        // EXPANDING MODAL 1: EPISODE DETAIL QUICK-SHEET (iOS 17 FROSTED GLASS)
                        selectedEpisodeForDetail?.let { detailEp ->
                            val detailSaga = OnePieceHelper.getSagaForEpisode(detailEp)
                            val detailType = OnePieceHelper.getEpisodeType(detailEp)
                            val isEpWatched = watchedEpisodes.contains(detailEp)
                            val isEpFav = favoriteEpisodes.contains(detailEp)

                            Dialog(
                                onDismissRequest = { selectedEpisodeForDetail = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            selectedEpisodeForDetail = null
                                        },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 20.dp)
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                                        shape = RoundedCornerShape(32.dp),
                                        color = Color(0xF2161622),
                                        border = BorderStroke(1.dp, specularBorder),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // iOS Pill Grab Handle
                                            Box(
                                                modifier = Modifier
                                                    .width(40.dp)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.White.copy(alpha = 0.25f))
                                            )

                                            Spacer(modifier = Modifier.height(18.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = detailSaga.name.uppercase(),
                                                        color = goldAccent,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp
                                                    )
                                                    Text(
                                                        text = "Episodio $detailEp",
                                                        color = Color.White,
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(detailType.hexColor).copy(alpha = 0.22f),
                                                    border = BorderStroke(1.dp, Color(detailType.hexColor).copy(alpha = 0.65f))
                                                ) {
                                                    Text(
                                                        text = detailType.label,
                                                        color = Color(detailType.hexColor),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = detailSaga.description,
                                                color = Color.LightGray,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(22.dp))

                                            // Main Play Button
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = accentRed,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                shadowElevation = 10.dp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .iosSpringClick {
                                                        selectedEpisodeForDetail = null
                                                        playEpisode(detailEp)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 15.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Guarda Ora Episodio $detailEp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Secondary action buttons (Watched toggle, Favorite toggle)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (isEpWatched) Color(0xFF34C759).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                                    border = BorderStroke(1.dp, if (isEpWatched) Color(0xFF34C759) else Color.White.copy(alpha = 0.15f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .iosSpringClick {
                                                            val newStatus = !isEpWatched
                                                            prefs.markEpisodeWatched(detailEp, newStatus)
                                                            watchedEpisodes = prefs.getWatchedEpisodes()
                                                            Toast.makeText(this@MainActivity, if (newStatus) "Segnato come visto ✅" else "Segnato come da vedere", Toast.LENGTH_SHORT).show()
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isEpWatched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                                            contentDescription = null,
                                                            tint = if (isEpWatched) Color(0xFF34C759) else Color.White,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = if (isEpWatched) "Visto" else "Segna Visto",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (isEpFav) goldAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                                    border = BorderStroke(1.dp, if (isEpFav) goldAccent else Color.White.copy(alpha = 0.15f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .iosSpringClick {
                                                            val newFav = prefs.toggleFavorite(detailEp)
                                                            favoriteEpisodes = prefs.getFavoriteEpisodes()
                                                            Toast.makeText(this@MainActivity, if (newFav) "Aggiunto ai Preferiti ⭐" else "Rimosso dai Preferiti", Toast.LENGTH_SHORT).show()
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isEpFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = null,
                                                            tint = if (isEpFav) goldAccent else Color.White,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = if (isEpFav) "Preferito" else "Aggiungi",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Direct Download Button without opening Player!
                                            val currentPreferredQuality = prefs.getPreferredDownloadQuality()
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color(0x3332ADE6),
                                                border = BorderStroke(1.dp, Color(0xFF32ADE6).copy(alpha = 0.45f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .iosSpringClick {
                                                        coroutineScope.launch {
                                                            downloadManagerHelper.startDownloadForEpisode(detailEp, currentPreferredQuality)
                                                        }
                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "Avvio download Ep. $detailEp ($currentPreferredQuality)... Controlla la tab Download per il progresso!",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF32ADE6), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Scarica Episodio ($currentPreferredQuality)",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Batch Download: Prossimi 3 Episodi
                                            val next3Episodes = (detailEp until minOf(detailEp + 3, OnePieceHelper.TOTAL_AIRING_EPISODES + 1)).toList()
                                            if (next3Episodes.size > 1) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color.White.copy(alpha = 0.08f),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .iosSpringClick {
                                                            coroutineScope.launch {
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "Avvio download in massa per gli episodi ${next3Episodes.joinToString(", ")} ($currentPreferredQuality)...",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                                for (ep in next3Episodes) {
                                                                    downloadManagerHelper.startDownloadForEpisode(ep, currentPreferredQuality)
                                                                }
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, tint = accentRed, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Scarica Prossimi ${next3Episodes.size} Episodi (${next3Episodes.first()}-${next3Episodes.last()})",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // EXPANDING MODAL 2: QUICK JUMP & SEARCH (iOS 17 FLOATING MODAL)
                        if (showQuickJumpDialog) {
                            val focusManager = LocalFocusManager.current
                            Dialog(
                                onDismissRequest = { showQuickJumpDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .imePadding()
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            showQuickJumpDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                                        shape = RoundedCornerShape(26.dp),
                                        color = Color(0xF2121217),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(22.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Cerca Episodio o Saga", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                    Text("Digita il numero o il nome della saga", color = Color.Gray, fontSize = 12.sp)
                                                }
                                                IconButton(onClick = { showQuickJumpDialog = false }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            OutlinedTextField(
                                                value = quickJumpInput,
                                                onValueChange = { quickJumpInput = it },
                                                placeholder = { Text("Es. 1071, Wano, Alabasta, Enies...", color = Color.Gray) },
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingIcon = {
                                                    Icon(Icons.Default.Search, contentDescription = null, tint = accentRed)
                                                },
                                                trailingIcon = {
                                                    if (quickJumpInput.isNotEmpty()) {
                                                        IconButton(onClick = { quickJumpInput = "" }) {
                                                            Icon(Icons.Default.Close, contentDescription = "Cancella", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = {
                                                    focusManager.clearFocus()
                                                    val epNum = quickJumpInput.trim().toIntOrNull()
                                                    if (epNum != null && epNum in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                                        showQuickJumpDialog = false
                                                        playEpisode(epNum)
                                                    } else {
                                                        val query = quickJumpInput.trim().lowercase()
                                                        val matchedSaga = OnePieceHelper.SAGAS.find { it.name.lowercase().contains(query) }
                                                        if (matchedSaga != null) {
                                                            showQuickJumpDialog = false
                                                            playEpisode(matchedSaga.range.first)
                                                        }
                                                    }
                                                }),
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = accentRed,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                                                )
                                            )

                                            // Dynamic Search Results
                                            val queryTrimmed = quickJumpInput.trim()
                                            val parsedEpNum = queryTrimmed.toIntOrNull()
                                            val matchingSagas = if (queryTrimmed.isNotEmpty() && parsedEpNum == null) {
                                                OnePieceHelper.SAGAS.filter { it.name.lowercase().contains(queryTrimmed.lowercase()) }
                                            } else emptyList()

                                            if (parsedEpNum != null && parsedEpNum in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color.White.copy(alpha = 0.06f),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Episodio $parsedEpNum", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                            val epType = OnePieceHelper.getEpisodeType(parsedEpNum)
                                                            Text(epType.label, color = Color(epType.hexColor), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            IconButton(
                                                                onClick = {
                                                                    val q = prefs.getPreferredDownloadQuality()
                                                                    coroutineScope.launch {
                                                                        downloadManagerHelper.startDownloadForEpisode(parsedEpNum, q)
                                                                    }
                                                                    Toast.makeText(this@MainActivity, "Download Ep. $parsedEpNum ($q) avviato! 📥", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0x3332ADE6))
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = "Scarica", tint = Color(0xFF32ADE6), modifier = Modifier.size(18.dp))
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    showQuickJumpDialog = false
                                                                    playEpisode(parsedEpNum)
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                                                                shape = RoundedCornerShape(12.dp),
                                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Guarda", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (matchingSagas.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text("Saghe Trovate:", color = Color.Gray, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    matchingSagas.take(3).forEach { saga ->
                                                        Surface(
                                                            shape = RoundedCornerShape(14.dp),
                                                            color = Color.White.copy(alpha = 0.05f),
                                                            border = BorderStroke(1.dp, specularBorder),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .iosSpringClick {
                                                                    showQuickJumpDialog = false
                                                                    playEpisode(saga.range.first)
                                                                }
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(12.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column {
                                                                    Text(saga.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                                    Text("Episodi ${saga.range.first} - ${saga.range.last}", color = Color.Gray, fontSize = 11.sp)
                                                                }
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentRed, modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Saghe Principali / Momenti Chiave:", color = Color.LightGray, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val milestones = listOf(
                                                Pair("Ep. 1 - Inizio", 1),
                                                Pair("Ep. 62 - Rotta", 62),
                                                Pair("Ep. 136 - Sky", 136),
                                                Pair("Ep. 227 - Enies", 227),
                                                Pair("Ep. 483 - Guerra", 483),
                                                Pair("Ep. 892 - Wano", 892),
                                                Pair("Ep. 1071 - Gear 5", 1071)
                                            )

                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(milestones) { (label, ep) ->
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = Color.White.copy(alpha = 0.08f),
                                                        border = BorderStroke(1.dp, specularBorder),
                                                        modifier = Modifier.iosSpringClick {
                                                            showQuickJumpDialog = false
                                                            playEpisode(ep)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Button(
                                                onClick = {
                                                    val epNum = quickJumpInput.trim().toIntOrNull()
                                                    if (epNum != null && epNum in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                                        showQuickJumpDialog = false
                                                        playEpisode(epNum)
                                                    } else {
                                                        val query = quickJumpInput.trim().lowercase()
                                                        val matchedSaga = OnePieceHelper.SAGAS.find { it.name.lowercase().contains(query) }
                                                        if (matchedSaga != null) {
                                                            showQuickJumpDialog = false
                                                            playEpisode(matchedSaga.range.first)
                                                        } else {
                                                            Toast.makeText(this@MainActivity, "Inserisci un numero da 1 a ${OnePieceHelper.TOTAL_AIRING_EPISODES} o il nome di una saga", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Avvia Episodio", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // EXPANDING MODAL 3: IMPORT DATA MODAL (iOS 17 FROSTED GLASS)
                        if (showImportDialog) {
                            Dialog(
                                onDismissRequest = { showImportDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            showImportDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                                        shape = RoundedCornerShape(28.dp),
                                        color = Color(0xF2161622),
                                        border = BorderStroke(1.dp, specularBorder),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(modifier = Modifier.padding(22.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Ripristina Backup Dati", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                IconButton(onClick = { showImportDialog = false }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text("Incolla la stringa JSON esportata in precedenza:", color = Color.LightGray, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = importInputText,
                                                onValueChange = { importInputText = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                maxLines = 4,
                                                placeholder = { Text("{\"lastEpisode\":...}", color = Color.Gray) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = accentRed,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { showImportDialog = false },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                                ) {
                                                    Text("Annulla", color = Color.LightGray)
                                                }

                                                Button(
                                                    onClick = {
                                                        val backupData = OnePieceHelper.importFromJson(importInputText)
                                                        if (backupData != null) {
                                                            backupData.watchedEpisodes.forEach { prefs.markEpisodeWatched(it, true) }
                                                            backupData.favoriteEpisodes.forEach { prefs.toggleFavorite(it) }
                                                            currentEpisodeNumber = backupData.lastEpisode
                                                            val epUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, backupData.lastEpisode)
                                                            currentWebUrl = epUrl
                                                            prefs.saveLastPlayback(epUrl, backupData.lastEpisode, backupData.lastPositionMs)
                                                            watchedEpisodes = prefs.getWatchedEpisodes()
                                                            favoriteEpisodes = prefs.getFavoriteEpisodes()
                                                            if (backupData.dailyStreak > 0) {
                                                                prefs.saveDailyStreak(backupData.dailyStreak, backupData.lastWatchDate)
                                                                dailyStreak = backupData.dailyStreak
                                                            }
                                                            webViewInstance?.loadUrl(epUrl)
                                                            Toast.makeText(this@MainActivity, "Backup ripristinato con successo!", Toast.LENGTH_SHORT).show()
                                                            showImportDialog = false
                                                        } else {
                                                            Toast.makeText(this@MainActivity, "Formato JSON non valido", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Text("Ripristina", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STREAK & NOTIFICATION DIALOG
                        if (showStreakReminderDialog) {
                            Dialog(
                                onDismissRequest = { showStreakReminderDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            showStreakReminderDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                                        shape = RoundedCornerShape(26.dp),
                                        color = Color(0xF2151622),
                                        border = BorderStroke(1.dp, specularBorder),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(modifier = Modifier.padding(24.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🔥", fontSize = 24.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Serie Giornaliera",
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "$dailyStreak giorni consecutivi",
                                                        color = goldAccent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { showStreakReminderDialog = false }) {
                                                Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0x33FF2A42),
                                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "Non spezzare la rotta!",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Guarda almeno un episodio al giorno per mantenere la tua streak e aumentare la tua taglia pirate. Attiva le notifiche per ricevere promemoria di navigazione.",
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showStreakReminderDialog = false },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                            ) {
                                                Text("Chiudi", color = Color.LightGray)
                                            }

                                            Button(
                                                onClick = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                    } else {
                                                        showEpisodeReminderNotification(currentEpisodeNumber, dailyStreak)
                                                        Toast.makeText(this@MainActivity, "Promemoria inviato! 🏴‍☠️", Toast.LENGTH_SHORT).show()
                                                    }
                                                    showStreakReminderDialog = false
                                                },
                                                modifier = Modifier.weight(1.3f),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Attiva Promemoria", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // SETTINGS & CLOUD DIALOG MODAL (Gear Icon)
                        SettingsDialog(
                            isOpen = showSettingsDialog,
                            onDismiss = { showSettingsDialog = false },
                            prefs = prefs,
                            cloudSyncManager = cloudSyncManager,
                            currentEpisode = currentEpisodeNumber,
                            dailyStreak = dailyStreak,
                            watchedCount = watchedEpisodes.size,
                            onRestoreJsonRequested = {
                                showImportDialog = true
                            },
                            onSyncSuccess = { email ->
                                cloudUserEmail = email
                                cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                            }
                        )

                        activeVideoUrl?.let { videoUrl ->
                            VideoPlayerScreen(
                                videoUrl = videoUrl,
                                currentWebUrl = currentWebUrl,
                                initialPositionMs = startFromPosition,
                                isInPipMode = isInPipModeState.value,
                                isAdvancingNext = isPlayerAdvancingNext,
                                onEnterPip = { enterPipMode() },
                                onDownloadRequested = { url, ep ->
                                    downloadEpisodeOffline(url, ep)
                                },
                                onNextEpisode = {
                                    val nextEp = currentEpisodeNumber + 1
                                    if (nextEp <= OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                        val nextUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, nextEp)
                                        currentEpisodeNumber = nextEp
                                        currentWebUrl = nextUrl
                                        startFromPosition = 0L
                                        isPlayerAdvancingNext = true
                                        isAutoAdvancing = true

                                        // 1. Controlla prima se è in locale
                                        val localNext = downloadedList.firstOrNull { it.episodeNumber == nextEp }
                                        if (localNext != null && localNext.file.exists()) {
                                            activeVideoUrl = localNext.file.absolutePath
                                            isPlayerAdvancingNext = false
                                            prefs.saveLastPlayback(localNext.file.absolutePath, nextEp, 0L)
                                            Toast.makeText(this@MainActivity, "Riproduzione locale Ep. $nextEp 💾", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // 2. Naviga WebView e in parallelo estrai direttamente
                                            webViewInstance?.loadUrl(nextUrl)

                                            lifecycleScope.launch(Dispatchers.IO) {
                                                try {
                                                    val direct = StreamExtractor.resolveStreamUrl(nextUrl)
                                                    if (!direct.isNullOrBlank()) {
                                                        withContext(Dispatchers.Main) {
                                                            activeVideoUrl = direct
                                                            detectedVideoUrl = direct
                                                            isPlayerAdvancingNext = false
                                                            isAutoAdvancing = false
                                                            prefs.saveLastPlayback(nextUrl, nextEp, 0L)
                                                        }
                                                        return@launch
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(this@MainActivity, "Sei arrivato all'ultimo episodio trasmesso!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onPositionChanged = { pos ->
                                    val ep = OnePieceHelper.extractEpisodeNumber(currentWebUrl)
                                    prefs.saveLastPlayback(currentWebUrl, ep, pos)
                                },
                                onClose = {
                                    activeVideoUrl = null
                                    isPlayerAdvancingNext = false
                                }
                            )
                        }

                        // FLUID OPENING APP TRANSITION
                        AnimatedVisibility(
                            visible = isOpeningSplashVisible,
                            enter = fadeIn(),
                            exit = fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 1.05f, animationSpec = tween(400))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF070709)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    LogPoseCompassIcon(
                                        isSelected = true,
                                        iconSize = 64.dp
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "ONE PIECE",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 4.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Rotta Maggiore • Diario di Bordo",
                                        color = accentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
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
