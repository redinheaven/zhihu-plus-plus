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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.NotificationItem
import com.github.zly2006.zhihu.data.NotificationTarget
import com.github.zly2006.zhihu.data.ZhihuInboxThread
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.NotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationCategoryViewModel
import com.github.zly2006.zhihu.viewmodel.NotificationEnvironment
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

@Composable
expect fun rememberNotificationEnvironment(
    settingsStore: NotificationSettingsStore,
): NotificationEnvironment

/**
 * 通知主页：三个网页端通知分类、邀请回答入口和私信会话列表，全部走网页端接口（见 [NotificationCategory]）。
 * 应用内显示过滤与自动已读仍由 [NotificationSettingsStore] 控制，不能借布局调整改变默认通知偏好。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen() {
    val navigator = LocalNavigator.current
    val settingsStore = rememberNotificationSettingsStore()
    val viewModel = viewModel { NotificationViewModel() }
    val environment = rememberNotificationEnvironment(settingsStore)
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, environment) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(environment)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("消息") },
                navigationIcon = {
                    IconButton(onClick = navigator.onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (viewModel.unreadCount > 0) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                if (viewModel.markAllAsRead(environment)) {
                                    userMessages.showMessage("已全部标记为已读")
                                } else {
                                    userMessages.showMessage("标记已读失败")
                                }
                            }
                        }) {
                            Icon(Icons.Default.MarkChatRead, contentDescription = "已读")
                        }
                    }
                    IconButton(onClick = { navigator.onNavigate(Notification.NotificationSettings()) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.refresh(environment) },
            modifier = Modifier.padding(paddingValues),
        ) {
            PaginatedList(
                items = viewModel.allData,
                onLoadMore = { viewModel.loadMore(environment) },
                isEnd = { viewModel.isEnd },
                modifier = Modifier.fillMaxSize(),
                footer = ProgressIndicatorFooter,
                key = { it.id.ifBlank { it.participant.id.orEmpty() } },
                topContent = {
                    item(key = "notification_categories") {
                        NotificationCategoryRow(
                            unreadCounts = viewModel.categoryUnreadCounts,
                            showUnreadBadges = settingsStore.getUnreadBadgeEnabled(),
                            onCategoryClick = { category ->
                                navigator.onNavigate(Notification.Entry(category.entryName, category.title))
                            },
                        )
                    }
                    item(key = "notification_invitation") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        NotificationInvitationRow(onClick = { navigator.onNavigate(Notification.Invitations) })
                    }
                    item(key = "notification_messages_divider") {
                        HorizontalDivider(
                            thickness = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                    }
                },
            ) { thread ->
                NotificationConversationRow(
                    thread = thread,
                    showUnreadBadge = settingsStore.getUnreadBadgeEnabled(),
                    onClick = {
                        val peerId = thread.participant.id
                        if (peerId.isNullOrBlank()) {
                            userMessages.showMessage("暂不支持打开此消息")
                        } else {
                            navigator.onNavigate(
                                Notification.Message(peerId, thread.participant.name, thread.participant.avatarUrl),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationCategoryRow(
    unreadCounts: Map<NotificationCategory, Int>,
    showUnreadBadges: Boolean,
    onCategoryClick: (NotificationCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NotificationCategory.entries.forEach { category ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("notification_category_${category.entryName}")
                    .clickable { onCategoryClick(category) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BadgedBox(
                    badge = {
                        val unreadCount = unreadCounts[category] ?: 0
                        if (showUnreadBadges && unreadCount > 0) {
                            Badge {
                                Text(formatUnreadCount(unreadCount))
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = category.homeIcon(),
                        contentDescription = category.title,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotificationInvitationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_invitation")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContactPage, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "邀请回答",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NotificationConversationRow(
    thread: ZhihuInboxThread,
    showUnreadBadge: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_message_${thread.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarUrl = thread.participant.avatarUrl
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.participant.name.ifBlank { "私信" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.updatedTime > 0) {
                    Text(
                        text = formatRelativeTime(thread.updatedTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = Ksoup.parse(thread.snippet).text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showUnreadBadge && thread.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Badge {
                Text(formatUnreadCount(thread.unreadCount))
            }
        }
    }
}

/** 一个网页端通知分类下的列表（[Notification.Entry]） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCategoryScreen(entry: Notification.Entry) {
    val navigator = LocalNavigator.current
    val settingsStore = rememberNotificationSettingsStore()
    val environment = rememberNotificationEnvironment(settingsStore)
    val userMessages = rememberUserMessageSink()
    val category = NotificationCategory.entries.firstOrNull { it.entryName == entry.entryName } ?: NotificationCategory.Default
    val viewModel = viewModel(key = "notification_category_${category.entryName}") {
        NotificationCategoryViewModel(category)
    }

    LaunchedEffect(category) {
        if (viewModel.allData.isEmpty()) {
            viewModel.refresh(environment)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(entry.title.ifBlank { category.title }) },
                navigationIcon = {
                    IconButton(onClick = navigator.onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.refresh(environment) },
            modifier = Modifier.padding(paddingValues),
        ) {
            PaginatedList(
                items = viewModel.allData,
                onLoadMore = { viewModel.loadMore(environment) },
                isEnd = { viewModel.isEnd },
                modifier = Modifier.fillMaxSize(),
                footer = ProgressIndicatorFooter,
                key = { it.id },
            ) { notification ->
                if (viewModel.shouldShowNotification(settingsStore, notification)) {
                    NotificationItemView(
                        notification = notification,
                        onClick = {
                            notification.navDestination()?.let(navigator.onNavigate)
                                ?: userMessages.showMessage("暂不支持打开此通知")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItemView(
    notification: NotificationItem,
    onClick: () -> Unit,
) {
    val backgroundColor = if (notification.isRead) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    notification.content.extend?.icon?.let { icon ->
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = buildNotificationText(notification),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!notification.isRead) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = "未读",
                        modifier = Modifier.size(8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            notification.target?.let { target ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        target.title?.let { title ->
                            Text(
                                text = Ksoup.parse(title).text(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        target.content?.takeIf { it.isNotBlank() }?.let { content ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Ksoup.parse(content).text(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatRelativeTime(notification.createTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun buildNotificationText(notification: NotificationItem) = buildAnnotatedString {
    val content = notification.content
    content.actors.forEachIndexed { index, actor ->
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(actor.name)
        }
        if (index < content.actors.size - 1) append("、")
    }
    if (notification.mergeCount > 1 && content.actors.size != notification.mergeCount) {
        append(" 等${notification.mergeCount}人")
    }
    append(" ")
    append(content.verb.trim())
    content.target?.let { target ->
        append(" ")
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(target.text)
        }
    }
}

private fun NotificationItem.navDestination(): NavDestination? = when (val target = target) {
    is NotificationTarget.Comment -> target.target?.navDestination
    is NotificationTarget.Question -> target.id.toLongOrNull()?.let { Question(it, Ksoup.parse(target.title).text()) }
    is NotificationTarget.People -> Person(target.id, target.urlToken, name = target.name)
    is NotificationTarget.Answer -> target.id.toLongOrNull()?.let {
        Article(title = target.title, type = ArticleType.Answer, id = it, excerpt = target.excerpt)
    }
    is NotificationTarget.Article -> target.id.toLongOrNull()?.let {
        Article(title = target.title, type = ArticleType.Article, id = it, excerpt = target.excerpt)
    }
    is NotificationTarget.Pin -> target.id.toLongOrNull()?.let { Pin(it) }
    null -> null
}

private fun NotificationCategory.homeIcon(): ImageVector = when (this) {
    NotificationCategory.Default -> Icons.AutoMirrored.Outlined.Comment
    NotificationCategory.VoteThank -> Icons.Filled.Favorite
    NotificationCategory.Follow -> Icons.Filled.PersonAddAlt1
}

internal fun MobileNotificationTimelineItem.avatarUrl(): String =
    head?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: head?.author?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: target?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: content?.subIcon?.takeIf { it.isNotBlank() }
        ?: ""

private fun formatUnreadCount(count: Int): String = if (count > 99) "99+" else count.toString()
