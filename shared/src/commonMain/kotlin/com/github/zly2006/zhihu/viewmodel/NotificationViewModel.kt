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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.MobileNotificationAuthor
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.NotificationItem
import com.github.zly2006.zhihu.data.ZHIHU_ME_NOTIFICATIONS_INCLUDE
import com.github.zly2006.zhihu.data.ZHIHU_ME_URL
import com.github.zly2006.zhihu.data.ZhihuChatPage
import com.github.zly2006.zhihu.data.ZhihuInboxThread
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuMeNotifications
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.ZhihuPrivateMessage
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.matchNotificationType
import com.github.zly2006.zhihu.util.Log
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.typeOf

interface NotificationSettingsEnvironment {
    val notificationSettingsStore: NotificationSettingsStore
}

interface NotificationEnvironment :
    PaginationEnvironment,
    NotificationSettingsEnvironment

/**
 * 网页端通知的三个分类（www.zhihu.com 顶栏消息面板的三个标签）。
 * 本 fork 只用网页身份：上游用 App 的 api.zhihu.com/notifications/v3，会让同一个登录凭证同时以浏览器和安卓 App 出现。
 * 网页端 v3 的分类入口（comment / like / follow）实测返回空列表，只能用 v2 的这三个。
 */
enum class NotificationCategory(
    val entryName: String,
    val title: String,
) {
    Default("default", "评论与邀请"),
    VoteThank("vote_thank", "赞同与喜欢"),
    Follow("follow", "新关注"),
    ;

    val listUrl: String
        get() = "https://www.zhihu.com/api/v4/notifications/v2/$entryName?limit=20"

    val readAllUrl: String
        get() = "https://www.zhihu.com/api/v4/notifications/v2/$entryName/actions/readall"

    fun unreadCount(counts: ZhihuMeNotifications): Int = when (this) {
        Default -> counts.defaultNotificationsCount
        VoteThank -> counts.voteThankNotificationsCount
        Follow -> counts.followNotificationsCount
    }
}

/** 通知主页：三个分类的未读数（取自 /api/v4/me）和私信会话列表（/api/v4/inbox） */
class NotificationViewModel :
    PaginationViewModel<ZhihuInboxThread>(
        dataType = typeOf<ZhihuInboxThread>(),
    ) {
    override val initialUrl = "https://www.zhihu.com/api/v4/inbox?limit=20"

    val categoryUnreadCounts = SnapshotStateMap<NotificationCategory, Int>()

    var unreadCount: Int by mutableIntStateOf(0)
        private set

    override fun refresh(environment: PaginationEnvironment) {
        super.refresh(environment)
        viewModelScope.launch {
            try {
                val counts = environment
                    .fetchJson(ZHIHU_ME_URL, ZHIHU_ME_NOTIFICATIONS_INCLUDE)
                    ?.let { ZhihuJson.decodeJson<ZhihuMeNotifications>(it) } ?: return@launch
                NotificationCategory.entries.forEach { categoryUnreadCounts[it] = it.unreadCount(counts) }
                unreadCount = counts.totalCount
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NotificationViewModel", "Failed to load notification counts", e)
            }
        }
    }

    suspend fun markAllAsRead(environment: ZhihuApiEnvironment): Boolean {
        var succeeded = true
        NotificationCategory.entries.filter { (categoryUnreadCounts[it] ?: 0) > 0 }.forEach { category ->
            val ok = try {
                environment.postSigned(category.readAllUrl).status.isSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NotificationViewModel", "Failed to mark ${category.entryName} notifications as read", e)
                false
            }
            if (ok) categoryUnreadCounts[category] = 0 else succeeded = false
        }
        unreadCount = categoryUnreadCounts.values.sum()
        return succeeded
    }
}

/** 一个分类下的通知列表（www.zhihu.com/api/v4/notifications/v2/{分类}），打开后按设置自动标记已读 */
class NotificationCategoryViewModel(
    val category: NotificationCategory,
) : PaginationViewModel<NotificationItem>(
        dataType = typeOf<NotificationItem>(),
    ) {
    override val initialUrl = category.listUrl
    private var markedAsRead = false

    override fun processResponse(environment: PaginationEnvironment, data: List<NotificationItem>, rawData: JsonArray) {
        super.processResponse(environment, data, rawData)
        val settingsStore = (environment as? NotificationSettingsEnvironment)?.notificationSettingsStore ?: return
        if (!markedAsRead && settingsStore.getAutoMarkAsReadEnabled()) {
            markedAsRead = true
            viewModelScope.launch {
                runCatching { environment.postSigned(category.readAllUrl) }
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
    }

    fun shouldShowNotification(
        settingsStore: NotificationSettingsStore,
        notification: NotificationItem,
    ): Boolean {
        val type = matchNotificationType(notification.content.verb)
        return type == null || settingsStore.getDisplayInAppEnabled(type)
    }
}

/** 邀请回答：网页端问题路由面板同样用 v3 的 invite 入口（www.zhihu.com/api/v4/notifications/v3/timeline/entry/invite） */
class NotificationTimelineViewModel :
    PaginationViewModel<MobileNotificationTimelineItem>(
        dataType = typeOf<MobileNotificationTimelineItem>(),
    ) {
    override val initialUrl =
        "https://www.zhihu.com/api/v4/notifications/v3/timeline/entry/invite?invite_with_time_slice=1&limit=20"
}

class PrivateMessageViewModel(
    private val peerId: String,
) : PaginationViewModel<ZhihuPrivateMessage>(
        dataType = typeOf<ZhihuPrivateMessage>(),
    ) {
    override val initialUrl = "https://www.zhihu.com/api/v4/chat?sender_id=$peerId&limit=20"

    var peer: MobileNotificationAuthor? by mutableStateOf(null)
        private set

    var isSending by mutableStateOf(false)
        private set

    /** 网页端私信页的发送请求：POST /api/v4/chat，JSON 正文 */
    suspend fun sendMessage(
        content: String,
        environment: PaginationEnvironment,
    ): Boolean {
        if (content.isBlank() || isSending) return false

        isSending = true
        errorMessage = null
        return try {
            val response = environment.postSigned("https://www.zhihu.com/api/v4/chat") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("content_type", "text")
                        put("receiver_id", peerId)
                        put("text", content)
                    }.toString(),
                )
            }
            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                errorMessage = runCatching {
                    ZhihuJson.json
                        .parseToJsonElement(responseText)
                        .jsonObject["error"]
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull() ?: "发送失败（${response.status.value}）"
                Log.e("PrivateMessageViewModel", "Failed to send private message: ${response.status}, $errorMessage")
                false
            } else {
                // 发送接口的返回格式没核实，发完重新拉第一页
                refresh(environment)
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PrivateMessageViewModel", "Failed to send private message", e)
            errorMessage = e.message ?: "发送失败"
            false
        } finally {
            isSending = false
        }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        try {
            // 服务端给的下一页链接是 www.zhihu.com/messages?…&after_id=，浏览器翻页时请求的是 /api/v4/chat，只取 after_id
            val afterId = lastPaging?.next?.let { runCatching { Url(it).parameters["after_id"] }.getOrNull() }
            val url = if (afterId != null) "$initialUrl&after_id=$afterId" else initialUrl
            val json = environment.fetchJson(url, "") ?: return
            val page = ZhihuJson.decodeJson<ZhihuChatPage>(json)
            if (peer == null) {
                peer = listOfNotNull(page.data.sender, page.data.receiver).firstOrNull { it.id == peerId }
            }
            val rawData = json["data"]?.jsonObject?.get("messages")?.jsonArray ?: JsonArray(emptyList())
            val existingIds = allData.mapTo(mutableSetOf()) { it.stableId }
            processResponse(environment, page.data.messages.filter { existingIds.add(it.stableId) }, rawData)
            lastPaging = page.paging ?: ZhihuPaging(isEnd = true, next = "")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            environment.handleFetchFailure(this::class.simpleName, e)
        } finally {
            isLoading = false
        }
    }
}
