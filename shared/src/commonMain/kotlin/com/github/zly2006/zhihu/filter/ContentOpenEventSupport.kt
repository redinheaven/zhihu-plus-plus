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

package com.github.zly2006.zhihu.filter

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.ContentOpenEvent
import com.github.zly2006.zhihu.viewmodel.filter.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class TrackedContentIdentity(
    val type: String,
    val id: String,
)

data class QuestionAnswerCandidatePartition(
    val previousCandidates: List<Article>,
    val nextCandidates: List<Article>,
)

object ContentOpenFrom {
    const val ANSWER_SWITCH = "answer_switch"
    const val COLLECTION = "collection"
    const val HISTORY = "history"
    const val HOME_FEED = "home_feed"
    const val NOTIFICATION = "notification"
    const val QUESTION_FEED = "question_feed"
    const val UNKNOWN = "unknown"
}

object ContentOpenEventSupport {
    /**
     * 每条内容（键为 `type:id`）最近一次打开的时间，供列表上的「读过」标签使用。
     * 首次用到时整表读入一次，之后随新的打开记录增量更新，避免每张卡片各查一次数据库。
     */
    val lastOpenedAt = MutableStateFlow<Map<String, Long>?>(null)
    private val lastOpenedLoadLock = Mutex()

    suspend fun ensureLastOpenedAtLoaded(database: ContentFilterDatabase) = lastOpenedLoadLock.withLock {
        if (lastOpenedAt.value == null) {
            lastOpenedAt.value = database.contentOpenEventDao().getLastOpenedTimes().associate { it.contentKey to it.openedAt }
        }
    }

    fun noteOpened(events: List<ContentOpenEvent>) = lastOpenedAt.update { times ->
        times?.let {
            it + events.map { event ->
                val key = "${event.contentType}:${event.contentId}"
                key to maxOf(event.openedAt, it[key] ?: 0)
            }
        }
    }

    /** 把上次阅读时间格式化成「3 天前」这样的相对时间 */
    fun formatReadAgo(openedAt: Long, now: Long = Clock.System.now().toEpochMilliseconds()): String {
        val minutes = (now - openedAt).coerceAtLeast(0) / 60_000
        val days = minutes / (60 * 24)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            days < 1 -> "${minutes / 60} 小时前"
            days < 2 -> "昨天"
            days < 30 -> "$days 天前"
            days < 365 -> "${days / 30} 个月前"
            else -> "${days / 365} 年前"
        }
    }

    fun toTrackedContentIdentity(destination: NavDestination): TrackedContentIdentity? = when (destination) {
        is Article -> {
            val type = when (destination.type) {
                ArticleType.Answer -> ContentType.ANSWER
                ArticleType.Article -> ContentType.ARTICLE
            }
            TrackedContentIdentity(type = type, id = destination.id.toString())
        }
        is Question -> TrackedContentIdentity(type = ContentType.QUESTION, id = destination.questionId.toString())
        is Pin -> TrackedContentIdentity(type = ContentType.PIN, id = destination.id.toString())
        else -> null
    }

    fun inferOpenFrom(
        source: NavDestination?,
        target: NavDestination,
    ): String = when {
        source is Article &&
            source.type == ArticleType.Answer &&
            target is Article &&
            target.type == ArticleType.Answer -> ContentOpenFrom.ANSWER_SWITCH
        source is Question -> ContentOpenFrom.QUESTION_FEED
        source is CollectionContent -> ContentOpenFrom.COLLECTION
        source is History -> ContentOpenFrom.HISTORY
        source is Notification -> ContentOpenFrom.NOTIFICATION
        else -> ContentOpenFrom.UNKNOWN
    }

    suspend fun recordOpenEvent(
        database: ContentFilterDatabase,
        destination: NavDestination,
        questionId: Long? = null,
        openFrom: String = ContentOpenFrom.UNKNOWN,
    ) {
        val identity = toTrackedContentIdentity(destination) ?: return
        val event = ContentOpenEvent(
            contentType = identity.type,
            contentId = identity.id,
            questionId = questionId,
            openFrom = openFrom,
        )
        database.contentOpenEventDao().insert(event)
        noteOpened(listOf(event))
    }

    suspend fun getAlreadyOpenedContentIds(
        database: ContentFilterDatabase,
        content: List<Pair<String, String>>,
    ): Set<String> = run {
        val idsToCheck = content.map { (targetType, targetId) ->
            "$targetType:$targetId"
        }
        database
            .contentOpenEventDao()
            .getOpenedContentKeysByKeys(idsToCheck)
            .toSet()
    }

    fun partitionQuestionAnswerCandidates(
        candidates: List<Article>,
        openedAnswerIds: Set<Long>,
        currentArticleId: Long,
        historyIds: Set<Long> = emptySet(),
        previousIds: Set<Long> = emptySet(),
        nextIds: Set<Long> = emptySet(),
    ): QuestionAnswerCandidatePartition {
        val previousCandidates = mutableListOf<Article>()
        val nextCandidates = mutableListOf<Article>()

        candidates.forEach { article ->
            if (article.type != ArticleType.Answer || article.id == currentArticleId || article.id in historyIds) {
                return@forEach
            }
            if (article.id in previousIds || article.id in nextIds) {
                return@forEach
            }
            if (article.id in openedAnswerIds) {
                previousCandidates.add(article)
            } else {
                nextCandidates.add(article)
            }
        }

        return QuestionAnswerCandidatePartition(
            previousCandidates = previousCandidates,
            nextCandidates = nextCandidates,
        )
    }
}
