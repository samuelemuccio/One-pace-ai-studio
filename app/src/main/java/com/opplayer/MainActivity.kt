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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

class MainActivity : ComponentActivity() {

    private var isInPipModeState = mutableStateOf(false)
    private var isPlayerActive = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
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
        createNotificationChannel()
        StreakReminderReceiver.scheduleDailyReminder(this)
        val prefs = PlaybackPreferences(this)

        setContent {
            MaterialTheme {
                val savedEpisode = remember { prefs.getLastEpisode() }
                val defaultStartUrl = OnePieceHelper.buildEpisodeUrl("", savedEpisode)
                val savedUrl = remember { prefs.getLastUrl() ?: defaultStartUrl }
                val savedPosition = remember { prefs.getPositionForEpisode(savedEpisode) }

                var currentEpisodeNumber by remember { mutableIntStateOf(savedEpisode) }
                var currentWebUrl by remember { mutableStateOf(savedUrl) }
                var activeVideoUrl by remember { mutableStateOf<String?>(null) }
                var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }
                var startFromPosition by remember { mutableLongStateOf(0L) }
                var isAutoAdvancing by remember { mutableStateOf(false) }

                var isResolvingStream by remember { mutableStateOf(false) }
                var resolvingEpisodeNumber by remember { mutableIntStateOf(savedEpisode) }
                var isPlayerAdvancingNext by remember { mutableStateOf(false) }

                var currentTab by remember { mutableIntStateOf(0) }
                var watchedEpisodes by remember { mutableStateOf(prefs.getWatchedEpisodes()) }
                var favoriteEpisodes by remember { mutableStateOf(prefs.getFavoriteEpisodes()) }
                var downloadedList by remember { mutableStateOf(getDownloadedFilesList()) }

                var dailyStreak by remember { mutableIntStateOf(prefs.getStreak()) }
                val bountyBeli by remember(watchedEpisodes) { derivedStateOf { OnePieceHelper.calculateBounty(watchedEpisodes.size) } }
                val pirateRank by remember(watchedEpisodes) { derivedStateOf { OnePieceHelper.getPirateRank(watchedEpisodes.size) } }
                var selectedSagaIndex by remember {
                    mutableIntStateOf(
                        OnePieceHelper.SAGAS.indexOfFirst { savedEpisode in it.range }.coerceAtLeast(0)
                    )
                }
                var selectedFilterTag by remember { mutableStateOf("Tutti") }

                var showImportDialog by remember { mutableStateOf(false) }
                var importInputText by remember { mutableStateOf("") }
                var selectedEpisodeForDetail by remember { mutableStateOf<Int?>(null) }
                var showQuickJumpDialog by remember { mutableStateOf(false) }
                var quickJumpInput by remember { mutableStateOf("") }
                var showMarkUpToConfirmDialog by remember { mutableStateOf(false) }
                var showClearHistoryConfirmDialog by remember { mutableStateOf(false) }
                var showStreakReminderDialog by remember { mutableStateOf(false) }
                var showBountyExplainDialog by remember { mutableStateOf(false) }
                var showCloudSyncDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                val downloadManagerHelper = remember { DownloadManagerHelper(this@MainActivity) }

                val cloudSyncManager = remember { CloudSyncManager(this@MainActivity) }
                var cloudUserEmail by remember { mutableStateOf(cloudSyncManager.getUserEmail()) }
                var cloudLastSyncText by remember { mutableStateOf(cloudSyncManager.getLastSyncDateFormatted()) }
                var isAutoSyncEnabled by remember { mutableStateOf(cloudSyncManager.isAutoSyncEnabled()) }
                val coroutineScope = rememberCoroutineScope()

                // ========== FIX PATCH 5.3: sincronizza anche currentWebUrl ==========
                LaunchedEffect(Unit) {
                    val restored = cloudSyncManager.checkAutoRestoreOnStartup(prefs)
                    if (restored) {
                        watchedEpisodes = prefs.getWatchedEpisodes()
                        favoriteEpisodes = prefs.getFavoriteEpisodes()
                        val restoredEp = prefs.getLastEpisode()
                        currentEpisodeNumber = restoredEp
                        currentWebUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, restoredEp)
                        startFromPosition = prefs.getPositionForEpisode(restoredEp)
                        dailyStreak = prefs.getStreak()
                        webViewInstance?.loadUrl(currentWebUrl)
                        cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                        Toast.makeText(this@MainActivity, "Salvataggio ripristinato automaticamente dal dispositivo! 🏴‍☠️", Toast.LENGTH_LONG).show()
                    }
                }

                // ========== FIX PATCH 5.4: auto-save su IO + debounce ==========
                LaunchedEffect(watchedEpisodes, currentEpisodeNumber, dailyStreak) {
                    if (isAutoSyncEnabled) {
                        delay(800)
                        val json = OnePieceHelper.exportToJson(
                            watched = watchedEpisodes,
                            favorites = favoriteEpisodes,
                            lastEp = currentEpisodeNumber,
                            lastPos = prefs.getPositionForEpisode(currentEpisodeNumber),
                            streak = dailyStreak
                        )
                        withContext(Dispatchers.IO) {
                            cloudSyncManager.saveLocalPersistentVault(json)
                        }
                        cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        showEpisodeReminderNotification(currentEpisodeNumber, dailyStreak)
                        Toast.makeText(this@MainActivity, "Promemoria attivato con successo! 🏴‍☠️", Toast.LENGTH_SHORT).show()
                    }
                }

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

                // ========== FIX PATCH 5.2: non mettere in pausa il WebView ==========
                LaunchedEffect(activeVideoUrl) {
                    if (activeVideoUrl != null) {
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
                        showMarkUpToConfirmDialog -> {
                            showMarkUpToConfirmDialog = false
                        }
                        showClearHistoryConfirmDialog -> {
                            showClearHistoryConfirmDialog = false
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

                // ========== FIX PATCH 5.5: playEpisode con posizione per-episodio ==========
                fun playEpisode(targetEp: Int, fromPos: Long = 0L) {
                    currentEpisodeNumber = targetEp
                    val effectivePos = if (fromPos > 0L) fromPos else prefs.getPositionForEpisode(targetEp)
                    startFromPosition = effectivePos

                    val epUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, targetEp)
                    currentWebUrl = epUrl

                    val localFile = downloadedList.firstOrNull { it.episodeNumber == targetEp }
                    if (localFile != null && localFile.file.exists()) {
                        activeVideoUrl = localFile.file.absolutePath
                        prefs.saveLastPlayback(localFile.file.absolutePath, targetEp, effectivePos)
                        Toast.makeText(this@MainActivity, "Avvio da memoria locale 💾", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (detectedVideoUrl != null && isValidVideoStream(detectedVideoUrl!!) && currentWebUrl.contains("pagine/$targetEp")) {
                        activeVideoUrl = detectedVideoUrl
                        prefs.saveLastPlayback(epUrl, targetEp, effectivePos)
                        return
                    }

                    isResolvingStream = true
                    resolvingEpisodeNumber = targetEp
                    isAutoAdvancing = true

                    webViewInstance?.onResume()
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
                                    prefs.saveLastPlayback(epUrl, targetEp, effectivePos)
                                }
                                return@launch
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

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
                    bottomBar = {
                        if (activeVideoUrl == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    color = Color(0xD80E1220),
                                    border = BorderStroke(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.32f),
                                                Color.White.copy(alpha = 0.08f)
                                            )
                                        )
                                    ),
                                    shadowElevation = 24.dp
                                ) {
                                    val navItems = listOf(
                                        Pair(Icons.Default.PlayCircle, "Cinema"),
                                        Pair(Icons.Default.Explore, "Registro"),
                                        Pair(Icons.Default.Language, "Sito Web"),
                                        Pair(Icons.Default.FileDownload, "Download")
                                    )

                                    Box(modifier = Modifier.fillMaxWidth().height(62.dp)) {
                                        val pillPosition by animateFloatAsState(
                                            targetValue = currentTab.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = 0.75f,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "pillPos"
                                        )

                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val tabWidth = maxWidth / navItems.size
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = tabWidth * pillPosition)
                                                    .width(tabWidth)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.38f),
                                                                accentRed.copy(alpha = 0.15f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        brush = Brush.verticalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.90f),
                                                                accentRed.copy(alpha = 0.25f)
                                                            )
                                                        ),
                                                        shape = RoundedCornerShape(22.dp)
                                                    )
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            navItems.forEachIndexed { idx, (icon, label) ->
                                                val isSelected = currentTab == idx

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .iosSpringClick {
                                                            if (idx == 1) {
                                                                watchedEpisodes = prefs.getWatchedEpisodes()
                                                                favoriteEpisodes = prefs.getFavoriteEpisodes()
                                                            }
                                                            if (idx == 3) downloadedList = getDownloadedFilesList()
                                                            currentTab = idx
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Box(contentAlignment = Alignment.TopEnd) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = label,
                                                                tint = if (isSelected) Color.White else Color(0xFF8E8E93),
                                                                modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                                                            )
                                                            if (idx == 3 && downloadedList.isNotEmpty()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .offset(x = 6.dp, y = (-4).dp)
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(accentRed)
                                                                )
                                                            }
                                                        }

                                                        if (isSelected) {
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
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
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF070709))
                    ) {
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
                                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

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
                            modifier = Modifier.fillMaxSize()
                        )

                        if (currentTab != 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF070709))
                            )
                        }

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
                                    val currentSaga = OnePieceHelper.getSagaForEpisode(currentEpisodeNumber)
                                    val epType = OnePieceHelper.getEpisodeType(currentEpisodeNumber)
                                    val sagaEpisodes = remember(currentSaga) { currentSaga.range.toList() }
                                    val targetIndex = remember(currentEpisodeNumber, sagaEpisodes) {
                                        sagaEpisodes.indexOf(currentEpisodeNumber).coerceAtLeast(0)
                                    }
                                    val carouselState = rememberLazyListState()
                                    val isHeroFavorited = favoriteEpisodes.contains(currentEpisodeNumber)

                                    val upcomingEpisodes = remember(sagaEpisodes, currentEpisodeNumber) {
                                        sagaEpisodes.filter { it >= currentEpisodeNumber }.take(12)
                                    }

                                    LaunchedEffect(targetIndex) {
                                        carouselState.scrollToItem(targetIndex)
                                    }

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
                                            contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
                                        ) {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
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
                                                            fontSize = 26.sp
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(16.dp),
                                                            color = Color(0x33FFD700),
                                                            border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f)),
                                                            modifier = Modifier.iosSpringClick { showBountyExplainDialog = true }
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text("฿", color = goldAccent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = OnePieceHelper.formatBeli(bountyBeli.first),
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }

                                                        Surface(
                                                            shape = RoundedCornerShape(16.dp),
                                                            color = Color(0x33FF2A42),
                                                            border = BorderStroke(1.dp, accentRed.copy(alpha = 0.6f)),
                                                            modifier = Modifier.iosSpringClick {
                                                                showStreakReminderDialog = true
                                                            }
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text("🔥", fontSize = 11.sp)
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text(
                                                                    text = "$dailyStreak gg",
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = { showQuickJumpDialog = true },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.10f))
                                                        ) {
                                                            Icon(Icons.Default.Search, contentDescription = "Cerca", tint = Color.White, modifier = Modifier.size(18.dp))
                                                        }

                                                        IconButton(
                                                            onClick = { showSettingsDialog = true },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.10f))
                                                        ) {
                                                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni", tint = Color.White, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(18.dp))

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .iosSpringClick {
                                                            playEpisode(currentEpisodeNumber, startFromPosition)
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
                                                                modifier = Modifier.size(34.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isHeroFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                                                    contentDescription = "Preferito",
                                                                    tint = if (isHeroFavorited) goldAccent else Color.Gray,
                                                                    modifier = Modifier.size(22.dp)
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

                                                        Surface(
                                                            shape = RoundedCornerShape(18.dp),
                                                            color = Color.Transparent,
                                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)),
                                                            shadowElevation = 12.dp,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(
                                                                    Brush.horizontalGradient(
                                                                        listOf(
                                                                            Color(0xFFFF2D55),
                                                                            Color(0xFFE50914)
                                                                        )
                                                                    ),
                                                                    shape = RoundedCornerShape(18.dp)
                                                                )
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(vertical = 15.dp),
                                                                horizontalArrangement = Arrangement.Center,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = if (startFromPosition > 10_000L) "Riprendi da ${formatTime(startFromPosition)}" else "Guarda Adesso",
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 16.sp
                                                                )
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
                                                            color = if (isSelected) Color(0xFF1E1017) else Color(0xFF121520),
                                                            border = if (isSelected) {
                                                                BorderStroke(2.dp, Color(0xFFFF2D55))
                                                            } else {
                                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                                            },
                                                            shadowElevation = if (isSelected) 8.dp else 2.dp,
                                                            modifier = Modifier
                                                                .width(122.dp)
                                                                .height(146.dp)
                                                                .iosSpringClick {
                                                                    selectedEpisodeForDetail = ep
                                                                }
                                                        ) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(12.dp),
                                                                verticalArrangement = Arrangement.SpaceBetween
                                                            ) {
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

                                                                    if (isWatched) {
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

                                                                Box(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    contentAlignment = Alignment.CenterStart
                                                                ) {
                                                                    Text(
                                                                        text = "$ep",
                                                                        color = Color.White,
                                                                        fontSize = 32.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        letterSpacing = (-0.5).sp
                                                                    )
                                                                }

                                                                if (isSelected) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = Color(0xFF280B13),
                                                                        border = BorderStroke(1.dp, Color(0xFFFF2D55).copy(alpha = 0.70f)),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(vertical = 4.dp),
                                                                            horizontalArrangement = Arrangement.Center,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(12.dp))
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
                                                                        text = if (isWatched) "Visto" else "Ep. $ep",
                                                                        color = if (isWatched) Color(0xFF30D15B) else Color.Gray,
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
                                                val isCurrent = ep == currentEpisodeNumber
                                                val isWatched = watchedEpisodes.contains(ep)
                                                val isFav = favoriteEpisodes.contains(ep)
                                                val episodeType = OnePieceHelper.getEpisodeType(ep)

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (isCurrent) Color(0x33FF2A42) else Color(0x99181822),
                                                    border = if (isCurrent) BorderStroke(1.dp, accentRed) else BorderStroke(1.dp, specularBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .iosSpringClick {
                                                                selectedEpisodeForDetail = ep
                                                            }
                                                            .padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = if (isCurrent) accentRed else Color.White.copy(alpha = 0.10f),
                                                                modifier = Modifier.size(38.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(
                                                                        imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.Movie,
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
                                                                    text = if (isCurrent) "In corso di visione" else "Tocca per guardare o dettagli",
                                                                    color = Color.Gray,
                                                                    fontSize = 11.sp
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
                                    val stats = remember(watchedEpisodes) {
                                        OnePieceHelper.calculateStats(watchedEpisodes.size)
                                    }
                                    var filterSelection by remember { mutableIntStateOf(0) }

                                    val pirateRank = remember(watchedEpisodes.size) {
                                        when (watchedEpisodes.size) {
                                            in 0..61 -> "Mozzo dell'East Blue ⚓"
                                            in 62..206 -> "Pirata della Rotta Maggiore 🌊"
                                            in 207..516 -> "Supernova dei Dieci Pirati ⭐"
                                            in 517..891 -> "Veterano del Nuovo Mondo ⚔️"
                                            in 892..1085 -> "Grande Flotta di Cappello di Paglia 👑"
                                            else -> "Imperatore dei Mari / Gear 5 ⚡"
                                        }
                                    }

                                    val totalMinutesSpent = watchedEpisodes.size * 23.5
                                    val hoursSpent = (totalMinutesSpent / 60).toInt()
                                    val minsRemaining = (totalMinutesSpent % 60).toInt()
                                    val percentageWatched = (watchedEpisodes.size.toFloat() / OnePieceHelper.TOTAL_AIRING_EPISODES * 100f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF070709))
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 18.dp),
                                            contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
                                        ) {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("DIARIO DI BORDO", color = accentRed, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                                                        Text("Registro Saghe", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = goldAccent.copy(alpha = 0.15f),
                                                            border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.40f))
                                                        ) {
                                                            Text(
                                                                text = "${"%.2f".format(percentageWatched)}% Completato",
                                                                color = goldAccent,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = { showSettingsDialog = true },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.10f))
                                                        ) {
                                                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni", tint = Color.White, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(26.dp),
                                                    color = Color(0xF0151522),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    shadowElevation = 18.dp
                                                ) {
                                                    Column(modifier = Modifier.padding(20.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column {
                                                                Text("Grado Navigatore:", color = Color.Gray, fontSize = 11.sp)
                                                                Text(text = pirateRank, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(12.dp),
                                                                color = Color(0x33FFD700),
                                                                border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.5f)),
                                                                modifier = Modifier.iosSpringClick { showBountyExplainDialog = true }
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text("฿", color = goldAccent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(OnePieceHelper.formatBeli(bountyBeli.first), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Icon(Icons.Default.Info, contentDescription = "Spiegazione Beli e Barre", tint = goldAccent, modifier = Modifier.size(13.dp))
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(14.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Surface(
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.White.copy(alpha = 0.05f),
                                                                border = BorderStroke(1.dp, specularBorder)
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Episodi Visti", color = Color.Gray, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("${watchedEpisodes.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                                                    Text("su ${OnePieceHelper.TOTAL_AIRING_EPISODES}", color = Color.Gray, fontSize = 10.sp)
                                                                }
                                                            }

                                                            Surface(
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.White.copy(alpha = 0.05f),
                                                                border = BorderStroke(1.dp, specularBorder)
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Tempo Speso", color = Color.Gray, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("${hoursSpent}h ${minsRemaining}m", color = goldAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                                                    Text("a guardare Luffy", color = Color.Gray, fontSize = 10.sp)
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(10.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Surface(
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.White.copy(alpha = 0.05f),
                                                                border = BorderStroke(1.dp, specularBorder)
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Ritmo di Visione", color = Color.Gray, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("${"%.2f".format(stats.first)} ep/gg", color = Color(0xFF34C759), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                                    Text("media calcolata", color = Color.Gray, fontSize = 10.sp)
                                                                }
                                                            }

                                                            Surface(
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.White.copy(alpha = 0.05f),
                                                                border = BorderStroke(1.dp, specularBorder)
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Data Pareggio", color = Color.Gray, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text(stats.third, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    Text("in pari con anime", color = Color.Gray, fontSize = 10.sp)
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Progresso Globale Serie", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                            Text("${watchedEpisodes.size} di ${OnePieceHelper.TOTAL_AIRING_EPISODES} ep. (${"%.2f".format(percentageWatched)}%)", color = accentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        LinearProgressIndicator(
                                                            progress = { (percentageWatched / 100f).coerceIn(0f, 1f) },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(8.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            color = accentRed,
                                                            trackColor = Color.White.copy(alpha = 0.12f)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(14.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = Color(0xFF30D15B).copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color(0xFF30D15B).copy(alpha = 0.40f)),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .iosSpringClick {
                                                                showMarkUpToConfirmDialog = true
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF30D15B), modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Segna tutti gli episodi come visti fino all'Ep. $currentEpisodeNumber", color = Color(0xFF30D15B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(20.dp))

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

                                            items(OnePieceHelper.SAGAS) { saga ->
                                                val totalInSaga = saga.range.count()
                                                val watchedInSaga = saga.range.count { watchedEpisodes.contains(it) }
                                                val isCompleted = watchedInSaga == totalInSaga
                                                val sagaPercent = (watchedInSaga.toFloat() / totalInSaga.toFloat())
                                                var isExpanded by remember { mutableStateOf(false) }
                                                val arrowRotation by animateFloatAsState(
                                                    targetValue = if (isExpanded) 180f else 0f,
                                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                                    label = "arrowRot"
                                                )

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 5.dp),
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = Color(0xF2161622),
                                                    border = if (isCompleted) {
                                                        BorderStroke(1.2.dp, Color(0xFF34C759).copy(alpha = 0.60f))
                                                    } else {
                                                        BorderStroke(1.dp, specularBorder)
                                                    },
                                                    shadowElevation = 4.dp
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
                                                                        Text("🏆", fontSize = 13.sp)
                                                                    }
                                                                }
                                                                Text(text = "Ep. ${saga.range.first} - ${saga.range.last} • ${saga.description.take(45)}...", color = Color.Gray, fontSize = 11.sp)
                                                            }

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = "$watchedInSaga / $totalInSaga",
                                                                    color = if (isCompleted) Color(0xFF34C759) else Color.White,
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

                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Progresso Saga", color = Color.Gray, fontSize = 10.sp)
                                                            Text("$watchedInSaga / $totalInSaga ep. (${(sagaPercent * 100).toInt()}%)", color = if (isCompleted) Color(0xFF34C759) else Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(
                                                            progress = { sagaPercent },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(4.dp)
                                                                .clip(RoundedCornerShape(2.dp)),
                                                            color = if (isCompleted) Color(0xFF34C759) else accentRed,
                                                            trackColor = Color.White.copy(alpha = 0.10f)
                                                        )

                                                        AnimatedVisibility(
                                                            visible = isExpanded,
                                                            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                                                            exit = fadeOut(tween(140)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing))
                                                        ) {
                                                            Column {
                                                                Spacer(modifier = Modifier.height(14.dp))
                                                                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                                                                Spacer(modifier = Modifier.height(10.dp))

                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.End
                                                                ) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = Color.White.copy(alpha = 0.08f),
                                                                        border = BorderStroke(1.dp, specularBorder),
                                                                        modifier = Modifier.iosSpringClick {
                                                                            val markAll = !isCompleted
                                                                            saga.range.forEach { prefs.markEpisodeWatched(it, markAll) }
                                                                            watchedEpisodes = prefs.getWatchedEpisodes()
                                                                            Toast.makeText(
                                                                                this@MainActivity,
                                                                                if (markAll) "Saga ${saga.name} completata! ✅" else "Saga azzerata",
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                        }
                                                                    ) {
                                                                        Text(
                                                                            text = if (isCompleted) "Deseleziona tutta la saga" else "Segna tutta la saga come vista ✅",
                                                                            color = if (isCompleted) Color.Gray else Color(0xFF34C759),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                                        )
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.height(8.dp))

                                                                val filteredEpisodes = saga.range.filter { ep ->
                                                                    val t = OnePieceHelper.getEpisodeType(ep)
                                                                    when (filterSelection) {
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

                                                                if (filteredEpisodes.isEmpty()) {
                                                                    Text("Nessun episodio corrisponde al filtro attivo", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                                                }

                                                                filteredEpisodes.forEach { ep ->
                                                                    val isWatched = watchedEpisodes.contains(ep)
                                                                    val isFav = favoriteEpisodes.contains(ep)
                                                                    val itemEpType = OnePieceHelper.getEpisodeType(ep)

                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(vertical = 4.dp),
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
                                                                                onClick = { playEpisode(ep) },
                                                                                modifier = Modifier.size(28.dp)
                                                                            ) {
                                                                                Icon(Icons.Default.PlayArrow, contentDescription = "Guarda", tint = accentRed, modifier = Modifier.size(18.dp))
                                                                            }

                                                                            Checkbox(
                                                                                checked = isWatched,
                                                                                onCheckedChange = { checked ->
                                                                                    prefs.markEpisodeWatched(ep, checked)
                                                                                    watchedEpisodes = prefs.getWatchedEpisodes()
                                                                                },
                                                                                colors = CheckboxDefaults.colors(
                                                                                    checkedColor = Color(0xFF34C759),
                                                                                    uncheckedColor = Color.Gray
                                                                                )
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

                                2 -> {
                                    Box(modifier = Modifier.fillMaxSize()) {
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
                                                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(22.dp),
                                                color = accentRed,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                shadowElevation = 14.dp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .iosSpringClick { activeVideoUrl = detectedVideoUrl }
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

                                3 -> {
                                    DownloadTabContent(
                                        downloadManagerHelper = downloadManagerHelper,
                                        prefs = prefs,
                                        currentEpisodeNumber = currentEpisodeNumber,
                                        watchedEpisodes = watchedEpisodes,
                                        onPlayOfflineEpisode = { filePath, epNum ->
                                            activeVideoUrl = filePath
                                            currentEpisodeNumber = epNum
                                            currentWebUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, epNum)
                                        }
                                    )
                                }
                            }
                        }

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
                                                            text = if (isEpFav) "Preferito" else "Aggiungi ⭐",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

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
                                        }
                                    }
                                }
                            }
                        }

                        if (showMarkUpToConfirmDialog) {
                            Dialog(onDismissRequest = { showMarkUpToConfirmDialog = false }) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xF2161622),
                                    border = BorderStroke(1.dp, specularBorder),
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Column(modifier = Modifier.padding(22.dp)) {
                                        Text("Conferma Rapida", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "Vuoi segnare tutti gli episodi dall'Episodio 1 all'Episodio $currentEpisodeNumber come VISTI?",
                                            color = Color.LightGray,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showMarkUpToConfirmDialog = false },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Annulla", color = Color.LightGray)
                                            }
                                            Button(
                                                onClick = {
                                                    for (ep in 1..currentEpisodeNumber) {
                                                        prefs.markEpisodeWatched(ep, true)
                                                    }
                                                    watchedEpisodes = prefs.getWatchedEpisodes()
                                                    showMarkUpToConfirmDialog = false
                                                    Toast.makeText(this@MainActivity, "Episodi da 1 a $currentEpisodeNumber segnati come visti! ✅", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Conferma", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showQuickJumpDialog) {
                            val focusManager = LocalFocusManager.current
                            Dialog(
                                onDismissRequest = { showQuickJumpDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            showQuickJumpDialog = false
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
                                                Text("Salta a qualsiasi Episodio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                IconButton(onClick = { showQuickJumpDialog = false }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            OutlinedTextField(
                                                value = quickJumpInput,
                                                onValueChange = { quickJumpInput = it.filter { ch -> ch.isDigit() } },
                                                placeholder = { Text("Es. 1071 per Gear 5, 483...", color = Color.Gray) },
                                                modifier = Modifier.fillMaxWidth(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    focusManager.clearFocus()
                                                    val epNum = quickJumpInput.toIntOrNull()
                                                    if (epNum != null && epNum in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                                        showQuickJumpDialog = false
                                                        playEpisode(epNum)
                                                    }
                                                }),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = accentRed,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

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
                                                    val epNum = quickJumpInput.toIntOrNull()
                                                    if (epNum != null && epNum in 1..OnePieceHelper.TOTAL_AIRING_EPISODES) {
                                                        showQuickJumpDialog = false
                                                        playEpisode(epNum)
                                                    } else {
                                                        Toast.makeText(this@MainActivity, "Inserisci un numero da 1 a ${OnePieceHelper.TOTAL_AIRING_EPISODES}", Toast.LENGTH_SHORT).show()
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

                        if (showStreakReminderDialog) {
                            Dialog(
                                onDismissRequest = { showStreakReminderDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(RoundedCornerShape(26.dp)),
                                    color = Color(0xF0141620),
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

                        if (showBountyExplainDialog) {
                            Dialog(
                                onDismissRequest = { showBountyExplainDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(RoundedCornerShape(26.dp)),
                                    color = Color(0xF0141622),
                                    border = BorderStroke(1.dp, specularBorder),
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("฿", color = goldAccent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Taglia Pirata & Progresso",
                                                        color = Color.White,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Guida ai Beli e alle barre dell'app",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { showBountyExplainDialog = false }) {
                                                Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.Gray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = goldAccent.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "1. Cos'è la Taglia in Beli (฿)?",
                                                    color = goldAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "I Berries (Beli ฿) sono la valuta ufficiale del mondo di One Piece. In questa applicazione rappresentano la tua taglia pirata rilasciata dalla Marina, calcolata con 1.500.000 ฿ per ogni episodio visto.",
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "La tua taglia attuale: ${OnePieceHelper.formatBeli(bountyBeli.first)} ฿ (${watchedEpisodes.size} episodi visti)",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "Grado ottenuto: $pirateRank",
                                                    color = goldAccent,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF32ADE6).copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, Color(0xFF32ADE6).copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "2. A cosa fanno riferimento le Barre?",
                                                    color = Color(0xFF32ADE6),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "• Barra Globale nel Diario: calcola la percentuale esatta di episodi completati sull'intero anime (${watchedEpisodes.size} su ${OnePieceHelper.TOTAL_AIRING_EPISODES} ep. = ${"%.2f".format(OnePieceHelper.calculateStats(watchedEpisodes.size).second)}%).\n\n• Barre delle Saghe: ciascuna saga ha una sua barra dedicata che indica il progresso di quello specifico arco narrativo (es. quanti episodi hai visto su quelli totali della saga).",
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, specularBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "3. Tag Ufficiali degli Episodi",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "I tag sono unificati in denominazione inglese sia nei filtri che sul player:\n• Canon (Verde): Fedele al manga originale\n• Anime Canon (Azzurro): Episodi canonici esclusivi anime\n• Mixed (Ambra): Episodi con trama mista canon e filler\n• Filler (Rosso): Episodi riempitivi non canonici",
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Button(
                                            onClick = { showBountyExplainDialog = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Ho Capito, Salpiamo! 🏴‍☠️", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

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
                            onMarkAllWatchedUpToCurrent = {
                                for (ep in 1..currentEpisodeNumber) {
                                    prefs.markEpisodeWatched(ep, true)
                                }
                                watchedEpisodes = prefs.getWatchedEpisodes()
                                Toast.makeText(this@MainActivity, "Segnati come visti fino all'episodio $currentEpisodeNumber! ✅", Toast.LENGTH_SHORT).show()
                            },
                            onSyncSuccess = { email ->
                                cloudUserEmail = email
                                cloudLastSyncText = cloudSyncManager.getLastSyncDateFormatted()
                            }
                        )

                        // ========== FIX PATCH 5.1: WebView resume + gestione posizione ==========
                        activeVideoUrl?.let { videoUrl ->
                            VideoPlayerScreen(
                                videoUrl = videoUrl,
                                currentWebUrl = currentWebUrl,
                                initialPositionMs = startFromPosition,
                                isInPipMode = isInPipModeState.value,
                                isAdvancingNext = isPlayerAdvancingNext,
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

                                        // FIX: WebView sempre attivo quando deve caricare
                                        webViewInstance?.onResume()

                                        val localNext = downloadedList.firstOrNull { it.episodeNumber == nextEp }
                                        if (localNext != null && localNext.file.exists()) {
                                            activeVideoUrl = localNext.file.absolutePath
                                            isPlayerAdvancingNext = false
                                            prefs.saveLastPlayback(localNext.file.absolutePath, nextEp, 0L)
                                            Toast.makeText(this@MainActivity, "Riproduzione locale Ep. $nextEp 💾", Toast.LENGTH_SHORT).show()
                                        } else {
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
                                    prefs.savePositionForEpisode(ep, pos)
                                },
                                onClose = {
                                    activeVideoUrl = null
                                    isPlayerAdvancingNext = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
