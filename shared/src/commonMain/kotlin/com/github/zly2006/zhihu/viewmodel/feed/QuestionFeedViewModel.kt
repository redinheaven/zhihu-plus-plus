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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.hasIdentityCertification
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.QuestionAnswerNavigator
import com.github.zly2006.zhihu.navigation.zhihuQuestionFeedsUrl
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.postSigned

open class QuestionFeedViewModel(
    private val questionId: Long,
) : BaseFeedViewModel() {
    var sortOrder by mutableStateOf("default")
        private set

    override val initialUrl: String
        get() = zhihuQuestionFeedsUrl(questionId, limit = 20, order = sortOrder)

    // 问题页回答列表不带 include 不返回全文；用浏览器里 www.zhihu.com 问题页同一接口的 include
    override val include =
        "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason," +
            "is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,attachment,voteup_count," +
            "reshipment_settings,comment_permission,created_time,updated_time,review_info,relevant_info,question,excerpt," +
            "is_labeled,paid_info,paid_info_content,reaction,reaction_instruction,segment_infos,allow_segment_interaction," +
            "hot_comment,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp;" +
            "data[*].author.follower_count,vip_info,kvip_info,badge[*].topics;"

    fun updateSortOrder(order: String) {
        if (sortOrder != order) {
            sortOrder = order
        }
    }

    override fun createDisplayItem(environment: FeedDisplayEnvironment, feed: Feed): FeedDisplayItem {
        val target = feed.target
        if (target is Feed.AnswerTarget) {
            return FeedDisplayItem(
                authorName = target.author?.name ?: "未知作者",
                avatarSrc = target.author?.avatarUrl,
                summary = target.excerpt,
                details = target.detailsText,
                feed = feed,
                title = "",
            )
        }
        return super.createDisplayItem(environment, feed)
    }

    fun createAnswerNavigatorFor(
        item: FeedDisplayItem,
        environment: ZhihuApiEnvironment,
    ): QuestionAnswerNavigator? {
        val destination = item.navDestination as? Article ?: return null
        if (destination.type != ArticleType.Answer) return null
        val index = displayItems.indexOfFirst { it.stableKey == item.stableKey }
        if (index < 0) return null
        return QuestionAnswerNavigator(
            questionId = questionId,
            initialNextAnswers = displayItems
                .drop(index + 1)
                .mapNotNull { it.navDestination as? Article },
            initialPreviousAnswers = displayItems
                .take(index)
                .asReversed()
                .mapNotNull { it.navDestination as? Article },
            initialNextUrl = lastPaging?.next.orEmpty(),
            order = sortOrder,
            environment = environment,
        )
    }

    suspend fun followQuestion(environment: ZhihuApiEnvironment, follow: Boolean) {
        try {
            if (environment.authenticatedCookies()["d_c0"] == null) return
            val url = "https://www.zhihu.com/api/v4/questions/$questionId/followers"
            if (follow) {
                environment.postSigned(url)
            } else {
                environment.deleteSigned(url)
            }
        } catch (e: Exception) {
            environment.handleFetchFailure("QuestionFeedViewModel", e)
        }
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: kotlinx.serialization.json.JsonArray) {
        val blockedUserIds = environment.blockedUserIds()
        val filtered = data.filterNot { feed ->
            val author = (feed.target as? Feed.AnswerTarget)?.author
            author != null && (author.id in blockedUserIds || author.badgeV2.hasIdentityCertification() && !author.isFollowing)
        }
        super.processResponse(environment, filtered, rawData)
    }
}
