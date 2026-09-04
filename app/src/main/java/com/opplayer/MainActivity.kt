package com.opplayer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Context
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
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PlaybackPreferences(this)

        setContent {
            MaterialTheme {
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
                var watchedEpisodes by remember { mutableStateOf(prefs.getWatchedEpisodes()) }
                var favoriteEpisodes by remember { mutableStateOf(prefs.getFavoriteEpisodes()) }
                var downloadedList by remember { mutableStateOf(getDownloadedFilesList()) }

                // Dialogs
                var showImportDialog by remember { mutableStateOf(false) }
                var importInputText by remember { mutableStateOf("") }
                var selectedEpisodeForDetail by remember { mutableStateOf<Int?>(null) }
                var showQuickJumpDialog by remember { mutableStateOf(false) }
                var quickJumpInput by remember { mutableStateOf("") }
                var showMarkUpToConfirmDialog by remember { mutableStateOf(false) }
                var showClearHistoryConfirmDialog by remember { mutableStateOf(false) }

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

                // UNIFIED ROBUST STREAM LAUNCHER
                fun playEpisode(targetEp: Int, fromPos: Long = 0L) {
                    currentEpisodeNumber = targetEp
                    startFromPosition = fromPos
                    val epUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, targetEp)
                    currentWebUrl = epUrl

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
                    bottomBar = {
                        if (activeVideoUrl == null) {
                            // ANIMATED FROSTED GLASS DOCK (Cool sliding pill indicator)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(32.dp),
                                    color = Color(0xF212121A),
                                    border = BorderStroke(1.dp, specularBorder),
                                    shadowElevation = 20.dp
                                ) {
                                    val tabs = listOf(
                                        Triple("Cinema", Icons.Default.Movie, 0),
                                        Triple("Registro", Icons.Default.Analytics, 1),
                                        Triple("Sito Web", Icons.Default.Public, 2),
                                        Triple("Download", Icons.Default.Folder, 3)
                                    )

                                    Box(modifier = Modifier.fillMaxWidth().height(68.dp)) {
                                        // Sliding indicator pill
                                        val pillPosition by animateFloatAsState(
                                            targetValue = currentTab.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = 0.72f,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "pillPos"
                                        )

                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val tabWidth = maxWidth / tabs.size
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = tabWidth * pillPosition)
                                                    .width(tabWidth)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                                    .clip(RoundedCornerShape(26.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.35f),
                                                                accentRed.copy(alpha = 0.15f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        brush = Brush.verticalGradient(
                                                            listOf(
                                                                accentRed.copy(alpha = 0.75f),
                                                                accentRed.copy(alpha = 0.20f)
                                                            )
                                                        ),
                                                        shape = RoundedCornerShape(26.dp)
                                                    )
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            tabs.forEach { (label, icon, idx) ->
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
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = label,
                                                            tint = if (isSelected) Color.White else Color(0xFF8E8E93),
                                                            modifier = Modifier.size(if (isSelected) 24.dp else 21.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = label,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else Color(0xFF8E8E93)
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
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
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
                                                // Header with Quick Jump button
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
                                                            fontSize = 12.sp,
                                                            letterSpacing = 2.sp
                                                        )
                                                        Text(
                                                            text = "Rotta Maggiore",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 28.sp
                                                        )
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = Color.White.copy(alpha = 0.09f),
                                                        border = BorderStroke(1.dp, specularBorder),
                                                        modifier = Modifier.iosSpringClick { showQuickJumpDialog = true }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.Search, contentDescription = "Cerca", tint = Color.White, modifier = Modifier.size(17.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Salta a...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                                                        // Play button with spring feedback
                                                        Surface(
                                                            shape = RoundedCornerShape(18.dp),
                                                            color = accentRed,
                                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                            shadowElevation = 8.dp,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(vertical = 15.dp),
                                                                horizontalArrangement = Arrangement.Center,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = if (savedPosition > 10_000L) "Riprendi da ${formatTime(savedPosition)}" else "Guarda Adesso",
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

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // Smooth Horizontal Carousel
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
                                                            shape = RoundedCornerShape(18.dp),
                                                            color = if (isSelected) accentRed.copy(alpha = 0.25f) else Color(0x99181822),
                                                            border = BorderStroke(1.5.dp, if (isSelected) accentRed else Color.White.copy(alpha = 0.12f)),
                                                            modifier = Modifier
                                                                .width(136.dp)
                                                                .iosSpringClick {
                                                                    selectedEpisodeForDetail = ep
                                                                }
                                                        ) {
                                                            Column(modifier = Modifier.padding(14.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "EP. $ep",
                                                                        color = Color.White,
                                                                        fontSize = 14.sp,
                                                                        fontWeight = FontWeight.ExtraBold
                                                                    )
                                                                    if (isFav) {
                                                                        Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(15.dp))
                                                                    }
                                                                }

                                                                Spacer(modifier = Modifier.height(8.dp))

                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(itemType.hexColor).copy(alpha = 0.20f),
                                                                    border = BorderStroke(1.dp, Color(itemType.hexColor).copy(alpha = 0.60f))
                                                                ) {
                                                                    Text(
                                                                        text = itemType.label,
                                                                        color = Color(itemType.hexColor),
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }

                                                                Spacer(modifier = Modifier.height(10.dp))

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = if (isWatched) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                                        contentDescription = null,
                                                                        tint = if (isWatched) Color(0xFF34C759) else Color.Gray,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = if (isWatched) "Visto" else "Non visto",
                                                                        color = if (isWatched) Color(0xFF34C759) else Color.Gray,
                                                                        fontSize = 10.sp
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
                                    // TAB 1: REGISTRO DI BORDO 3D COMPLETAMENTE RIORGANIZZATO
                                    val stats = remember(watchedEpisodes) {
                                        OnePieceHelper.calculateStats(watchedEpisodes.size)
                                    }
                                    var filterSelection by remember { mutableIntStateOf(0) }

                                    // Dynamic Pirate Rank Title based on episodes watched
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

                                    // Watch time stats: 23.5 minutes per episode
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

                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = goldAccent.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.40f))
                                                    ) {
                                                        Text(
                                                            text = "${"%.1f".format(percentageWatched)}% Completato",
                                                            color = goldAccent,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // 3D MASTER DEPTH STATS CARD (Glassmorphism + Dynamic Rank)
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(26.dp),
                                                    color = Color(0xF0151522),
                                                    border = BorderStroke(1.dp, specularBorder),
                                                    shadowElevation = 18.dp
                                                ) {
                                                    Column(modifier = Modifier.padding(20.dp)) {
                                                        // Rank banner
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Grado Navigatore:", color = Color.Gray, fontSize = 12.sp)
                                                            Text(text = pirateRank, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                                        }

                                                        Spacer(modifier = Modifier.height(14.dp))

                                                        // 4 Quad Metric Grid
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            // Box 1: Visti
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

                                                            // Box 2: Tempo
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
                                                            // Box 3: Ritmo
                                                            Surface(
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.White.copy(alpha = 0.05f),
                                                                border = BorderStroke(1.dp, specularBorder)
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Ritmo di Visione", color = Color.Gray, fontSize = 11.sp)
                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                    Text("${"%.1f".format(stats.first)} ep/gg", color = Color(0xFF34C759), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                                    Text("media calcolata", color = Color.Gray, fontSize = 10.sp)
                                                                }
                                                            }

                                                            // Box 4: Data Pareggio
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

                                                        // Tri-gradient progress bar
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

                                                // SMART QUICK ACTIONS (Mark up to current, Copy JSON, Restore)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Quick mark up to current
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = Color(0xFF34C759).copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.40f)),
                                                        modifier = Modifier
                                                            .weight(1.3f)
                                                            .iosSpringClick {
                                                                showMarkUpToConfirmDialog = true
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 11.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text("Visti fino all'Ep. $currentEpisodeNumber", color = Color(0xFF34C759), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }

                                                    // Copy backup
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = Color.White.copy(alpha = 0.08f),
                                                        border = BorderStroke(1.dp, specularBorder),
                                                        modifier = Modifier
                                                            .weight(0.9f)
                                                            .iosSpringClick {
                                                                val json = OnePieceHelper.exportToJson(watchedEpisodes, currentEpisodeNumber, savedPosition)
                                                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("OP_Backup", json)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(this@MainActivity, "Backup copiato negli appunti! 📋", Toast.LENGTH_SHORT).show()
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 11.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Backup", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }

                                                    // Restore
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = accentRed.copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.45f)),
                                                        modifier = Modifier
                                                            .weight(0.9f)
                                                            .iosSpringClick { showImportDialog = true }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 11.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = accentRed, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Ripristina", color = accentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(20.dp))

                                                // 3D FILTER CHIPS BAR (Smooth horizontal scroll)
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    val filters = listOf(
                                                        "Tutti (${OnePieceHelper.TOTAL_AIRING_EPISODES})",
                                                        "Visti (${watchedEpisodes.size}) ✅",
                                                        "Da Vedere (${OnePieceHelper.TOTAL_AIRING_EPISODES - watchedEpisodes.size}) ⏳",
                                                        "Preferiti (${favoriteEpisodes.size}) ⭐",
                                                        "Solo Canon 🟢",
                                                        "Filler 🔴"
                                                    )

                                                    items(filters.indices.toList()) { i ->
                                                        val isSelected = filterSelection == i
                                                        Surface(
                                                            shape = RoundedCornerShape(14.dp),
                                                            color = if (isSelected) accentRed else Color.White.copy(alpha = 0.07f),
                                                            border = if (isSelected) BorderStroke(1.dp, accentRed) else BorderStroke(1.dp, specularBorder),
                                                            modifier = Modifier.iosSpringClick { filterSelection = i }
                                                        ) {
                                                            Text(
                                                                text = filters[i],
                                                                color = if (isSelected) Color.White else Color.LightGray,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            // SAGAS 3D ACCORDION
                                            items(OnePieceHelper.SAGAS) { saga ->
                                                val totalInSaga = saga.range.count()
                                                val watchedInSaga = saga.range.count { watchedEpisodes.contains(it) }
                                                val isCompleted = watchedInSaga == totalInSaga
                                                val sagaPercent = (watchedInSaga.toFloat() / totalInSaga.toFloat())
                                                var isExpanded by remember { mutableStateOf(false) }
                                                val arrowRotation by animateFloatAsState(
                                                    targetValue = if (isExpanded) 180f else 0f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                    label = "arrowRot"
                                                )

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 5.dp)
                                                        .animateContentSize(
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow
                                                            )
                                                        ),
                                                    shape = RoundedCornerShape(22.dp),
                                                    color = Color(0xF2161622),
                                                    border = if (isCompleted) {
                                                        BorderStroke(1.2.dp, Color(0xFF34C759).copy(alpha = 0.60f))
                                                    } else {
                                                        BorderStroke(1.dp, specularBorder)
                                                    },
                                                    shadowElevation = 8.dp
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

                                                        // Micro progress bar inside saga card
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        LinearProgressIndicator(
                                                            progress = { sagaPercent },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(4.dp)
                                                                .clip(RoundedCornerShape(2.dp)),
                                                            color = if (isCompleted) Color(0xFF34C759) else accentRed,
                                                            trackColor = Color.White.copy(alpha = 0.10f)
                                                        )

                                                        if (isExpanded) {
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
                                                                    4 -> t == EpisodeType.MANGA_CANON || t == EpisodeType.ANIME_CANON
                                                                    5 -> t == EpisodeType.FILLER
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

                                2 -> {
                                    // TAB 2: SITO WEB ONE PIECE POWER
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
                                    // TAB 3: DOWNLOAD OFFLINE
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF070709))
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 18.dp),
                                            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
                                        ) {
                                            item {
                                                Text("Download Offline", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                                Text("File multimediali memorizzati sul dispositivo", color = Color.Gray, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(16.dp))

                                                if (downloadedList.isEmpty()) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 40.dp),
                                                        shape = RoundedCornerShape(24.dp),
                                                        color = Color(0x66181822),
                                                        border = BorderStroke(1.dp, specularBorder)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(28.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                                            Spacer(modifier = Modifier.height(14.dp))
                                                            Text(
                                                                text = "Nessun episodio in locale",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 17.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = "Tocca l'icona download nella barra del player durante la visione per salvare l'episodio e guardarlo senza connessione.",
                                                                color = Color.Gray,
                                                                fontSize = 12.sp,
                                                                textAlign = TextAlign.Center,
                                                                lineHeight = 17.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            items(downloadedList) { item ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 5.dp),
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = Color(0x99181822),
                                                    border = BorderStroke(1.dp, specularBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(text = "Episodio ${item.episodeNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                            Text(text = item.sizeMb, color = Color.Gray, fontSize = 12.sp)
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            IconButton(
                                                                onClick = {
                                                                    activeVideoUrl = item.file.absolutePath
                                                                    currentWebUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, item.episodeNumber)
                                                                },
                                                                modifier = Modifier.iosSpringClick {
                                                                    activeVideoUrl = item.file.absolutePath
                                                                    currentWebUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, item.episodeNumber)
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = accentRed)
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    item.file.delete()
                                                                    downloadedList = getDownloadedFilesList()
                                                                    Toast.makeText(this@MainActivity, "Episodio eliminato", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.iosSpringClick {
                                                                    item.file.delete()
                                                                    downloadedList = getDownloadedFilesList()
                                                                    Toast.makeText(this@MainActivity, "Episodio eliminato", Toast.LENGTH_SHORT).show()
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
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
                                                            text = if (isEpFav) "Preferito" else "Aggiungi ⭐",
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

                        // CONFIRM DIALOG: MARK UP TO CURRENT EPISODE
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
                                                        val res = OnePieceHelper.importFromJson(importInputText)
                                                        if (res != null) {
                                                            res.first.forEach { prefs.markEpisodeWatched(it, true) }
                                                            currentEpisodeNumber = res.second
                                                            val epUrl = OnePieceHelper.buildEpisodeUrl(currentWebUrl, res.second)
                                                            currentWebUrl = epUrl
                                                            prefs.saveLastPlayback(epUrl, res.second, res.third)
                                                            watchedEpisodes = prefs.getWatchedEpisodes()
                                                            webViewInstance?.loadUrl(epUrl)
                                                            Toast.makeText(this@MainActivity, "Dati ripristinati con successo!", Toast.LENGTH_SHORT).show()
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

                        // VIDEO PLAYER OVERLAY (Keeps player screen open seamlessly during next episode advancement)
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
                    }
                }
            }
        }
    }
}
