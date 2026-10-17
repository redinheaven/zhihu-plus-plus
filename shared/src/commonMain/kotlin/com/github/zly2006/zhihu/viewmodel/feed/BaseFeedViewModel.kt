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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.decodeArticleContentDetail
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.questionId
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.toPeopleAnswerDisplayItem
import com.github.zly2006.zhihu.ui.toPeopleArticleDisplayItem
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestion
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.typeOf

abstract class BaseFeedViewModel : PaginationViewModel<Feed>(typeOf<Feed>()) {
    var displayItems = mutableStateListOf<FeedDisplayItem>()
    internal var latestLoadedDisplayItems = mutableStateOf<List<FeedDisplayItem>>(emptyList())
    internal var completedPageCount by mutableIntStateOf(0)
    var isPullToRefresh by mutableStateOf(false)
        protected set

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        super.processResponse(environment, data, rawData)
        // 本 fork：「隐藏」模式在所有信息流页面生效（上游只在首页 HomeFeedViewModel 里处理），不留「已屏蔽」占位
        val hideQualityFiltered = environment.feedDisplaySettings().qualityFilterMode == QualityFilterMode.HIDE
        val loadedItems = data
            .flattenFeeds()
            .map { createDisplayItem(environment, it) }
            .filterNot { hideQualityFiltered && it.isQualityFiltered }
        addDisplayItems(loadedItems)
        latestLoadedDisplayItems.value = loadedItems
    }

    override fun refresh(environment: PaginationEnvironment) {
        displayItems.clear()
        super.refresh(environment)
    }

    suspend fun pullToRefresh(environment: PaginationEnvironment) {
        isPullToRefresh = true
        displayItems.clear()
        if (isLoading) return
        errorMessage = null
        networkUnavailable = false
        hostResolutionRetries = 0
        debugData.clear()
        allData.clear()
        lastPaging = null // 重置 lastPaging
        isLoading = true
        try {
            fetchFeeds(environment)
            // 本 fork：第一页到了就收起下拉刷新圈，后面几页在列表底部接着加载
            isPullToRefresh = false
            repeat(pagesPerRefresh - 1) {
                if (errorMessage != null || isEnd) return@repeat
                isLoading = true
                fetchFeeds(environment)
            }
        } catch (e: Exception) {
            errorHandle(e, environment)
        }
        isLoading = false
        isPullToRefresh = false
    }

    /** 首页加载失败（如断网）且列表为空时，显示联网时离线保存的回答和文章 */
    protected suspend fun showOfflineItemsIfEmpty(environment: PaginationEnvironment) {
        if (displayItems.isNotEmpty()) return
        val offlineItems = runCatching { getContentFilterDatabase().offlineContentDao().recentReadable(100) }
            .getOrDefault(emptyList())
            .mapNotNull { record ->
                runCatching {
                    val type = if (record.type == "answer") ArticleType.Answer else ArticleType.Article
                    val article = Article(type = type, id = record.key.substringAfter(':').toLong())
                    when (val content = decodeArticleContentDetail(article, Json.parseToJsonElement(record.json).jsonObject)) {
                        is DataHolder.Answer -> content.toPeopleAnswerDisplayItem()
                        is DataHolder.Article -> content.toPeopleArticleDisplayItem()
                        else -> null
                    }?.let { it.copy(details = "离线 · ${it.details}") }
                }.getOrNull()
            }
        // 离线保存的内容包含当初被屏蔽的条目，按当前屏蔽设置重新过滤
        val filteredItems = runCatching { environment.applyBackgroundHomeFeedFilter(offlineItems) }.getOrDefault(offlineItems)
        withContext(Dispatchers.Main) { addDisplayItems(filteredItems) }
    }

    open fun createDisplayItem(environment: FeedDisplayEnvironment, feed: Feed): FeedDisplayItem {
        val settings = environment.feedDisplaySettings()
        return feed.toDisplayItem(
            enableQualityFilter = settings.qualityFilterMode != QualityFilterMode.OFF,
            reverseBlock = settings.reverseBlock,
            qualityFilterSettings = settings.qualityFilter,
        )
    }

    fun addDisplayItems(newItems: List<FeedDisplayItem>) {
        newItems.forEach {
            if (displayItems.none { existing -> existing.stableKey == it.stableKey }) {
                displayItems.add(it)
            }
        }
    }

    fun handleBlockUser(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedBlockAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("无法获取屏蔽用户所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    fun handleBlockQuestionAuthor(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedQuestionAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("当前条目没有可用的提问者数据，无法屏蔽提问者")
            }
        }
    }

    fun handleBlockByKeywords(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<FeedDisplayItem, Triple<String, String, String?>>) -> Unit,
    ) {
        viewModelScope.launch {
            val contentInfo = resolveFeedKeywordBlockingContent(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (contentInfo != null) {
                onShowDialog(feedItem to contentInfo)
            } else {
                userMessages.showLongMessage("无法获取关键词屏蔽所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    /** 屏蔽用户后只移除该作者的卡片，不整页刷新；安卓推荐卡片在详情加载前没有作者 ID，按作者名匹配 */
    fun removeItemsByAuthor(userId: String, userName: String) {
        displayItems.removeAll { item ->
            val authorId = item.feed
                ?.target
                ?.author
                ?.id ?: when (val raw = item.raw) {
                is DataHolder.Answer -> raw.author.id
                is DataHolder.Article -> raw.author.id
                is DataHolder.Question -> raw.author.id
                is DataHolder.Pin -> raw.author.id
                else -> null
            }
            if (authorId.isNullOrEmpty()) item.authorName == userName else authorId == userId
        }
    }

    fun handleBlockQuestion(
        userMessages: UserMessageSink,
        questionId: Long,
        questionTitle: String,
    ) {
        viewModelScope.launch {
            try {
                getContentFilterDatabase()
                    .blockedQuestionDao()
                    .insertQuestion(BlockedQuestion(questionId = questionId, title = questionTitle))
                displayItems.removeAll { it.blockableQuestion()?.first == questionId }
                userMessages.showShortMessage("已屏蔽问题「$questionTitle」")
            } catch (e: Exception) {
                userMessages.showShortMessage("屏蔽失败: ${e.message}")
            }
        }
    }

    fun handleBlockTopic(
        userMessages: UserMessageSink,
        topicId: String,
        topicName: String,
    ) {
        viewModelScope.launch {
            try {
                getContentFilterDatabase()
                    .blockedTopicDao()
                    .insertTopic(BlockedTopic(topicId = topicId, topicName = topicName))
                userMessages.showShortMessage("已屏蔽主题「$topicName」")
                removeFeedItemsByBlockedTopic(this@BaseFeedViewModel, topicId)
            } catch (e: Exception) {
                userMessages.showShortMessage("屏蔽失败: ${e.message}")
            }
        }
    }
}

/** 卡片所属的问题（回答取其所在问题）的 ID 和标题，供「屏蔽问题」使用；文章、想法等返回 null。 */
fun FeedDisplayItem.blockableQuestion(): Pair<Long, String>? = when (val raw = raw) {
    is DataHolder.Answer -> raw.question.id to raw.question.title
    is DataHolder.Question -> raw.id to raw.title
    else -> feed?.target?.questionId?.let { it to title }
}

/**
 * Merges the final home-feed filter result back into the list that was already shown optimistically.
 *
 * Only items from [HomeFeedFilterResult.foregroundItems] are touched, so older or unrelated cards in the
 * list keep their current state. A foreground item is removed when it is absent from
 * [HomeFeedFilterResult.filteredItems], and replaced when the final filter pipeline returns a matching item
 * with the same [FeedDisplayItem.stableKey]. This lets delayed quality/content filters swap an already
 * rendered card with an `已屏蔽` placeholder while preserving existing raw content if the replacement has not
 * loaded one. Reverse-block mode is intentionally ignored because it renders filtered items directly.
 */
internal fun MutableList<FeedDisplayItem>.replaceHomeFeedItemsWithFilteredResult(filterResult: HomeFeedFilterResult) {
    if (filterResult.reverseBlock) return

    val foregroundKeys = filterResult.foregroundItems.map { it.stableKey }.toSet()
    val filteredItemsByKey = filterResult.filteredItems.associateBy { it.stableKey }
    var index = 0
    while (index < size) {
        val item = this[index]
        if (item.stableKey !in foregroundKeys) {
            index++
            continue
        }

        val filteredVersion = filteredItemsByKey[item.stableKey]
        if (filteredVersion == null) {
            removeAt(index)
        } else {
            this[index] = filteredVersion.copy(raw = filteredVersion.raw ?: item.raw)
            index++
        }
    }
}
