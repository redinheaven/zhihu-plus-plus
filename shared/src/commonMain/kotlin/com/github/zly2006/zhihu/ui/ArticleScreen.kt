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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.article.ArticleActionsMenu
import com.github.zly2006.zhihu.ui.article.ArticleSummarySheet
import com.github.zly2006.zhihu.ui.article.ArticleVideoAttachmentContent
import com.github.zly2006.zhihu.ui.article.CachedAnswerPreview
import com.github.zly2006.zhihu.ui.article.rememberArticleAnswerNavigationState
import com.github.zly2006.zhihu.ui.article.rememberArticleBottomBarState
import com.github.zly2006.zhihu.ui.article.rememberArticleTopBarState
import com.github.zly2006.zhihu.ui.article.rememberBottomBarAvoidingBringIntoViewSpec
import com.github.zly2006.zhihu.ui.article.voteUpActiveButtonColors
import com.github.zly2006.zhihu.ui.article.voteUpNeutralButtonColors
import com.github.zly2006.zhihu.ui.article.voteUpNeutralContent
import com.github.zly2006.zhihu.ui.article.voteUpNeutralContentDuo3
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AnswerHorizontalOverscroll
import com.github.zly2006.zhihu.ui.components.AnswerVerticalOverscroll
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.CollectionDialogComponent
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.ContentEndMarker
import com.github.zly2006.zhihu.ui.components.DEFAULT_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.ExportDialogComponent
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.VotersSheet
import com.github.zly2006.zhihu.ui.components.ZhihuTwoRowsTopAppBar
import com.github.zly2006.zhihu.ui.components.normalizedAnswerSwitchSensitivity
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberContentLinkedScrollBehavior
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.ui.subscreens.DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PREF_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.smoothGradient
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.formatArticleDateTime
import com.github.zly2006.zhihu.viewmodel.markCollectionSearchKeyword
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import com.hrm.markdown.renderer.LocalMarkdownHighlightRequester
import com.materialkolor.ktx.harmonize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import zhihu.shared.generated.resources.Res
import zhihu.shared.generated.resources.ic_vote_down_24dp
import zhihu.shared.generated.resources.ic_vote_up_24dp
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SCROLL_THRESHOLD = 10 // 滑动阈值，单位为dp
private val ScrollThresholdDp = SCROLL_THRESHOLD.dp

/**
 * 文章/回答详情页。
 *
 * 页面负责加载知乎回答或专栏文章，展示标题、作者、正文、附件视频、评论入口、分享/复制/浏览器打开等底部操作，
 * 正文主路径使用 Compose Markdown 渲染。回答页还承载同题回答切换手势和对应转场状态，因此改动时要同时关注
 * `answerSwitchMode`、`buttonSkipAnswer`、`autoHideArticleBottomBar`、`titleAutoHide`、`answerDoubleTapAction` 和
 * `ARTICLE_USE_WEBVIEW_PREFERENCE_KEY`。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun ArticleScreen(
    article: Article,
    viewModel: ArticleViewModel,
) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val articleNavController = LocalArticleNavController.current
    val backStackEntry by articleNavController?.currentBackStackEntryAsState()
        ?: remember { mutableStateOf(null) }

    val scrollState = rememberScrollState()
    val settings = rememberSettingsStore()
    val isTitleAutoHide by rememberObservedSetting(settings, "titleAutoHide") { getBoolean("titleAutoHide", false) }
    val autoHideArticleBottomBar by rememberObservedSetting(settings, "autoHideArticleBottomBar") {
        getBoolean("autoHideArticleBottomBar", true)
    }
    val configuredAnswerSwitchMode by rememberObservedSetting(settings, "answerSwitchMode") {
        getString("answerSwitchMode", "vertical")
    }
    val answerSwitchMode = configuredAnswerSwitchMode.takeIf { isAnswerSwipeSupported } ?: "off"
    val answerSwitchSensitivity by rememberObservedSetting(settings, ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY) {
        normalizedAnswerSwitchSensitivity(getFloat(ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY, DEFAULT_ANSWER_SWITCH_SENSITIVITY))
    }
    val pinAnswerDate by rememberObservedSetting(settings, "pinAnswerDate") { getBoolean("pinAnswerDate", false) }
    val useDuo3ArticleActions by rememberObservedSetting(settings, "duo3_article_actions") {
        getBoolean("duo3_article_actions", false)
    }
    val buttonSkipAnswer by rememberObservedSetting(settings, "buttonSkipAnswer") { getBoolean("buttonSkipAnswer", false) }
    val autoHideSkipAnswerButton by rememberObservedSetting(settings, "autoHideSkipAnswerButton") {
        getBoolean("autoHideSkipAnswerButton", true)
    }
    var answerDoubleTapAction by rememberObservedSetting(settings, ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY) {
        AnswerDoubleTapAction.fromPreference(
            getString(ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY, AnswerDoubleTapAction.Ask.preferenceValue),
        )
    }
    val useWebView by rememberObservedSetting(settings, ARTICLE_USE_WEBVIEW_PREFERENCE_KEY) {
        getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
    }
    val useTiqianMarkdown by rememberObservedSetting(settings, DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY) {
        getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    }
    val pageTurnSwitchAnswer by rememberObservedSetting(settings, PREF_PAGE_TURN_SWITCH_ANSWER) {
        getBoolean(PREF_PAGE_TURN_SWITCH_ANSWER, DEFAULT_PAGE_TURN_SWITCH_ANSWER)
    }

    fun saveAnswerDoubleTapAction(action: AnswerDoubleTapAction) {
        answerDoubleTapAction = action
        settings.putString(ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY, action.preferenceValue)
    }
    val userMessages = rememberUserMessageSink()
    val density = LocalDensity.current
    val effectiveScrollMaxValue by remember { derivedStateOf { scrollState.maxValue } }
    val latestEffectiveScrollMaxValue by rememberUpdatedState(effectiveScrollMaxValue)
    var showComments by rememberSaveable(article.type, article.id) { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDoubleTapActionDialog by remember { mutableStateOf(false) }
    var showVoters by rememberSaveable(article.type, article.id) { mutableStateOf(false) }
    val topBarState = rememberArticleTopBarState(
        scrollState = scrollState,
        autoHide = isTitleAutoHide,
    )
    val bottomBarState = rememberArticleBottomBarState(
        scrollState = scrollState,
        autoHide = autoHideArticleBottomBar,
        scrollDeltaThreshold = with(density) { ScrollThresholdDp.toPx() },
        showSlot = backStackEntry?.hasRoute(Article::class) == true || articleNavController == null,
        navigationBarHeightPx = density.run {
            WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
                .toPx()
                .coerceAtLeast(0f)
        },
    )
    val sharedData = sharedArticleAnswerSwitchState.takeIf { article.type == ArticleType.Answer }
    var isImmersiveMode by remember(sharedData) {
        mutableStateOf(sharedData?.isImmersiveMode ?: false)
    }
    val answerNavigationState = rememberArticleAnswerNavigationState(
        switchState = sharedData,
        viewModel = viewModel,
        navigator = navigator,
        navController = articleNavController,
        answerSwitchMode = answerSwitchMode,
    )
    val usesVerticalAnswerSwitch = article.type == ArticleType.Answer && answerSwitchMode == "vertical"
    val pageTurnActive = !(useWebView && isLegacyWebViewSupported) &&
        !showComments &&
        !showCollectionDialog &&
        !showActionsMenu &&
        !showSummaryDialog &&
        !showExportDialog &&
        !showDoubleTapActionDialog &&
        !showVoters
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = scrollState,
        enabled = pageTurnActive,
        maxScrollValue = effectiveScrollMaxValue,
        onPageUpAtStart = (answerNavigationState::navigateToPrevious)
            .takeIf { article.type == ArticleType.Answer && pageTurnSwitchAnswer },
        onPageDownAtEnd = (answerNavigationState::navigateToNext)
            .takeIf { article.type == ArticleType.Answer && pageTurnSwitchAnswer },
    )
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(sharedData, isImmersiveMode) {
        if (sharedData != null) sharedData.isImmersiveMode = isImmersiveMode
    }
    ArticleImmersiveModeEffect(isImmersiveMode)

    // 从这里进作者页、评论再返回时会重新进入组合，用可保存状态保证每次打开只记一次（浏览器里同一页面也只报一次）
    var readHistoryRecorded by rememberSaveable(article.id) { mutableStateOf(false) }
    LaunchedEffect(article.id) {
        if (readHistoryRecorded) return@LaunchedEffect
        readHistoryRecorded = true
        environment.addReadHistory(
            contentToken = article.id.toString(),
            contentTypeName = article.type.name.lowercase(),
        )
    }

    fun upVoteFromDoubleTap() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (viewModel.voteUpState != VoteUpState.Up) {
            viewModel.toggleVoteUp(environment, VoteUpState.Up)
        }
    }

    fun performAnswerDoubleTapAction(action: AnswerDoubleTapAction) {
        when (action) {
            AnswerDoubleTapAction.None -> Unit
            AnswerDoubleTapAction.Ask -> showDoubleTapActionDialog = true
            AnswerDoubleTapAction.VoteUp -> upVoteFromDoubleTap()
            AnswerDoubleTapAction.OpenComments -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                showComments = true
            }
            AnswerDoubleTapAction.ToggleImmersive -> {
                isImmersiveMode = !isImmersiveMode
            }
        }
    }

    fun handleAnswerDoubleTap() {
        if (article.type != ArticleType.Answer) return
        performAnswerDoubleTapAction(answerDoubleTapAction)
    }

    val answerDoubleTapModifier = if (
        article.type == ArticleType.Answer &&
        answerDoubleTapAction != AnswerDoubleTapAction.None
    ) {
        Modifier.pointerInput(answerDoubleTapAction) {
            detectTapGestures(
                onDoubleTap = { handleAnswerDoubleTap() },
            )
        }
    } else {
        Modifier
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collectLatest { currentScroll ->
            if (viewModel.rememberedScrollYSync) {
                viewModel.rememberedScrollY = currentScroll
            }
            if (currentScroll == viewModel.rememberedScrollY && scrollState.maxValue != Int.MAX_VALUE) {
                viewModel.rememberedScrollYSync = true
            }
        }
    }

    val articleBringIntoViewSpec = rememberBottomBarAvoidingBringIntoViewSpec(
        bottomBarState.obscuredHeightPx,
    )
    LaunchedEffect(article.id) {
        answerNavigationState.prepareArticle()
        viewModel.loadArticle(environment)
        viewModel.loadCollections(environment)
    }

    // 本 fork：手指从左往右划一段返回上一页（同返回键）。左右切换回答模式下右划是上一个回答，不启用。
    // 正文用 Main 阶段：能横向滚动的代码块、表格先消费拖动，在它们上面划不触发；顶栏的标题、作者行可点，
    // 在那里划完松手会被当成点击打开问题或作者页，所以顶栏用 Initial 阶段抢先识别，划动时把事件消费掉
    val swipeBackEnabled = !(article.type == ArticleType.Answer && answerSwitchMode == "horizontal")

    fun Modifier.swipeBack(pass: PointerEventPass): Modifier = if (!swipeBackEnabled) {
        this
    } else {
        pointerInput(navigator, pass) {
            val threshold = 96.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - down.position.x
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            if (dx > threshold) navigator.onNavigateBack()
                        }
                        break
                    }
                    if (!dragging) {
                        // 子元素（竖向滚动、横向滚动的代码块）已经接手
                        if (change.isConsumed) break
                        val dy = change.position.y - down.position.y
                        if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                            dragging = true
                        } else if (abs(dy) > viewConfiguration.touchSlop) {
                            break
                        }
                    }
                    if (dragging) change.consume()
                }
            }
        }
    }

    // 本 fork：从收藏夹关键词搜索打开时，给关键词包上 <mark> 高亮，正文加载出来后滚到那一段
    val searchKeyword = article.searchKeyword?.takeIf { it.isNotBlank() }
    val searchHighlightRequester = remember { BringIntoViewRequester() }
    val renderedContent = remember(viewModel.content, searchKeyword) {
        if (searchKeyword == null || viewModel.content.isBlank()) {
            viewModel.content
        } else {
            markCollectionSearchKeyword(viewModel.content, searchKeyword, article.searchSentence.orEmpty())
        }
    }
    LaunchedEffect(renderedContent) {
        if (searchKeyword == null || viewModel.content.isBlank()) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        searchHighlightRequester.bringIntoView()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent() {
        val scrollBehavior = rememberContentLinkedScrollBehavior(scrollState)
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isImmersiveMode) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            topBar = if (isImmersiveMode) {
                {}
            } else {
                {
                    val topBarFling = ScrollableDefaults.flingBehavior()
                    Box(
                        modifier = Modifier
                            .swipeBack(PointerEventPass.Initial)
                            // 本 fork：顶栏盖在正文上方，触摸到不了正文；上下拖动顶栏时转给正文滚动（含松手后的惯性）
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta -> scrollState.dispatchRawDelta(-delta) },
                                onDragStopped = { velocity ->
                                    scrollState.scroll { with(topBarFling) { performFling(-velocity) } }
                                },
                            ).onSizeChanged {
                                topBarState.heightPx = it.height.toFloat()
                            }.graphicsLayer {
                                translationY = topBarState.offset.value
                                alpha = if (topBarState.heightPx > 0f) 1f + (topBarState.offset.value / topBarState.heightPx) else 1f
                            },
                    ) {
                        ZhihuTwoRowsTopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        if (articleNavController != null) {
                                            articleNavController.popBackStack()
                                        } else {
                                            navigator.onNavigateBack()
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                if (useDuo3ArticleActions) {
                                    IconButton(
                                        onClick = { showActionsMenu = true },
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "更多选项",
                                        )
                                    }
                                }
                            },
                            title = { expanded ->
                                Text(
                                    text = viewModel.title,
                                    modifier = Modifier
                                        .padding(if (expanded) PaddingValues(end = 16.dp) else PaddingValues())
                                        .let {
                                            if (article.type == ArticleType.Answer) {
                                                it.clickable {
                                                    navigator.onNavigate(Question(viewModel.questionId, viewModel.title))
                                                }
                                            } else {
                                                it
                                            }
                                        },
                                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            subtitle = { expanded ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(if (expanded) PaddingValues(vertical = 16.dp) else PaddingValues(top = 2.dp, bottom = 8.dp))
                                        .padding(end = 16.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            navigator.onNavigate(
                                                com.github.zly2006.zhihu.navigation.Person(
                                                    id = viewModel.authorId,
                                                    urlToken = viewModel.authorUrlToken,
                                                    name = viewModel.authorName,
                                                ),
                                            )
                                        },
                                ) {
                                    if (viewModel.authorAvatarSrc.isNotEmpty()) {
                                        AsyncImage(
                                            model = viewModel.authorAvatarSrc,
                                            contentDescription = "作者头像",
                                            modifier = Modifier
                                                .size(if (expanded) 40.dp else 20.dp)
                                                .clip(CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(if (expanded) 40.dp else 20.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(if (expanded) 8.dp else 4.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = viewModel.authorName,
                                                style = if (expanded) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                                                color = if (expanded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (viewModel.authorBadge != null) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                AuthorBadge(
                                                    badge = viewModel.authorBadge,
                                                    compact = !expanded,
                                                )
                                            }
                                        }
                                        if (viewModel.authorBio.isNotEmpty() && expanded) {
                                            Text(
                                                text = viewModel.authorBio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            },
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.topAppBarColors().copy(
                                scrolledContainerColor = if (MaterialTheme.colorScheme.surfaceContainer != MaterialTheme.colorScheme.background) {
                                    MaterialTheme.colorScheme.surfaceContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ),
                        )
                    }
                }
            },
            bottomBar = if (isImmersiveMode) {
                {}
            } else {
                @Composable {
                    // 防止在导航动画和预测性返回手势过程中，底部操作栏闪烁。
                    // 操作栏内容的共享组合，按 useDuo3ArticleActions 切换两套视觉。
                    @Composable
                    fun ActionBarContent() {
                        if (!useDuo3ArticleActions) {
                            // ── 主视觉：按钮式投票与操作区 ────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 8.dp)
                                    .height(36.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            color = if (viewModel.voteUpState == VoteUpState.Neutral) {
                                                voteUpNeutralContent().copy(alpha = 0.1f)
                                            } else {
                                                voteUpNeutralContent()
                                            },
                                        ),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    when (viewModel.voteUpState) {
                                        VoteUpState.Neutral -> {
                                            Button(
                                                onClick = { viewModel.toggleVoteUp(environment, VoteUpState.Up) },
                                                colors = voteUpNeutralButtonColors(),
                                                shape = RectangleShape,
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                            ) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(painterResource(Res.drawable.ic_vote_up_24dp), "赞同")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = viewModel.voteUpCount.toString())
                                            }
                                            Button(
                                                onClick = { viewModel.toggleVoteUp(environment, VoteUpState.Down) },
                                                colors = voteUpNeutralButtonColors(),
                                                shape = RectangleShape,
                                                modifier = Modifier.height(ButtonDefaults.MinHeight).width(ButtonDefaults.MinHeight),
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                            ) {
                                                Icon(painterResource(Res.drawable.ic_vote_down_24dp), "反对")
                                            }
                                        }

                                        VoteUpState.Up -> {
                                            Button(
                                                onClick = { viewModel.toggleVoteUp(environment, VoteUpState.Neutral) },
                                                colors = voteUpActiveButtonColors(),
                                                shape = RectangleShape,
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                            ) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(painterResource(Res.drawable.ic_vote_up_24dp), "赞同")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = viewModel.voteUpCount.toString())
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                        }

                                        VoteUpState.Down -> {
                                            Button(
                                                onClick = { viewModel.toggleVoteUp(environment, VoteUpState.Neutral) },
                                                colors = voteUpActiveButtonColors(),
                                                shape = RectangleShape,
                                                modifier = Modifier.height(ButtonDefaults.MinHeight),
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                            ) {
                                                Icon(painterResource(Res.drawable.ic_vote_down_24dp), "反对")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("反对")
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.End) {
                                    IconButton(
                                        onClick = { showCollectionDialog = true },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (viewModel.isFavorited) Color(0xFFF57C00) else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (viewModel.isFavorited) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    ) {
                                        Icon(if (viewModel.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "收藏")
                                    }
                                    Button(
                                        onClick = { showComments = true },
                                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
                                        colors = voteUpNeutralButtonColors(),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "评论")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "${viewModel.commentCount}")
                                    }

                                    IconButton(
                                        onClick = { showActionsMenu = true },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "更多选项",
                                        )
                                    }
                                }
                            }
                        } else {
                            // ── duo3：药丸式动画投票与操作区 ────────────────────
                            Row(
                                modifier = Modifier
                                    .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp)
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AnimatedVisibility(
                                        visible = viewModel.voteUpState == VoteUpState.Neutral || viewModel.voteUpState == VoteUpState.Up,
                                    ) {
                                        val upBgColor by animateColorAsState(
                                            targetValue = if (viewModel.voteUpState == VoteUpState.Up) voteUpNeutralContentDuo3() else MaterialTheme.colorScheme.surfaceContainer,
                                        )
                                        val upContentColor by animateColorAsState(
                                            targetValue = if (viewModel.voteUpState == VoteUpState.Up) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(upBgColor)
                                                .clickable {
                                                    viewModel.toggleVoteUp(
                                                        environment,
                                                        if (viewModel.voteUpState == VoteUpState.Up) VoteUpState.Neutral else VoteUpState.Up,
                                                    )
                                                }.padding(6.dp, 8.dp, 12.dp, 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_vote_up_24dp),
                                                contentDescription = "赞同",
                                                tint = upContentColor,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = viewModel.voteUpCount.toString(),
                                                color = upContentColor,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = viewModel.voteUpState == VoteUpState.Neutral) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    AnimatedVisibility(
                                        visible = viewModel.voteUpState == VoteUpState.Neutral || viewModel.voteUpState == VoteUpState.Down,
                                    ) {
                                        val downBgColor by animateColorAsState(
                                            targetValue = if (viewModel.voteUpState == VoteUpState.Down) voteUpNeutralContentDuo3() else MaterialTheme.colorScheme.surfaceContainer,
                                        )
                                        val downContentColor by animateColorAsState(
                                            targetValue = if (viewModel.voteUpState == VoteUpState.Down) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(downBgColor)
                                                .clickable {
                                                    viewModel.toggleVoteUp(
                                                        environment,
                                                        if (viewModel.voteUpState == VoteUpState.Down) VoteUpState.Neutral else VoteUpState.Down,
                                                    )
                                                }.padding(6.dp, 8.dp, 8.dp, 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AnimatedVisibility(visible = viewModel.voteUpState != VoteUpState.Down) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                            }
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_vote_down_24dp),
                                                contentDescription = "反对",
                                                tint = downContentColor,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            AnimatedVisibility(visible = viewModel.voteUpState == VoteUpState.Down) {
                                                Row {
                                                    Text(
                                                        text = "反对",
                                                        color = downContentColor,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        modifier = Modifier.padding(horizontal = 4.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(
                                        onClick = { showCollectionDialog = true },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (viewModel.isFavorited) {
                                                Color(0xFFF57C00).harmonize(MaterialTheme.colorScheme.primary)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            },
                                            contentColor = if (viewModel.isFavorited) {
                                                Color.White.copy(alpha = 0.87f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        ),
                                    ) {
                                        Icon(
                                            if (viewModel.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                            contentDescription = "收藏",
                                        )
                                    }

                                    Button(
                                        onClick = { showComments = true },
                                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "评论")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "${viewModel.commentCount}", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        if (bottomBarState.showSlot) {
                            Box(
                                modifier = Modifier
                                    .onSizeChanged { bottomBarState.heightPx = it.height.toFloat() }
                                    .graphicsLayer {
                                        translationY = bottomBarState.offset.value
                                        alpha = if (bottomBarState.heightPx > 0f) {
                                            1f - (bottomBarState.offset.value / bottomBarState.heightPx)
                                        } else {
                                            1f
                                        }
                                    }
                                    // 本 fork：操作栏加底色，避免正文从按钮后面和系统导航栏下面透出来
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(top = 8.dp),
                            ) {
                                ActionBarContent()
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            // innerPadding 顶部随顶栏折叠变小；正文边距加回已折叠量（顶栏按取整后的 heightOffset 布局），固定为展开高度，
            // 顶栏覆盖在正文上折叠，避免每帧重排整篇正文。
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = with(LocalDensity.current) {
                val collapsedPx = if (isImmersiveMode) 0 else -scrollBehavior.state.heightOffset.roundToInt()
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = (innerPadding.calculateTopPadding().roundToPx() + collapsedPx).toDp() + 8.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                )
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides articleBringIntoViewSpec) {
                Box {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .verticalScroll(scrollState)
                            .padding(contentPadding),
                    ) {
                        if (isImmersiveMode && viewModel.authorName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navigator.onNavigate(
                                            com.github.zly2006.zhihu.navigation.Person(
                                                id = viewModel.authorId,
                                                urlToken = viewModel.authorUrlToken,
                                                name = viewModel.authorName,
                                            ),
                                        )
                                    }.padding(vertical = 8.dp),
                            ) {
                                if (viewModel.authorAvatarSrc.isNotBlank()) {
                                    AsyncImage(
                                        model = viewModel.authorAvatarSrc,
                                        contentDescription = "作者头像",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = viewModel.authorName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        @Suppress("UnusedReceiverParameter") // 确保竖式布局
                        @Composable
                        fun ColumnScope.DateTexts() {
                            Text(
                                "发布于 " + formatArticleDateTime(viewModel.createdAt),
                                color = Color.Gray,
                                fontSize = 11.sp,
                            )
                            if (viewModel.createdAt != viewModel.updatedAt) {
                                Text(
                                    "编辑于 " + formatArticleDateTime(viewModel.updatedAt),
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                )
                            }
                        }

                        if (viewModel.content.isNotEmpty() || viewModel.attachment != null) {
                            if (article.type == ArticleType.Article && viewModel.topics.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    viewModel.topics.forEach { topic ->
                                        androidx.compose.material3.FilterChip(
                                            selected = false,
                                            onClick = { navigator.onNavigate(Topic(topic.id, topic.name)) },
                                            label = { Text("# ${topic.name}") },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            val hasPinnedDate = pinAnswerDate
                            val endorsements = viewModel.endorsements
                            val hasEndorsements = endorsements.isNotEmpty()
                            if (hasPinnedDate || hasEndorsements) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    if (hasPinnedDate) {
                                        DateTexts()
                                    }
                                    if (hasEndorsements) {
                                        if (hasPinnedDate) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            endorsements.forEach { endorsement ->
                                                AnswerEndorsementChip(endorsement)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            if (useWebView && isLegacyWebViewSupported) {
                                // WebView 正文渲染已经废弃，只保留为紧急回退路径；正文外 UI 不再为它单独分支。
                                ArticleWebViewContent(
                                    article = article,
                                    html = viewModel.content,
                                    title = viewModel.title,
                                    scrollState = scrollState,
                                    rememberedScrollY = viewModel.rememberedScrollY,
                                    rememberedScrollYSync = viewModel.rememberedScrollYSync,
                                    onRememberedScrollYSyncChange = { viewModel.rememberedScrollYSync = it },
                                    onImageLoadFailed = { userMessages.showMessage("图片加载失败，请向开发者反馈") },
                                    onDoubleTap = ::handleAnswerDoubleTap,
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    if (!pinAnswerDate) {
                                        DateTexts()
                                    }
                                    if (viewModel.ipInfo != null) {
                                        Text(
                                            "IP属地：${viewModel.ipInfo}",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height((16 + 36).dp))
                            } else {
                                CompositionLocalProvider(
                                    LocalMarkdownHighlightRequester provides searchHighlightRequester.takeIf { searchKeyword != null },
                                ) {
                                    RenderMarkdown(
                                        html = renderedContent,
                                        modifier = Modifier
                                            .testTag("article_content")
                                            .articleMarkdownSelectionWorkaround(),
                                        scrollState = scrollState,
                                        selectable = true,
                                        enableScroll = false,
                                        useTiqianRenderer = useTiqianMarkdown,
                                        header = {},
                                        footer = {
                                            ArticleVideoAttachmentContent(viewModel.attachment)
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.End,
                                            ) {
                                                if (!pinAnswerDate) {
                                                    DateTexts()
                                                }
                                                if (viewModel.ipInfo != null) {
                                                    Text(
                                                        "IP属地：${viewModel.ipInfo}",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                            ContentEndMarker()
                                            Spacer(modifier = Modifier.height((16 + 36).dp))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .pageTurnViewportWithGuide(pageTurnTarget),
                    )
                    // 状态栏渐变遮罩，仅 duo3 路径需要；主视觉路径不绘制。
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusBarHeight + 16.dp)
                            .background(
                                Brush.verticalGradient(smoothGradient(surfaceColor, 0.8f)),
                            ),
                    ) {}
                }
            }
        }
    }

    val nav = answerNavigationState.answerNavigator
    // 本 fork 默认关了「下一个回答」按钮，第一次读到回答底部时提示一次上拉切换
    val hasNextAnswer = nav?.nextAnswer != null
    LaunchedEffect(usesVerticalAnswerSwitch, hasNextAnswer) {
        if (!usesVerticalAnswerSwitch || !hasNextAnswer || settings.getBoolean("answerSwitchHintShown", false)) return@LaunchedEffect
        snapshotFlow { scrollState.value > 0 && scrollState.value >= latestEffectiveScrollMaxValue }.first { it }
        settings.putBoolean("answerSwitchHintShown", true)
        userMessages.showMessage("到底后继续上拉，可切换到下一个回答", UserMessageDuration.Long)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("article_screen_root")
            .then(answerDoubleTapModifier)
            .swipeBack(PointerEventPass.Main),
    ) {
        // 根据模式渲染
        if (article.type == ArticleType.Answer && answerSwitchMode == "vertical") {
            AnswerVerticalOverscroll(
                previousAnswer = nav?.previousAnswer,
                nextAnswer = nav?.nextAnswer,
                onNavigatePrevious = answerNavigationState::navigateToPrevious,
                onNavigateNext = answerNavigationState::navigateToNext,
                isAtTop = { scrollState.value == 0 },
                isAtBottom = { scrollState.value >= effectiveScrollMaxValue },
                scrollState = scrollState,
                answerSwitchSensitivity = answerSwitchSensitivity,
            ) {
                MainContent()
            }
        } else if (article.type == ArticleType.Answer && answerSwitchMode == "horizontal") {
            AnswerHorizontalOverscroll(
                canGoPrevious = nav?.previousAnswer != null,
                canGoNext = nav?.nextAnswer != null,
                onNavigatePrevious = answerNavigationState::navigateToPrevious,
                onNavigateNext = answerNavigationState::navigateToNext,
                previousContent = nav?.previousAnswer?.let { cached ->
                    { CachedAnswerPreview(cached, useTiqianMarkdown) }
                },
                nextContent = nav?.nextAnswer?.let { cached ->
                    { CachedAnswerPreview(cached, useTiqianMarkdown) }
                },
                answerSwitchSensitivity = answerSwitchSensitivity,
            ) {
                MainContent()
            }
        } else {
            MainContent()
        }

        // 本 fork：正文滑动时不显示右侧的进度条（上游 VerticalReadingProgressBar 保留未调用）

        // 跳转按钮需要压在问题区和回答区之上。
        if (article.type == ArticleType.Answer && buttonSkipAnswer && !isImmersiveMode) {
            val isAtTop by remember(scrollState) {
                derivedStateOf { scrollState.value == 0 }
            }
            val showSkipButton = !autoHideSkipAnswerButton || bottomBarState.isScrollingUp || isAtTop
            val skipButtonAlpha by animateFloatAsState(
                targetValue = if (showSkipButton) 1f else 0f,
                animationSpec = tween(200),
                label = "skipButtonAlpha",
            )
            var fabClickCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(fabClickCount) {
                if (fabClickCount > 0) {
                    delay(350)
                    if (fabClickCount >= 2) {
                        isImmersiveMode = !isImmersiveMode
                    } else {
                        if (showSkipButton) {
                            answerNavigationState.navigateToNext()
                        }
                    }
                    fabClickCount = 0
                }
            }
            DraggableRefreshButton(
                modifier = Modifier.graphicsLayer { alpha = skipButtonAlpha },
                onClick = { fabClickCount++ },
                preferenceName = "buttonSkipAnswer",
            ) {
                if (answerNavigationState.navigatingToNextAnswer) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                } else {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一个回答")
                }
            }
        }
    }

    // 全屏菜单
    ArticleActionsMenu(
        article = article,
        viewModel = viewModel,
        showMenu = showActionsMenu,
        onDismissRequest = { showActionsMenu = false },
        onSummaryRequest = {
            showSummaryDialog = true
            viewModel.requestAiSummary(environment)
        },
        onExportRequest = { showExportDialog = true },
        onSetImmersiveDoubleTap = {
            showActionsMenu = false
            // 沉浸式模式下，按返回键优先退出沉浸式，不会直接退出回答
            isImmersiveMode = !isImmersiveMode
            userMessages.showMessage("已进入沉浸式，按返回键即可退出")
        },
    )

    ArticleSummarySheet(
        showDialog = showSummaryDialog,
        summaryText = viewModel.aiSummaryText,
        loading = viewModel.aiSummaryLoading,
        errorMessage = viewModel.aiSummaryError,
        onDismissRequest = {
            showSummaryDialog = false
            viewModel.cancelAiSummary()
        },
        onRetryRequest = {
            viewModel.requestAiSummary(environment)
        },
    )

    // 沉浸式模式下，返回键优先退出沉浸式
    PlatformBackHandler(enabled = isImmersiveMode) {
        isImmersiveMode = false
    }

    PlatformBackHandler(showActionsMenu) {
        showActionsMenu = false
    }

    // 使用新的收藏夹对话框组件
    CollectionDialogComponent(
        showDialog = showCollectionDialog,
        onDismiss = { showCollectionDialog = false },
        collections = viewModel.collections,
        onLoadCollections = { viewModel.loadCollections(environment) },
        onToggleFavorite = { collection ->
            viewModel.toggleFavorite(collection.id, collection.isFavorited, environment)
        },
        onCreateCollection = { title, description, isPublic ->
            viewModel.createNewCollection(environment, title, description, isPublic)
        },
    )

    CommentScreenComponent(
        showComments = showComments,
        onDismiss = { showComments = false },
        content = article,
        contentTitle = viewModel.title,
        isZhPlusAuthorContent = article.type == ArticleType.Answer &&
            viewModel.authorId == DataHolder.ZH_PLUS_AUTHOR_USER_ID,
    )
    VotersSheet(
        show = showVoters,
        title = "${formatCompactCount(viewModel.votersTotal)} 人赞同了该回答",
        voters = viewModel.voters,
        isLoading = viewModel.votersLoading,
        errorMessage = viewModel.votersError,
        canLoadMore = viewModel.votersNextUrl != null,
        onDismissRequest = { showVoters = false },
        onLoadMore = { viewModel.loadMoreVoters(environment) },
        onRetry = { viewModel.loadMoreVoters(environment, reset = viewModel.voters.isEmpty()) },
        onNavigate = { person ->
            showVoters = false
            navigator.onNavigate(person)
        },
    )
    if (showDoubleTapActionDialog) {
        MyModalBottomSheet(
            onDismissRequest = { showDoubleTapActionDialog = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "设置双击回答动作",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "选择以后双击回答时默认执行的动作。选择后会立即保存到设置，你也可以稍后在设置中修改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.None)
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.None.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为无操作")
                }
                Button(
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.VoteUp)
                        upVoteFromDoubleTap()
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.VoteUp.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为点赞")
                }
                Button(
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.OpenComments)
                        showComments = true
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.OpenComments.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为打开评论区")
                }
                Button(
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.ToggleImmersive)
                        isImmersiveMode = !isImmersiveMode
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.ToggleImmersive.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为开关沉浸式")
                }
            }
        }
    }
    // 导出对话框
    ExportDialogComponent(
        showDialog = showExportDialog,
        isHtmlExportSupported = isArticleHtmlExportSupported,
        isImageExportSupported = isArticleImageExportSupported,
        onDismiss = { showExportDialog = false },
        onExportHtml = { includeAppAttribution, onComplete ->
            viewModel.exportToHtml(environment, includeAppAttribution, onComplete)
        },
        onExportImage = { includeAppAttribution, onComplete ->
            viewModel.exportToImage(environment, includeAppAttribution, onComplete)
        },
        onExportMarkdown = {
            viewModel.exportToClipboard(environment)
        },
        onExportImageWithComments = { commentCount, includeAppAttribution, onComplete ->
            viewModel.exportToImageWithComments(environment, commentCount, includeAppAttribution, onComplete)
        },
    )
}
