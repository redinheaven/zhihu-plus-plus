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

package com.github.zly2006.zhihu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkUnreadChatAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZHIHU_ME_NOTIFICATIONS_INCLUDE
import com.github.zly2006.zhihu.data.ZHIHU_ME_URL
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuMeNotifications
import com.github.zly2006.zhihu.data.cachedContentDetail
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.networkReconnected
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.filter.RemoteHistorySync
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberAppPrivateDirectory
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.BlockByKeywordsDialog
import com.github.zly2006.zhihu.ui.components.BlockTopicMenuItems
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.feedKeywordExtractionAvailable
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.blockableQuestion
import com.github.zly2006.zhihu.viewmodel.feed.replaceHomeFeedItemsWithFilteredResult
import com.github.zly2006.zhihu.viewmodel.fetchContentDetail
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import com.github.zly2006.zhihu.viewmodel.local.LocalHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PREFERENCE_NAME = "com.github.zly2006.zhihu_preferences"
const val ARTICLE_USE_WEBVIEW_PREFERENCE_KEY = "webviewRenderLegacy"
const val HOME_TOP_ACTIONS_TAG = "home_top_actions"
private const val SHOW_HOME_CREATE_FAB = false
const val HOME_SEARCH_BUTTON_TAG = "home_search_button"
const val HOME_CREATE_FAB_TAG = "home_create_fab"
const val HOME_CREATE_MENU_TAG = "home_create_menu"
const val HOME_WRITE_QUESTION_BUTTON_TAG = "home_write_question_button"
const val HOME_WRITE_ANSWER_BUTTON_TAG = "home_write_answer_button"
const val HOME_WRITE_PIN_BUTTON_TAG = "home_write_pin_button"
const val HOME_NOTIFICATION_BUTTON_TAG = "home_notification_button"
const val HOME_NOTIFICATION_BUTTON_CONTENT_TAG = "home_notification_button_content"
const val HOME_NOTIFICATION_BADGE_TAG = "home_notification_badge"
const val HOME_ACCOUNT_BUTTON_TAG = "home_account_button"
const val HOME_FEED_LIST_TAG = "home_feed_list"
const val HOME_REFRESH_BUTTON_TAG = "home_refresh_button"

// Pager can dispose this page while its feed ViewModels survive. Keep the header rows and
// synchronization owner alive too, so restoring a saved list index does not shift the visible card.
private class HomeScreenState(
    settings: SettingsStore,
) : ViewModel() {
    val remoteHistory = RemoteHistorySync(settings)

    /** 本次运行里已做过初次加载的推荐模式：之后列表即使空了也不自动刷新，只等用户手动刷新 */
    val initialLoadedModes = mutableSetOf<RecommendationMode>()

    /** 刷新时换上的预取实例及其推荐模式；为空或模式不符时用 viewModel() 创建的初始实例 */
    var swappedFeed by mutableStateOf<Pair<RecommendationMode, BaseFeedViewModel>?>(null)
        private set

    /**
     * 阅读期间预取的下一轮刷新。用户的节奏是「刷新—阅读—刷新」，阅读时网络空闲：回到列表时新建一个同类
     * ViewModel 在后台照常加载、过滤（含详情和 Jev），刷新时直接换上，免去等网络和过滤。
     * 只留一份，不设有效期（用户不在乎实时性）；已展示只在卡片滚进屏幕时记（见 [markSeen]），预取的不会被当成看过。
     */
    var prefetchedFeed by mutableStateOf<Pair<RecommendationMode, BaseFeedViewModel>?>(null)
        private set

    private fun newDeferredFeed(mode: RecommendationMode): BaseFeedViewModel? = when (mode) {
        RecommendationMode.WEB -> HomeFeedViewModel()
        RecommendationMode.LOCAL -> null
    }

    fun prefetch(mode: RecommendationMode, environment: PaginationEnvironment) {
        val pending = prefetchedFeed?.takeIf { it.first == mode }?.second
        // 已在加载或已加载好就不重复预取；报错或加载完却是空的才重来
        if (pending != null && pending.errorMessage == null && (pending.isLoading || pending.displayItems.isNotEmpty())) return
        if (environment.feedDisplaySettings().reverseBlock) return
        val feed = newDeferredFeed(mode) ?: return
        prefetchedFeed = mode to feed
        feed.refresh(environment)
    }

    /** 进程被杀后切回时，把存在文件里的预取批次放回内存，留给下次手动刷新 */
    fun adoptPrefetched(mode: RecommendationMode, items: List<FeedDisplayItem>) {
        val feed = newDeferredFeed(mode) ?: return
        feed.addDisplayItems(items)
        prefetchedFeed = mode to feed
    }

    /** 有预取好的下一轮就换上并返回 true；没有或还没加载出内容时返回 false，由调用方正常刷新。 */
    suspend fun swapInPrefetched(mode: RecommendationMode, environment: PaginationEnvironment): Boolean {
        val feed = prefetchedFeed?.takeIf { it.first == mode }?.second?.takeIf { it.displayItems.isNotEmpty() } ?: return false
        prefetchedFeed = null
        // 再跑一遍前台已读过滤：去掉预取之后在旧列表里划到过、打开过的
        val unread = remoteHistory.filterRemoteReads(environment.applyForegroundHomeFeedFilter(feed.displayItems.toList()))
        if (unread.isEmpty()) return false
        val unreadKeys = unread.mapTo(hashSetOf()) { it.stableKey }
        feed.displayItems.removeAll { it.stableKey !in unreadKeys }
        swappedFeed = mode to feed
        // 预取之后可能新加了屏蔽规则，换上后在后台再过一遍；可见卡片照常延后移除
        refilter(feed, environment, protectVisible = true)
        return true
    }

    /** 本次运行滚进过屏幕的卡片 */
    private val seenKeys = hashSetOf<String>()
    private val pendingTouch = linkedSetOf<Pair<String, String>>()
    private var touchJob: Job? = null

    /**
     * 卡片真正滚进屏幕才算看过：本地记为已展示（以后不再推），还向知乎上报 touch（攒 3 秒一起报）。
     * 加载了但没划到就被刷新掉的卡片什么都不记，以后还能被推回来。
     */
    fun markSeen(items: List<FeedDisplayItem>, mode: RecommendationMode, environment: PaginationEnvironment) {
        if (mode == RecommendationMode.LOCAL) return
        val newItems = items.filter { seenKeys.add(it.stableKey) }
        if (newItems.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) { runCatching { environment.recordHomeFeedViews(newItems) } }
        newItems.filterNot { it.isFiltered }.mapNotNullTo(pendingTouch) { item ->
            when (val target = item.feed?.target) {
                is Feed.AnswerTarget -> "answer" to target.id.toString()
                is Feed.ArticleTarget -> "article" to target.id.toString()
                is Feed.PinTarget -> "pin" to target.id.toString()
                else -> null
            }
        }
        touchJob?.cancel()
        touchJob = viewModelScope.launch {
            delay(3000)
            if (pendingTouch.isEmpty() || environment.authenticatedCookies()["d_c0"] == null) return@launch
            val batch = pendingTouch.toList()
            try {
                // 网页端首页上报 touch 不带 zse 签名，和浏览器保持一致
                val response = environment.httpClient().post("https://www.zhihu.com/lastread/touch") {
                    header("x-requested-with", "fetch")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("items", ZhihuJson.json.encodeToString(batch.map { (type, id) -> listOf(type, id, "touch") }))
                            },
                        ),
                    )
                }
                if (response.status.isSuccess()) {
                    pendingTouch.removeAll(batch.toSet())
                } else {
                    Log.e("Browse-Touch", response.bodyAsText())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                environment.handleFetchFailure("HomeScreen", e)
            }
        }
    }

    /**
     * 按当前屏蔽规则在后台重新过滤 [feed] 的列表（详情和 Jev 结果大多命中缓存），不刷新。
     * [protectVisible] 为 false 时命中的可见卡片也立刻移除（用户刚加了屏蔽规则，期待马上生效）。
     */
    fun refilter(feed: BaseFeedViewModel, environment: PaginationEnvironment, protectVisible: Boolean) {
        // 只重过滤已经过滤过的卡片
        val items = feed.displayItems.filter { it.feed != null || it.raw != null }
        viewModelScope.launch(Dispatchers.Default) {
            val filtered = runCatching { environment.applyBackgroundHomeFeedFilter(items) }.getOrElse { return@launch }
            withContext(Dispatchers.Main) {
                feed.listSync.pendingRemovalKeys += feed.displayItems.replaceHomeFeedItemsWithFilteredResult(
                    HomeFeedFilterResult(foregroundItems = items, filteredItems = filtered, reverseBlock = false),
                    if (protectVisible) feed.listSync.visibleItemKeys else emptySet(),
                )
            }
        }
    }
}

/**
 * 首页信息流页面。
 *
 * 页面顶部承载搜索、通知、账号入口等高频操作，主体是可分页的推荐信息流，底部可按设置显示可拖动刷新 FAB。
 * 设计上首页同时响应推荐算法、Duo3 账号入口迁移和未读通知等状态，因此 UI 改动时要同时检查
 * `recommendationMode`、`duo3_home_account`、`showRefreshFab` 和账号面板相关路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    showTopActions: Boolean = true,
    isActive: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val baseEnvironment = rememberPaginationEnvironment(allowGuestAccess = true)
    val settings = rememberSettingsStore()
    val homeState: HomeScreenState = viewModel { HomeScreenState(settings) }
    val remoteHistory = homeState.remoteHistory
    val paginationEnvironment = remember(baseEnvironment, remoteHistory) { remoteHistory.feedEnvironment(baseEnvironment) }
    val appPrivateDirectory = rememberAppPrivateDirectory()
    val notificationSettings = rememberNotificationSettingsStore()
    val userMessages = rememberUserMessageSink()
    val lifecycleOwner = LocalLifecycleOwner.current

    val duo3HomeAccount = settings.getBoolean("duo3_home_account", false)
    val showRefreshFab = settings.getBoolean("showRefreshFab", false)
    val autoRefreshOnStartup = settings.getBoolean(AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, true)
    val showUnreadBadge = notificationSettings.getUnreadBadgeEnabled()
    var showAccountBottomSheet by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    val createMenuBlurRadius by animateDpAsState(
        targetValue = if (showCreateMenu) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "createMenuBlurRadius",
    )

    // 获取当前推荐算法设置
    val currentRecommendationMode =
        RecommendationMode.entries.find {
            it.key == settings.getString("recommendationMode", RecommendationMode.WEB.key)
        } ?: RecommendationMode.WEB
    val startupCacheFile = remember(appPrivateDirectory, currentRecommendationMode) {
        Path(appPrivateDirectory, homeFeedStartupCacheFileName(currentRecommendationMode))
    }

    val account = rememberAccountSettingsAccountState().value
    if (account.login && !account.hasRequiredCookie) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Cookie 不完整") },
            text = { Text("当前登录信息缺少必要的 Cookie d_c0，请重新登录。") },
            confirmButton = {
                TextButton(onClick = ::requestLoginNavigation) {
                    Text("重新登录")
                }
            },
        )
    }
    val isDebuggable = rememberHomeIsDebuggable()
    val initialViewModel: BaseFeedViewModel = when (currentRecommendationMode) {
        RecommendationMode.WEB -> viewModel { HomeFeedViewModel() }
        RecommendationMode.LOCAL -> viewModel { LocalHomeFeedViewModel() }
    }
    val viewModel = homeState.swappedFeed?.takeIf { it.first == currentRecommendationMode }?.second ?: initialViewModel
    val latestViewModel by rememberUpdatedState(viewModel)
    val prefetchCacheFile = remember(appPrivateDirectory, currentRecommendationMode) {
        Path(appPrivateDirectory, homeFeedPrefetchCacheFileName(currentRecommendationMode))
    }
    val currentListCacheFile = remember(appPrivateDirectory, currentRecommendationMode) {
        Path(appPrivateDirectory, homeFeedCurrentListCacheFileName(currentRecommendationMode))
    }

    // 进程在后台被系统杀掉、切回时由系统恢复的为 true（保存状态带回来的），全新启动为 false
    var homeShownBefore by rememberSaveable { mutableStateOf(false) }
    val restoredAfterProcessDeath = remember { homeShownBefore }
    SideEffect { homeShownBefore = true }

    // 写在 homeState 的协程里：切到回答页时首页会离开组合，不能被一起取消
    fun writeFile(file: Path, serialized: String) {
        homeState.viewModelScope.launch(Dispatchers.Default) {
            runCatching { SystemFileSystem.sink(file).buffered().use { it.writeString(serialized) } }
        }
    }
    val localHomeViewModel = viewModel as? LocalHomeFeedViewModel

    val listState = rememberLazyListState()
    val refreshScope = rememberCoroutineScope()

    // 刷新：有阅读期间预取好的下一轮就直接换上并回到顶部，否则正常刷新
    suspend fun refreshFeed(pull: Boolean) {
        if (homeState.swapInPrefetched(currentRecommendationMode, paginationEnvironment)) {
            listState.scrollToItem(0)
            withContext(Dispatchers.Default) { runCatching { SystemFileSystem.delete(prefetchCacheFile, mustExist = false) } }
        } else if (pull) {
            viewModel.pullToRefresh(paginationEnvironment)
        } else {
            viewModel.refresh(paginationEnvironment)
        }
    }

    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }
    LaunchedEffect(scrollToTopTrigger) {
        when (
            topLevelReselectAction(
                triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
                isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            )
        ) {
            TopLevelReselectAction.Refresh -> refreshFeed(pull = false)
            TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
            null -> {}
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }

    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            unreadCount = paginationEnvironment
                .fetchJson(ZHIHU_ME_URL, ZHIHU_ME_NOTIFICATIONS_INCLUDE)
                ?.let { ZhihuJson.decodeJson<ZhihuMeNotifications>(it) }
                ?.let { it.totalCount + it.messagesCount } ?: 0
        } catch (_: Exception) {
            // 忽略错误
        }
    }

    val latestLoadedDisplayItems = viewModel.latestLoadedDisplayItems.value
    LaunchedEffect(latestLoadedDisplayItems) {
        if (latestLoadedDisplayItems.isNotEmpty()) {
            encodeHomeFeedStartupSnapshot(latestLoadedDisplayItems)?.let { serialized ->
                withContext(Dispatchers.Default) {
                    runCatching {
                        SystemFileSystem.sink(startupCacheFile).buffered().use { it.writeString(serialized) }
                    }
                }
            }
        }
    }

    // 预取好的下一轮也存一份：App 被杀后冷启动直接显示这批还没看过的内容
    val prefetchedFeed = homeState.prefetchedFeed?.takeIf { it.first == currentRecommendationMode }?.second
    val prefetchedPage = prefetchedFeed?.latestLoadedDisplayItems?.value
    LaunchedEffect(prefetchedPage) {
        val items = prefetchedFeed?.displayItems?.toList() ?: return@LaunchedEffect
        encodeHomeFeedStartupSnapshot(items, maxItems = Int.MAX_VALUE)?.let { writeFile(prefetchCacheFile, it) }
    }

    // 切出时存一份整批的当前列表：进程在后台被系统杀掉后切回能原样恢复，不用刷新。
    // 打开回答时首页先离开组合、收不到 ON_STOP，所以离开组合时也存
    DisposableEffect(lifecycleOwner, currentListCacheFile) {
        fun save() {
            encodeHomeFeedStartupSnapshot(latestViewModel.displayItems.toList(), maxItems = Int.MAX_VALUE)
                ?.let { writeFile(currentListCacheFile, it) }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) save() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            save()
        }
    }

    val completedPageCount = viewModel.completedPageCount
    LaunchedEffect(completedPageCount) {
        if (completedPageCount > 0 &&
            currentRecommendationMode == RecommendationMode.WEB &&
            settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.HIDE.name) == QualityFilterMode.HIDE.name &&
            viewModel.displayItems.isEmpty() &&
            !viewModel.isEnd
        ) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    LaunchedEffect(lifecycleOwner, currentRecommendationMode, account.login, account.id, autoRefreshOnStartup, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (account.login &&
                account.hasRequiredCookie &&
                settings.getBoolean("enableContentFilter", true) &&
                !settings.getBoolean("reverseBlock", false)
            ) {
                remoteHistory.start(homeState.viewModelScope, baseEnvironment)
            }
            // 换上预取实例后本 effect 不重启，用 latestViewModel 拿当前实例
            val feed = latestViewModel
            if (!account.login && settings.getBoolean("loginForRecommendation", true)) {
                requestLoginNavigation()
            } else if (feed.displayItems.isEmpty() && currentRecommendationMode !in homeState.initialLoadedModes) {
                // 每种推荐模式本次运行只自动加载这一次；之后列表即使空了（如全被屏蔽）也只等用户手动刷新。
                // 加载完才记：切回时首页先恢复、回答页随即盖上来会取消这里，返回首页时要重来
                fun readSnapshot(file: Path) = runCatching {
                    if (SystemFileSystem.exists(file)) {
                        SystemFileSystem.source(file).buffered().use { source -> decodeHomeFeedStartupSnapshot(source.readString()) }
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
                val prefetchedItems = withContext(Dispatchers.Default) { readSnapshot(prefetchCacheFile) }
                if (restoredAfterProcessDeath) {
                    // 切出后进程被系统杀掉再切回：原样恢复切出时的列表，不刷新；预取的那批放回内存留给下次手动刷新
                    if (prefetchedItems.isNotEmpty()) homeState.adoptPrefetched(currentRecommendationMode, prefetchedItems)
                    val currentItems = withContext(Dispatchers.Default) { readSnapshot(currentListCacheFile) }
                    if (currentItems.isNotEmpty()) {
                        feed.addDisplayItems(currentItems)
                        homeState.initialLoadedModes += currentRecommendationMode
                        return@repeatOnLifecycle
                    }
                }
                val cachedItems = if (prefetchedItems.isNotEmpty()) {
                    // 全新启动：上次阅读时预取好、还没看过的一批就是这次的刷新，用后删除；它预取时没记录已展示，这里才记
                    withContext(Dispatchers.Default) { runCatching { SystemFileSystem.delete(prefetchCacheFile, mustExist = false) } }
                    paginationEnvironment.applyForegroundHomeFeedFilter(prefetchedItems)
                } else if (autoRefreshOnStartup) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) { readSnapshot(startupCacheFile) }
                }
                if (cachedItems.isNotEmpty()) {
                    remoteHistory.awaitRecentPage()
                    val unreadCachedItems = remoteHistory.filterRemoteReads(cachedItems)
                    if (unreadCachedItems.isEmpty()) {
                        feed.refresh(paginationEnvironment)
                    } else {
                        feed.addDisplayItems(unreadCachedItems)
                    }
                } else {
                    feed.refresh(paginationEnvironment)
                }
                homeState.initialLoadedModes += currentRecommendationMode
            } else if (!feed.isLoading) {
                // 回到列表（读完一篇返回、从后台切回）时预取下一轮刷新
                homeState.prefetch(currentRecommendationMode, paginationEnvironment)
            }
        }
    }

    val historyRevision by remoteHistory.importedRevision.collectAsState()
    LaunchedEffect(remoteHistory, viewModel, historyRevision, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        snapshotFlow { viewModel.displayItems.toList() }.collectLatest { items ->
            // Keep cards already reached by the user stable; only prune the unseen tail in place.
            val visibleKeys = listState.layoutInfo.visibleItemsInfo
                .map { it.key }
                .toSet()
            val lastVisible = items.indexOfLast { it.stableKey in visibleKeys }
            val candidates = items.drop(lastVisible + 1)
            val keptKeys = remoteHistory.filterRemoteReads(candidates).map { it.stableKey }.toSet()
            val removedKeys = candidates.filterNot { it.stableKey in keptKeys }.map { it.stableKey }.toSet()
            // The user may have scrolled while the database query was suspended.
            val currentVisibleKeys = listState.layoutInfo.visibleItemsInfo
                .map { it.key }
                .toSet()
            val protectedEnd = viewModel.displayItems.indexOfLast { it.stableKey in currentVisibleKeys }
            for (index in viewModel.displayItems.lastIndex downTo protectedEnd + 1) {
                if (viewModel.displayItems[index].stableKey in removedKeys) viewModel.displayItems.removeAt(index)
            }
            viewModel.latestLoadedDisplayItems.value = remoteHistory.filterRemoteReads(viewModel.latestLoadedDisplayItems.value)
        }
    }

    // 记录可见卡片，滚进屏幕的记为看过；后台过滤命中时还在屏幕上的卡片，等滑出屏幕再移除
    LaunchedEffect(viewModel, currentRecommendationMode) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapTo(hashSetOf()) { it.key } }.collect { keys ->
            val sync = viewModel.listSync
            sync.visibleItemKeys = keys
            homeState.markSeen(viewModel.displayItems.filter { it.stableKey in keys }, currentRecommendationMode, paginationEnvironment)
            val leaving = sync.pendingRemovalKeys.filterTo(hashSetOf()) { it !in keys }
            if (leaving.isNotEmpty()) {
                viewModel.displayItems.removeAll { it.stableKey in leaving }
                sync.pendingRemovalKeys -= leaving
            }
        }
    }

    // 断网进入离线显示后，网络恢复时自动刷新
    LaunchedEffect(viewModel) {
        networkReconnected.collect {
            if (viewModel.networkUnavailable) viewModel.refresh(paginationEnvironment)
        }
    }

    var feedAuthorBlockRequest by remember { mutableStateOf<FeedAuthorBlockRequest?>(null) }

    // 按关键词屏蔽对话框
    var showBlockByKeywordsDialog by remember { mutableStateOf(false) }
    var feedToBlockByKeywords by remember { mutableStateOf<Pair<String, String?>?>(null) } // 二元组内容为标题和摘要。
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = isActive &&
            !showAccountBottomSheet &&
            !showCreateMenu &&
            feedAuthorBlockRequest == null &&
            !showBlockByKeywordsDialog &&
            (!account.login || account.hasRequiredCookie),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (duo3HomeAccount) {
                Modifier
                    .fillMaxSize()
                    .blur(createMenuBlurRadius)
            } else {
                // master旧版需要pad掉状态栏
                Modifier
                    .fillMaxSize()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .blur(createMenuBlurRadius)
            },
            topBar = {
                if (!showTopActions) {
                    return@Scaffold
                }
                if (duo3HomeAccount) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .height(
                                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp + 32.dp,
                                ).fillMaxWidth(),
                        ) { }
                        Row(
                            modifier = Modifier
                                .testTag(HOME_TOP_ACTIONS_TAG)
                                .fillMaxWidth()
                                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                                .padding(16.dp, 8.dp, 16.dp, 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .testTag(HOME_SEARCH_BUTTON_TAG),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                onClick = {
                                    navigator.onNavigate(
                                        Search(query = ""),
                                    )
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "搜索",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )

                                    IconButton(
                                        onClick = { showAccountBottomSheet = true },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .testTag(HOME_ACCOUNT_BUTTON_TAG),
                                    ) {
                                        Box(Modifier.padding(12.dp)) {
                                            BadgedBox(
                                                badge = {
                                                    if (showUnreadBadge && unreadCount > 0) {
                                                        Badge { }
                                                    }
                                                },
                                            ) {
                                                val avatarUrl = account.avatarUrl
                                                if (avatarUrl != null) {
                                                    AsyncImage(
                                                        model = avatarUrl,
                                                        contentDescription = "账号",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .border(
                                                                0.5.dp,
                                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                                                CircleShape,
                                                            ).clip(CircleShape),
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.AccountCircle,
                                                        contentDescription = "账号",
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(40.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(shadowElevation = 4.dp) {
                        Row(
                            modifier = Modifier
                                .testTag(HOME_TOP_ACTIONS_TAG)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag(HOME_SEARCH_BUTTON_TAG),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    navigator.onNavigate(
                                        Search(query = ""),
                                    )
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "搜索内容",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag(HOME_NOTIFICATION_BUTTON_TAG)
                                    .clickable(role = Role.Button) {
                                        navigator.onNavigate(Notification)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                // 点击盒与内容盒分离，避免 IconButton 的裁剪边界截掉 BadgedBox 的 badge。
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag(HOME_NOTIFICATION_BUTTON_CONTENT_TAG),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (showUnreadBadge && unreadCount > 0) {
                                                Badge(modifier = Modifier.testTag(HOME_NOTIFICATION_BADGE_TAG)) {
                                                    Text("$unreadCount")
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = "通知",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { scaffoldPadding ->
            if (duo3HomeAccount && showAccountBottomSheet) {
                MyModalBottomSheet(
                    onDismissRequest = { showAccountBottomSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AccountSettingScreen(
                        innerPadding = PaddingValues(0.dp),
                        unreadCount = unreadCount,
                        showUnreadBadge = showUnreadBadge,
                        onDismissRequest = { showAccountBottomSheet = false },
                    )
                }
            }

            FeedPullToRefresh(
                viewModel,
                paginationEnvironment,
                PaddingValues(top = scaffoldPadding.calculateTopPadding()),
                onRefresh = { refreshFeed(pull = true) },
            ) {
                PaginatedList(
                    items = viewModel.displayItems,
                    listState = listState,
                    modifier = Modifier
                        .pageTurnViewportWithGuide(pageTurnTarget)
                        .testTag(HOME_FEED_LIST_TAG),
                    contentPadding = PaddingValues(
                        top = scaffoldPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
                    onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                    footer = ProgressIndicatorFooter,
                    key = { item -> item.stableKey },
                ) { item ->
                    // 本 fork：卡片往左滑屏蔽所在问题（同卡片菜单「屏蔽问题」）。所有卡片都接住左右滑，不切换标签页；
                    // 从恢复的列表来、还不知道所属问题的卡片，滑时现查一次详情
                    SwipeToReplyContainer(
                        onReply = {
                            refreshScope.launch {
                                val question = item.blockableQuestion()
                                    ?: (item.navDestination as? Article)
                                        ?.takeIf { it.type == ArticleType.Answer }
                                        ?.let { answer ->
                                            (cachedContentDetail(answer) ?: paginationEnvironment.fetchContentDetail(answer)) as? DataHolder.Answer
                                        }?.let { it.question.id to it.question.title }
                                if (question != null) {
                                    viewModel.handleBlockQuestion(userMessages, question.first, question.second)
                                    // 现查到问题的卡片自己还不带问题信息，按问题批量移除时漏掉它，单独移除
                                    viewModel.displayItems.removeAll { it.stableKey == item.stableKey }
                                } else if ((item.navDestination as? Article)?.type == ArticleType.Answer) {
                                    userMessages.showShortMessage("没查到这条回答所属的问题，稍后再试")
                                } else {
                                    userMessages.showShortMessage("这条内容不属于任何问题，无法屏蔽问题")
                                }
                            }
                        },
                        replyIcon = Icons.Default.Block,
                    ) {
                        FeedCard(
                            item,
                            thumbnailUrl = when (val target = item.feed?.target) {
                                is Feed.AnswerTarget -> target.thumbnail
                                else -> null
                            },
                            menuItems = { dismissMenu ->
                                if (feedKeywordExtractionAvailable) {
                                    DropdownMenuItem(
                                        text = { Text("按关键词屏蔽") },
                                        onClick = {
                                            dismissMenu()
                                            viewModel.handleBlockByKeywords(paginationEnvironment, userMessages, item) { (_, contentInfo) ->
                                                feedToBlockByKeywords = contentInfo.first to contentInfo.second
                                                showBlockByKeywordsDialog = true
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("屏蔽用户") },
                                    onClick = {
                                        dismissMenu()
                                        viewModel.handleBlockUser(paginationEnvironment, userMessages, item) { authorInfo ->
                                            feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                                type = FeedAuthorBlockType.CONTENT_AUTHOR,
                                                userId = authorInfo.first,
                                                userName = authorInfo.second,
                                            )
                                        }
                                    },
                                )
                                item.blockableQuestion()?.let { (questionId, questionTitle) ->
                                    DropdownMenuItem(
                                        text = { Text("屏蔽问题") },
                                        onClick = {
                                            dismissMenu()
                                            viewModel.handleBlockQuestion(userMessages, questionId, questionTitle)
                                        },
                                    )
                                }
                                // 网页推荐卡片不再提前拉详情，打开菜单时现查话题（多半命中缓存）
                                var menuRaw by remember(item.stableKey) { mutableStateOf(item.raw) }
                                LaunchedEffect(item.stableKey) {
                                    if (menuRaw == null) {
                                        menuRaw = item.navDestination?.let { destination ->
                                            (destination as? Article)?.let { cachedContentDetail(it) }
                                                ?: paginationEnvironment.getOrFetchContentDetail(destination)
                                        }
                                    }
                                }
                                val topics = when (val raw = menuRaw) {
                                    is DataHolder.Answer -> raw.question.topics
                                    is DataHolder.Question -> raw.topics
                                    is DataHolder.Article -> raw.topics ?: emptyList()
                                    is DataHolder.Pin -> raw.topics ?: emptyList()
                                    else -> emptyList()
                                }
                                BlockTopicMenuItems(topics) { topic ->
                                    dismissMenu()
                                    viewModel.handleBlockTopic(userMessages, topic.id, topic.name)
                                }
                            },
                        ) { clickedItem, destination ->
                            val feed = clickedItem.feed
                            if (feed != null) {
//                            DataHolder.putFeed(feed)
                                (viewModel as? HomeFeedInteractionViewModel)
                                    ?.onUiContentClick(paginationEnvironment, feed, clickedItem)
                            } else {
                                localHomeViewModel?.onLocalItemOpened(clickedItem)
                            }
                            if (destination != null) {
                                navigator.onNavigate(destination)
                            }
                        }
                    }
                }

                if (showRefreshFab) {
                    if (isDebuggable) {
                        DraggableRefreshButton(
                            onClick = {
                                val data = Json.encodeToString(viewModel.debugData)
                                paginationEnvironment.setPlainTextClipboard("data", data)
                                userMessages.showShortMessage("已复制调试数据")
                            },
                            preferenceName = "copyAll",
                        ) {
                            Icon(Icons.Default.CopyAll, contentDescription = "复制")
                        }
                    }
                    DraggableRefreshButton(
                        modifier = Modifier.testTag(HOME_REFRESH_BUTTON_TAG),
                        onClick = { refreshScope.launch { refreshFeed(pull = false) } },
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCreateMenu,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        showCreateMenu = false
                    },
            )
        }

        // 本 fork 不显示创作按钮：其中「提问题」「写回答」上游尚未实现，只剩「发想法」
        if (SHOW_HOME_CREATE_FAB) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                horizontalAlignment = Alignment.End,
            ) {
                AnimatedVisibility(
                    visible = showCreateMenu,
                    enter = fadeIn(animationSpec = tween(durationMillis = 120)) +
                        scaleIn(
                            initialScale = 0.92f,
                            transformOrigin = TransformOrigin(1f, 1f),
                            animationSpec = tween(durationMillis = 180),
                        ) +
                        slideInVertically(animationSpec = tween(durationMillis = 180)) { it / 8 },
                    exit = fadeOut(animationSpec = tween(durationMillis = 90)) +
                        scaleOut(
                            targetScale = 0.96f,
                            transformOrigin = TransformOrigin(1f, 1f),
                            animationSpec = tween(durationMillis = 120),
                        ) +
                        slideOutVertically(animationSpec = tween(durationMillis = 120)) { it / 8 },
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            modifier = Modifier
                                .width(180.dp)
                                .testTag(HOME_CREATE_MENU_TAG),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp,
                        ) {
                            Column {
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(HOME_WRITE_QUESTION_BUTTON_TAG),
                                    text = { Text("提问题") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Default.HelpOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        showCreateMenu = false
                                        userMessages.showShortMessage("正在施工")
                                    },
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(HOME_WRITE_ANSWER_BUTTON_TAG),
                                    text = { Text("写回答") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showCreateMenu = false
                                        userMessages.showShortMessage("正在施工")
                                    },
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(HOME_WRITE_PIN_BUTTON_TAG),
                                    text = { Text("发想法") },
                                    leadingIcon = {
                                        Icon(Icons.Default.MarkUnreadChatAlt, contentDescription = null)
                                    },
                                    onClick = {
                                        showCreateMenu = false
                                        navigator.onNavigate(WritePin())
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                val createFabOpacity = remember(settings) {
                    settings.getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
                }
                FloatingActionButton(
                    modifier = Modifier.testTag(HOME_CREATE_FAB_TAG),
                    onClick = { showCreateMenu = !showCreateMenu },
                    shape = CircleShape,
                    containerColor = FloatingActionButtonDefaults.containerColor.copy(alpha = createFabOpacity),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = createFabOpacity),
                    elevation = if (createFabOpacity < 1f) {
                        FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    } else {
                        FloatingActionButtonDefaults.elevation()
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "创作")
                }
            }
        }
    }

    FeedAuthorBlockConfirmDialog(
        request = feedAuthorBlockRequest,
        displayItems = viewModel.displayItems,
        onDismiss = { feedAuthorBlockRequest = null },
        onConfirm = {
            feedAuthorBlockRequest?.let { viewModel.removeItemsByAuthor(it.userId, it.userName) }
            feedAuthorBlockRequest = null
        },
    )

    // 按关键词屏蔽对话框
    feedToBlockByKeywords?.let { (title, excerpt) ->
        BlockByKeywordsDialog(
            showDialog = showBlockByKeywordsDialog,
            feedTitle = title,
            feedExcerpt = excerpt,
            onDismiss = {
                showBlockByKeywordsDialog = false
                feedToBlockByKeywords = null
            },
            onConfirm = {
                // 只按新规则重新过滤当前列表、马上移除命中的卡片，不刷新；预取的下一批换上时也会再过滤
                homeState.refilter(viewModel, paginationEnvironment, protectVisible = false)
                showBlockByKeywordsDialog = false
                feedToBlockByKeywords = null
            },
        )
    }
}
