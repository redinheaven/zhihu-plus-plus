/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.data.HistoryStorage
import com.github.zly2006.zhihu.data.networkAvailable
import com.github.zly2006.zhihu.data.networkReconnected
import com.github.zly2006.zhihu.data.offlineImagePrefetcher
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.nlp.JevKeywordSemanticMatcher
import com.github.zly2006.zhihu.nlp.JevKeywordWeightExtractor
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.theme.AndroidThemeSettings
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidArticleNavigationHandoff
import com.github.zly2006.zhihu.ui.AndroidZhihuMain
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnCommand
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.getHighestQualityVideoUrl
import com.github.zly2006.zhihu.ui.subscreens.PREF_VOLUME_KEY_PAGE_TURN
import com.github.zly2006.zhihu.ui.subscreens.syncCloudFileIfDue
import com.github.zly2006.zhihu.util.EmojiManager
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuCredentialRefresher
import com.github.zly2006.zhihu.util.clearShareImageCache
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordSemanticMatcher
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordWeightExtractor
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    lateinit var history: HistoryStorage
    val httpClient
        get() = com.github.zly2006.zhihu.account
            .androidZhihuAccountStore(this)
            .client
            .httpClient()

    /** 主返回栈控制器，承载 MainTabs 主壳和单栏页面。 */
    lateinit var navController: NavHostController
    private val pageTurnDispatcher = PageTurnDispatcher()
    private var currentMainTabOpenFrom: String? = null
    var mainTabNavigationTarget by mutableStateOf<TopLevelDestination?>(null)
        private set

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught exception", e)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri
                    .Builder()
                    .apply {
                        scheme("https")
                        authority("zhihu-plus.internal")
                        appendPath("error")
                        appendQueryParameter("title", "Uncaught exception: ${e.message}")
                        appendQueryParameter(
                            "message",
                            e.message,
                        )
                        appendQueryParameter("stack", e.stackTraceToString())
                    }.build(),
                this,
                MainActivity::class.java,
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        enableEdgeToEdgeCompat()
        super.onCreate(savedInstanceState)
        clearShareImageCache(this)
        history = HistoryStorage(this)
        AccountData.loadData(this)
        AndroidThemeSettings.initialize(this)
        androidKeywordSemanticMatcher = JevKeywordSemanticMatcher(androidSettingsStore(this))
        androidKeywordWeightExtractor = JevKeywordWeightExtractor(androidSettingsStore(this))
        registerNetworkReconnectCallback()
        offlineImagePrefetcher = { urls ->
            // 离线阅读的图片预下载只在 Wi-Fi 等不按流量计费的网络下进行；只为写入磁盘缓存，不保留内存、按极小尺寸解码
            val appContext = applicationContext
            if (urls.isNotEmpty() && getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false) {
                val imageLoader = SingletonImageLoader.get(appContext)
                urls.forEach { url ->
                    imageLoader.enqueue(
                        ImageRequest
                            .Builder(appContext)
                            .data(url)
                            .size(1, 1)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build(),
                    )
                }
            }
        }
        getContentFilterDatabase(this)

        val settings = androidSettingsStore(this)
        val lastLaunchTimestamp = settings.getLong(KEY_LAST_LAUNCH_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (now - lastLaunchTimestamp >= TimeUnit.DAYS.toMillis(1)) {
            val client = httpClient
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val refreshToken = ZhihuCredentialRefresher.fetchRefreshToken(client)
                    ZhihuCredentialRefresher.refreshZhihuToken(refreshToken, client)
                    Log.i(TAG, "Zhihu token refreshed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh Zhihu token", e)
                    androidUserMessageSink(this@MainActivity)
                        .showLongMessage("刷新登录状态失败，如多次看到此提示请重新登录")
                }
            }
        }
        settings.putLong(KEY_LAST_LAUNCH_TIMESTAMP, now)

        // 初始化emoji管理器
        lifecycleScope.launch {
            try {
                EmojiManager
                    .initialize(this@MainActivity)
                Log.i(TAG, "Emoji manager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize emoji manager", e)
            }
        }

        setContent {
            navController = rememberNavController()
            ZhihuTheme {
                CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        AndroidZhihuMain(navController = navController)
                        PageTurnFab(dispatcher = pageTurnDispatcher)
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                syncCloudFileIfDue(this@MainActivity)?.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "数据自动同步失败：${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (intent.data != null) {
                if (intent.data!!.authority == "zhihu-plus.internal") {
                    if (intent.data!!.path == "/error") {
                        val title = intent.data!!.getQueryParameter("title")
//                        val message = intent.data!!.getQueryParameter("message")
                        val stack = intent.data!!.getQueryParameter("stack")
                        AlertDialog
                            .Builder(this)
                            .apply {
                                setTitle(title)
                                setMessage(stack)
                                setPositiveButton("OK") { _, _ ->
                                }
                                setNeutralButton("Copy") { _, _ ->
                                    val clip = ClipData.newPlainText("error", "$stack")
                                    clipboardManager.setPrimaryClip(clip)
                                }
                            }.create()
                            .show()
                    }
                }
            }
        }

        ImageLoader
            .Builder(this)
            .crossfade(true)
            .components {
                // add(SvgDecoder.Factory())
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(this, 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300L * 1024 * 1024) // 300 MB，配合离线阅读保留看过的图片
                    .build()
            }.build()
            .also { loader ->
                SingletonImageLoader.setSafe {
                    loader
                }
            }
    }

    private var pageTurnLongPressConsumed = false

    private fun pageTurnCommand(keyCode: Int): PageTurnCommand? = when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> PageTurnCommand.PageDown
        KeyEvent.KEYCODE_PAGE_UP -> PageTurnCommand.PageUp
        KeyEvent.KEYCODE_VOLUME_DOWN ->
            if (androidSettingsStore(this).getBoolean(PREF_VOLUME_KEY_PAGE_TURN, false)) {
                PageTurnCommand.PageDown
            } else {
                null
            }
        KeyEvent.KEYCODE_VOLUME_UP ->
            if (androidSettingsStore(this).getBoolean(PREF_VOLUME_KEY_PAGE_TURN, false)) {
                PageTurnCommand.PageUp
            } else {
                null
            }
        else -> null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = pageTurnCommand(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (!pageTurnDispatcher.hasActiveTarget) return super.dispatchKeyEvent(event)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when {
                    event.isLongPress -> {
                        pageTurnLongPressConsumed = true
                        pageTurnDispatcher.dispatch(
                            if (command == PageTurnCommand.PageDown) {
                                PageTurnCommand.JumpToBottom
                            } else {
                                PageTurnCommand.JumpToTop
                            },
                        )
                    }
                    event.repeatCount == 0 -> {
                        pageTurnLongPressConsumed = false
                        true
                    }
                    else -> true
                }
            }
            KeyEvent.ACTION_UP -> {
                val consumed = pageTurnLongPressConsumed || pageTurnDispatcher.dispatch(command)
                pageTurnLongPressConsumed = false
                consumed
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            if (!handleIntentData(intent)) {
                // read clipboard
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text
                    if (text != null) {
                        val regex = Regex("""https?://[-a-zA-Z0-9@:%_+.~#?&/=]*""")
                        val destination = regex.findAll(text).firstNotNullOfOrNull {
                            resolveContent(it.value)
                        }
                        if (destination != null && destination != AndroidArticleNavigationHandoff.clipboardDestination) {
                            AndroidArticleNavigationHandoff.markClipboardDestination(destination)
                            navigate(destination, popup = true)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntentData(intent)
        }
    }

    private fun handleIntentData(incomingIntent: Intent): Boolean {
        val data = incomingIntent.data ?: return false
        if (data.authority == "zhihu-plus.internal") return true

        Log.i(TAG, "Intent data: $data")
        val destination = resolveContent(data.toString())
        if (destination != null) {
            if (destination != AndroidArticleNavigationHandoff.clipboardDestination) {
                AndroidArticleNavigationHandoff.markClipboardDestination(destination)
                navigate(destination, popup = true)
            }
        } else {
            AlertDialog
                .Builder(this)
                .apply {
                    setTitle("Unsupported URL")
                    setMessage("Unknown URL: $data")
                    setPositiveButton("OK") { _, _ -> }
                }.create()
                .show()
        }
        return true
    }

    /**
     * 通过主返回栈打开页面；popup 可替换当前外部跳转页面。
     *
     * @param route 要打开的页面
     * @param popup 是否替换当前外部跳转页面
     */
    fun navigate(route: NavDestination, popup: Boolean = false) {
        navigate(route, navController, popup)
    }

    /**
     * 通过 [targetController] 打开页面；分屏时该控制器属于右侧详情栏。
     *
     * @param route 要在目标栏中打开的页面
     * @param targetController 持有目标页面返回栈的控制器
     */
    fun navigateIn(route: NavDestination, targetController: NavHostController) {
        navigate(route, targetController, popup = false)
    }

    /**
     * 通过指定返回栈打开页面。主控制器承载主壳和列表，详情控制器承载大屏右侧内容。
     */
    private fun navigate(
        route: NavDestination,
        targetController: NavHostController,
        popup: Boolean,
    ) {
        if (route is CommentHolder) {
            AndroidArticleNavigationHandoff.prepareComment(route)
            navigate(route.article, targetController, popup)
            return
        }
        AndroidArticleNavigationHandoff.clearCommentUnless(route)
        preparePendingContentOpen(route, targetController)
        history.add(route)
        if (route is Video) {
            val current = runCatching {
                targetController.currentBackStackEntry?.toRoute<Article>()
            }.getOrNull() ?: runCatching {
                targetController.currentBackStackEntry?.toRoute<Question>()
            }.getOrNull()
            if (current == null) {
                androidUserMessageSink(this).showShortMessage("无法打开视频：未知的内容类型")
                return
            }
            val (contentId, contentType) = when (current) {
                is Article -> {
                    current.id.toString() to when (current.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                }
                is Question -> {
                    current.questionId.toString() to "question"
                }
                else -> error("Unsupported content type for video: $current")
            }
            CoroutineScope(Dispatchers.Main).launch {
                val videoUrl = getHighestQualityVideoUrl(this@MainActivity, httpClient, route.id.toString(), contentId, contentType)
                if (videoUrl == null) {
                    androidUserMessageSink(this@MainActivity).showShortMessage("获取视频链接失败")
                    return@launch
                }
                startActivity(
                    Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                        putExtra("video_url", videoUrl)
                        putExtra("video_id", route.id)
                    },
                )
            }
            return
        }
        if (route == MainTabs) {
            mainTabNavigationTarget = Home
            navigateToMainTabs()
            return
        }
        targetController.navigate(route) {
            // A secondary NavHost scopes content ViewModels by back-stack entry. Reusing
            // the same Article destination here keeps the old entry-scoped article alive
            // when a different feed item is selected.
            launchSingleTop = popup
            if (popup) {
                popUpTo(MainTabs) {
                    // clear the back stack and viewModels
                    saveState = true
                }
            }
        }
    }

    /** [sourceController] 提供触发导航的来源页面，用于记录内容打开来源。 */
    private fun preparePendingContentOpen(
        target: NavDestination,
        sourceController: NavHostController,
    ) {
        val openFrom = if (
            runCatching { navController.currentBackStackEntry?.toRoute<MainTabs>() }.getOrNull() != null
        ) {
            currentMainTabOpenFrom
        } else {
            null
        }
            ?: ContentOpenEventSupport.inferOpenFrom(currentContentOpenSource(sourceController), target)
        AndroidArticleNavigationHandoff.prepareContentOpen(target, openFrom)
    }

    private fun navigateToMainTabs() {
        navController.navigate(MainTabs) {
            launchSingleTop = true
            restoreState = true
            popUpTo(MainTabs) {
                saveState = true
            }
        }
    }

    fun setCurrentMainTabOpenFrom(openFrom: String?) {
        currentMainTabOpenFrom = openFrom
    }

    fun consumeMainTabNavigationTarget(destination: TopLevelDestination) {
        if (mainTabNavigationTarget == destination) {
            mainTabNavigationTarget = null
        }
    }

    /** 从指定返回栈的当前页面读取内容打开来源，支持右侧详情栏。 */
    private fun currentContentOpenSource(controller: NavHostController = navController): NavDestination? {
        val currentEntry = controller.currentBackStackEntry
        return runCatching {
            currentEntry?.toRoute<Article>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Question>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Pin>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<CollectionContent>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<History>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Notification>()
        }.getOrNull()
    }

    @Suppress("unused")
    companion object {
        private const val KEY_LAST_LAUNCH_TIMESTAMP = "last_main_launch_timestamp"
        const val IOS = "5_2.0"
        const val ANDROID = "4_2.0"
        const val WEB = "3_2.0"
        const val ZSE93 = ZHIHU_WEB_ZSE93
        const val TAG = "MainActivity"
    }
}

private var networkReconnectCallbackRegistered = false

/**
 * 监听系统网络：维护 [networkAvailable]，从不可上网变为已验证可上网时发出 [networkReconnected]。
 * 进程内只注册一次，Activity 重建不重复注册。
 */
private fun Context.registerNetworkReconnectCallback() {
    if (networkReconnectCallbackRegistered) return
    networkReconnectCallbackRegistered = true
    val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
    // 开机就没网时不会有任何回调，先按当前状态初始化
    networkAvailable.value = connectivityManager.activeNetwork
        ?.let(connectivityManager::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    connectivityManager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val nowValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (nowValidated && !networkAvailable.value) networkReconnected.tryEmit(Unit)
                networkAvailable.value = nowValidated
            }

            override fun onLost(network: Network) {
                networkAvailable.value = false
            }
        },
    )
}
