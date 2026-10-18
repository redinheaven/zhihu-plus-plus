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

package com.github.zly2006.zhihu.viewmodel.filter

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.hasIdentityCertification
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.questionAuthor
import com.github.zly2006.zhihu.data.questionId
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

class ForegroundReadFilterPipeline(
    private val settings: FeedFilterSettings,
    private val contentFilterManager: ContentFilterManager,
    private val contentOpenEventDao: ContentOpenEventDao,
    private val blockedFeedRecordDao: BlockedFeedRecordDao,
) {
    suspend fun filter(items: List<FeedDisplayItem>): List<FeedDisplayItem> {
        if (settings.reverseBlock || !settings.enableContentFilter) {
            return items
        }

        val itemIdentityPairs = items.map { item -> item to item.resolveContentIdentity() }
        val viewedContentIds = contentFilterManager.getAlreadyViewedContentIds(
            itemIdentityPairs.map { (_, identity) -> identity.type to identity.id },
        )
        val openedContentKeys = contentOpenEventDao
            .getOpenedContentKeysByKeys(
                itemIdentityPairs.map { (_, identity) -> "${identity.type}:${identity.id}" },
            ).toSet()

        val keptItems = mutableListOf<FeedDisplayItem>()
        val blockedItems = mutableListOf<Pair<FilterableContent, String>>()

        itemIdentityPairs.forEach { (item, identity) ->
            val isViewed = ContentViewRecord.generateId(identity.type, identity.id) in viewedContentIds ||
                "${identity.type}:${identity.id}" in openedContentKeys
            val isFollowing = item.feed
                ?.target
                ?.author
                ?.isFollowing ?: false
            val isLowQualityAndroidFeed = isLowQualityForegroundFeed(item)

            if (isFollowing || (!isViewed && !isLowQualityAndroidFeed)) {
                keptItems.add(item)
            } else {
                blockedItems.add(
                    item.toFilterableContent(identity, DataHolder.DummyContent) to "已读过且未关注作者",
                )
            }
        }

        if (blockedItems.isNotEmpty()) {
            saveBlockedFeedRecords(blockedFeedRecordDao, blockedItems)
        }

        return keptItems
    }

    /** 卡片真正滚进屏幕时记为已展示，以后再推就被 [filter] 去掉；加载了但没划到的不记，以后还能被推回来 */
    suspend fun recordViews(items: List<FeedDisplayItem>) {
        if (settings.reverseBlock || !settings.enableContentFilter) return
        items.forEach { item -> item.resolveContentIdentity().let { contentFilterManager.recordContentView(it.type, it.id) } }
    }
}

private fun isLowQualityForegroundFeed(item: FeedDisplayItem): Boolean =
    item.details.contains("小时前") ||
        item.details.contains("分钟前") ||
        item.details.contains("浏览")

data class FeedContentFilterResult(
    val kept: List<FilterableContent>,
    val blocked: List<Pair<FilterableContent, String>>,
)

class FeedContentFilterPipeline(
    private val settings: FeedFilterSettings,
    private val blockedKeywordDao: BlockedKeywordDao,
    private val blockedUserDao: BlockedUserDao,
    private val blockedQuestionAuthorDao: BlockedQuestionAuthorDao,
    private val blockedQuestionDao: BlockedQuestionDao,
    private val blockedTopicDao: BlockedTopicDao,
    private val blockedKeywordService: BlockedKeywordService,
    private val onNlpBlocked: suspend (List<FilterableContent>) -> Unit = {},
) {
    suspend fun topicBlockingActive(): Boolean = settings.enableTopicBlocking && blockedTopicDao.getTopicCount() > 0

    suspend fun filter(contents: List<FilterableContent>): FeedContentFilterResult {
        val blocked = mutableListOf<Pair<FilterableContent, String>>()
        var filteredContents = contents

        val blockedQuestionIds = blockedQuestionDao.getAllQuestionIds().toHashSet()
        if (blockedQuestionIds.isNotEmpty()) {
            val (removed, kept) = filteredContents.partition { it.questionId in blockedQuestionIds }
            removed.forEach { blocked.add(it to "屏蔽问题") }
            filteredContents = kept
        }

        if (settings.enableUserBlocking) {
            val blockedUserIds = blockedUserDao.getAllUsers().mapTo(hashSetOf()) { it.userId }
            val blockedQuestionAuthorIds = blockedQuestionAuthorDao.getAllUsers().mapTo(hashSetOf()) { it.userId }
            val kept = ArrayList<FilterableContent>(filteredContents.size)
            filteredContents.forEach { content ->
                when {
                    content.authorId in blockedUserIds ->
                        blocked.add(content to "屏蔽作者：${content.authorName ?: content.authorId}")
                    content.questionAuthorId in blockedQuestionAuthorIds ->
                        blocked.add(content to "屏蔽提问者：${content.questionAuthorName ?: content.questionAuthorId}")
                    content.authorCertified && !content.isFollowing ->
                        blocked.add(content to "屏蔽认证用户：${content.authorName ?: content.authorId}")
                    else -> kept.add(content)
                }
            }
            filteredContents = kept
        }

        val allKeywords = if (settings.enableKeywordBlocking || settings.enableNlpBlocking) {
            blockedKeywordDao.getAllKeywords()
        } else {
            emptyList()
        }

        if (settings.enableKeywordBlocking) {
            // 正则每批只编译一次，不在每张卡片、每个字段上重复编译
            val exactMatchers = allKeywords
                .filter { it.getKeywordTypeEnum() == KeywordType.EXACT_MATCH }
                .map { it.getKeywordScopeEnum() to it.matcher() }
            val (kept, removed) = filteredContents.partition { content ->
                exactMatchers.none { (scope, matches) ->
                    when (scope) {
                        KeywordScope.ALL -> listOf(content.title, content.summary, content.content)
                        KeywordScope.TITLE -> listOf(content.title)
                        KeywordScope.AUTHOR_NAME -> listOf(content.authorName)
                    }.any(matches)
                }
            }
            removed.forEach { blocked.add(it to "关键词屏蔽") }
            filteredContents = kept
        }

        val nlpPhrases = allKeywords
            .filter { it.getKeywordTypeEnum() == KeywordType.NLP_SEMANTIC }
            .map { it.keyword }
        if (settings.enableNlpBlocking && nlpPhrases.isNotEmpty()) {
            val finalFilteredContents = mutableListOf<FilterableContent>()
            val blockedThisRound = mutableListOf<FilterableContent>()

            // 语义判断走网络，并发请求，避免一批内容串行等待
            val checks = coroutineScope {
                filteredContents
                    .map { content ->
                        async {
                            blockedKeywordService.checkNLPBlockingWithWeight(
                                title = content.title,
                                excerpt = content.summary,
                                // Jev 只读开头 3000 字（见 Jev.kt），只解析 HTML 开头一段，不为长回答解析整篇
                                content = content.content?.let { Ksoup.parse(it.take(NLP_HTML_PREFIX_CHARS)).text() },
                                phrases = nlpPhrases,
                                threshold = settings.nlpSimilarityThreshold,
                            )
                        }
                    }.awaitAll()
            }

            for ((content, check) in filteredContents.zip(checks)) {
                val (shouldBlock, matchedKeywords) = check

                if (!shouldBlock) {
                    finalFilteredContents.add(content)
                } else {
                    blockedKeywordService.recordBlockedContent(
                        contentId = content.contentId,
                        contentType = content.contentType,
                        title = content.title,
                        excerpt = content.summary ?: "",
                        authorName = content.authorName,
                        authorId = content.authorId,
                        matchedKeywords = matchedKeywords,
                    )
                    val keywordNames = matchedKeywords.joinToString("、") { it.keyword }
                    blocked.add(content to "NLP语义屏蔽：$keywordNames")
                    blockedThisRound.add(content)
                }
            }

            if (blockedThisRound.isNotEmpty()) {
                onNlpBlocked(blockedThisRound)
            }

            filteredContents = finalFilteredContents
        }

        if (settings.enableTopicBlocking) {
            // 整批内容的话题一次查库，不逐条查
            val topicIdsByContent = filteredContents.associateWith { extractTopicIds(it.raw).orEmpty() }
            val allBlockedTopicIds = topicIdsByContent.values
                .flatten()
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.let { blockedTopicDao.getBlockedTopicIds(it) }
                .orEmpty()
                .toSet()
            filteredContents = filteredContents.filter { content ->
                val blockedTopicIds = topicIdsByContent.getValue(content).filter { it in allBlockedTopicIds }
                val kept = blockedTopicIds.size < settings.topicBlockingThreshold
                if (!kept) {
                    val topicName = blockedTopicIds
                        .firstOrNull()
                        ?.let { topicId -> blockedTopicDao.getTopicNameById(topicId) }
                    blocked.add(content to "屏蔽主题：$topicName")
                }
                kept
            }
        }

        return FeedContentFilterResult(filteredContents, blocked)
    }
}

private const val NLP_HTML_PREFIX_CHARS = 8000

private fun BlockedKeyword.matcher(): (String?) -> Boolean {
    val pattern = if (isRegex) {
        // 无效正则永远不匹配
        runCatching { if (caseSensitive) Regex(keyword) else Regex(keyword, RegexOption.IGNORE_CASE) }.getOrNull()
            ?: return { false }
    } else {
        null
    }
    return { text ->
        !text.isNullOrBlank() &&
            when {
                pattern != null -> pattern.containsMatchIn(text)
                else -> text.contains(keyword, ignoreCase = !caseSensitive)
            }
    }
}

fun interface ContentDetailProvider {
    suspend fun get(navDestination: NavDestination): DataHolder.Content?
}

class FeedDisplayFilterPipeline(
    private val settings: FeedFilterSettings,
    private val contentDetailProvider: ContentDetailProvider,
    private val contentFilterPipeline: FeedContentFilterPipeline,
    private val blockedFeedRecordDao: BlockedFeedRecordDao,
) {
    suspend fun filter(items: List<FeedDisplayItem>): List<FeedDisplayItem> {
        val (followedUserItems, otherItems) = if (!settings.filterFollowedUserContent) {
            items.partition { item ->
                item.feed
                    ?.target
                    ?.author
                    ?.isFollowing == true
            }
        } else {
            emptyList<FeedDisplayItem>() to items
        }

        // 本 fork：网页推荐的回答卡片自带全文、作者和所属问题（实测与详情接口逐字一致），不再逐条拉详情；
        // 只有开了话题屏蔽时要拉，因为卡片里的 bound_topic_ids 和话题屏蔽用的话题 ID 是两套编号
        val topicBlockingActive = contentFilterPipeline.topicBlockingActive()
        val itemToFilterableMap = coroutineScope {
            otherItems
                .map { item ->
                    async {
                        val identity = item.resolveContentIdentity()
                        val rawContent = if (!topicBlockingActive && item.feedAnswerContent() != null) {
                            DataHolder.DummyContent
                        } else {
                            resolveRawContent(item)
                        }

                        if (rawContent is DataHolder.DummyContent) {
                            Log.w("ContentFilterExtensions", "Failed to fetch content details for item '${item.title}'. Using dummy content for filtering.")
                        }

                        item to item.toFilterableContent(identity, rawContent)
                    }
                }.awaitAll()
                .toMap()
        }

        val filterableContents = itemToFilterableMap.values.toList()

        if (settings.reverseBlock) {
            val adIds = filterableContents
                .filter { content -> getFeedAdBlockReason(content, FeedAdBlockSettings()) != null }
                .map { it.contentId }
                .toSet()
            return items.filter { item ->
                item.resolveContentIdentity().id in adIds
            } + items.filter { it.feed is AdvertisementFeed }
        }

        val adBlockedContents = mutableListOf<Pair<FilterableContent, String>>()
        val nonAdContents = filterableContents.filter { content ->
            val blockReason = getFeedAdBlockReason(content, settings.adBlockSettings)
            if (blockReason != null) adBlockedContents.add(content to blockReason)
            blockReason == null
        }

        val contentFilterResult = contentFilterPipeline.filter(nonAdContents)
        val filteredContentIds = contentFilterResult.kept.map { it.contentId }.toSet()

        val itemToRawMap = itemToFilterableMap.entries.associate { (item, filterable) ->
            filterable.contentId to Pair(item, filterable.raw)
        }

        val filteredOtherItems = otherItems.mapNotNull { item ->
            val contentId = item.resolveContentIdentity().id
            if (contentId in filteredContentIds) {
                val (_, raw) = itemToRawMap[contentId] ?: (null to null)
                item.copy(
                    raw = raw?.takeUnless { it is DataHolder.DummyContent },
                    wordCount = ((raw as? DataHolder.Answer)?.content ?: item.feedAnswerContent())?.let { html ->
                        Ksoup.parse(html).text().count { !it.isWhitespace() }
                    },
                )
            } else {
                null
            }
        }

        val allBlocked = adBlockedContents + contentFilterResult.blocked
        if (allBlocked.isNotEmpty()) {
            saveBlockedFeedRecords(blockedFeedRecordDao, allBlocked)
        }

        return (followedUserItems + filteredOtherItems).filterDetailsKeywords()
    }

    private suspend fun resolveRawContent(item: FeedDisplayItem): DataHolder.Content = when (val dest = item.navDestination) {
        is Article -> contentDetailProvider.get(dest) ?: DataHolder.DummyContent
        is Pin -> contentDetailProvider.get(dest) ?: DataHolder.DummyContent
        else -> DataHolder.DummyContent
    }

    private fun List<FeedDisplayItem>.filterDetailsKeywords(): List<FeedDisplayItem> = filter { item ->
        detailsPostFilterKeywords.none { keyword ->
            val shouldFilter = item.details.contains(keyword)
            if (shouldFilter) {
                Log.e("ContentFilterExtensions", "Filtered item '${item.title}' due to keyword '$keyword' in details: ${item.content}")
            }
            shouldFilter
        }
    }
}

suspend fun saveBlockedFeedRecords(
    dao: BlockedFeedRecordDao,
    blocked: List<Pair<FilterableContent, String>>,
) {
    dao.insertAll(
        blocked.map { (content, reason) ->
            BlockedFeedRecord(
                title = content.title,
                questionId = content.questionId,
                authorName = content.authorName,
                authorId = content.authorId,
                url = content.url,
                content = content.content,
                blockedReason = reason,
                navDestinationJson = content.navDestinationJson,
                feedJson = content.feedJson,
            )
        },
    )
    dao.maintainLimit()
}

private val detailsPostFilterKeywords = listOf("感兴趣", "购买")

/**
 * 常见内容身份类型。
 * 用于 feed 过滤、内容打开记录和导航查询，不属于 Android 平台语义。
 */
object ContentType {
    const val ANSWER = "answer"
    const val ARTICLE = "article"
    const val QUESTION = "question"
    const val TOPIC = "topic"
    const val COLUMN = "column"
    const val VIDEO = "video"
    const val PIN = "pin"
}

/**
 * 从 feed item 提炼出的内容快照。
 * 这个结构只在 feed 过滤流水线内部流转，用来承接关键词、NLP、作者、主题等内容级规则。
 */
data class FilterableContent(
    val title: String,
    val summary: String?,
    val content: String?,
    val authorName: String?,
    val authorId: String?,
    val contentId: String,
    val contentType: String,
    val raw: DataHolder.Content,
    val isFollowing: Boolean = false,
    val questionId: Long? = null,
    val url: String? = null,
    val feedJson: String? = null,
    val navDestinationJson: String? = null,
    val questionAuthorName: String? = null,
    val questionAuthorId: String? = null,
    val authorCertified: Boolean = false,
)

data class FeedContentIdentity(
    val type: String,
    val id: String,
)

fun FeedDisplayItem.resolveContentIdentity(): FeedContentIdentity {
    val identity = navDestination?.let(ContentOpenEventSupport::toTrackedContentIdentity)
    return if (identity != null) {
        FeedContentIdentity(identity.type, identity.id)
    } else {
        FeedContentIdentity("unknown", navDestination.hashCode().toString())
    }
}

fun FeedDisplayItem.toFilterableContent(
    identity: FeedContentIdentity,
    rawContent: DataHolder.Content,
): FilterableContent = FilterableContent(
    title = title,
    summary = summary,
    content = when (rawContent) {
        is DataHolder.Answer -> rawContent.content
        is DataHolder.Article -> rawContent.content
        is DataHolder.Pin -> rawContent.contentHtml
        else -> null
    } ?: content ?: feedAnswerContent() ?: summary,
    authorName = authorName,
    authorId = rawContent.author?.id ?: feed?.target?.author?.id,
    contentId = identity.id,
    contentType = identity.type,
    raw = rawContent,
    isFollowing = rawContent.author?.isFollowing ?: feed?.target?.author?.isFollowing ?: false,
    authorCertified = feed
        ?.target
        ?.author
        ?.badgeV2
        .hasIdentityCertification() ||
        rawContent.author?.badgeV2.hasIdentityCertification(),
    questionId = when (rawContent) {
        is DataHolder.Answer -> rawContent.question.id
        is DataHolder.Question -> rawContent.id
        else -> feed?.target?.questionId ?: (navDestination as? Question)?.questionId
    },
    questionAuthorName = feed?.target?.questionAuthor?.name ?: when (rawContent) {
        is DataHolder.Answer -> rawContent.question.author?.name
        is DataHolder.Question -> rawContent.author.name
        else -> null
    },
    questionAuthorId = feed?.target?.questionAuthor?.id ?: when (rawContent) {
        is DataHolder.Answer -> rawContent.question.author?.id
        is DataHolder.Question -> rawContent.author.id
        else -> null
    },
    url = feed?.target?.url,
    feedJson = feed?.let { runCatching { feedFilterRecordJson.encodeToString(it) }.getOrNull() },
    navDestinationJson = navDestination?.let { runCatching { feedFilterRecordJson.encodeToString(it) }.getOrNull() },
)

/** 网页推荐回答卡片自带的全文；安卓推荐卡片、其他类型为 null */
private fun FeedDisplayItem.feedAnswerContent(): String? = (feed?.target as? Feed.AnswerTarget)?.content?.takeIf { it.isNotBlank() }

/** 从内容实体中提取主题 ID 列表，供 feed 过滤阶段的主题规则使用。 */
fun extractTopicIds(raw: DataHolder.Content): List<String>? = when (raw) {
    is DataHolder.Answer -> raw.question.topics.map { it.id }
    is DataHolder.Question -> raw.topics.map { it.id }
    is DataHolder.Article -> raw.topics?.map { it.id }
    is DataHolder.Pin -> raw.topics?.map { it.id }
    else -> null
}

private val DataHolder.Content.author: DataHolder.Author?
    get() = when (this) {
        is DataHolder.Answer -> this.author
        is DataHolder.Article -> this.author
        is DataHolder.Pin -> this.author
        is DataHolder.Question -> this.author
        else -> null
    }

private val feedFilterRecordJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

data class FeedAdBlockSettings(
    val blockZhihuAdPlatform: Boolean = true,
    val blockZhihuSchool: Boolean = true,
    val blockWeChatOfficialAccount: Boolean = true,
    val blockPaidContent: Boolean = true,
)

fun getFeedAdBlockReason(
    content: FilterableContent,
    settings: FeedAdBlockSettings,
): String? = when (val raw = content.raw) {
    is DataHolder.Answer -> {
        if (settings.blockPaidContent && raw.paidInfo != null) {
            "知乎盐选付费内容"
        } else {
            getLinkBasedAdReason(raw.content, settings)
        }
    }
    is DataHolder.Article -> {
        if (settings.blockPaidContent && raw.paidInfo != null) {
            "知乎盐选付费内容"
        } else {
            getLinkBasedAdReason(raw.content, settings)
        }
    }
    is DataHolder.Pin -> getLinkBasedAdReason(raw.contentHtml, settings)
    else -> null
}

private fun getLinkBasedAdReason(
    content: String,
    settings: FeedAdBlockSettings,
): String? {
    if (settings.blockZhihuAdPlatform && "xg.zhihu.com" in content) return "知乎广告平台内容"
    if (settings.blockZhihuSchool && ("d.zhihu.com" in content || "data-edu-card-id" in content)) return "知乎学堂内容"
    if (settings.blockWeChatOfficialAccount && "mp.weixin.qq.com" in content) return "微信公众号文章"
    return null
}

data class FeedFilterSettings(
    val enableContentFilter: Boolean = true,
    val reverseBlock: Boolean = false,
    val filterFollowedUserContent: Boolean = false,
    val enableKeywordBlocking: Boolean = true,
    val enableNlpBlocking: Boolean = true,
    val nlpSimilarityThreshold: Double = 0.8,
    val enableUserBlocking: Boolean = true,
    val enableTopicBlocking: Boolean = true,
    val topicBlockingThreshold: Int = 1,
    val adBlockSettings: FeedAdBlockSettings = FeedAdBlockSettings(),
)

fun SettingsStore.toFeedFilterSettings(): FeedFilterSettings = FeedFilterSettings(
    enableContentFilter = getBoolean("enableContentFilter", true),
    reverseBlock = getBoolean("reverseBlock", false),
    filterFollowedUserContent = getBoolean("filterFollowedUserContent", false),
    enableKeywordBlocking = getBoolean("enableKeywordBlocking", true),
    enableNlpBlocking = getBoolean("enableNLPBlocking", true),
    nlpSimilarityThreshold = getFloat("nlpSimilarityThreshold", 0.8f).toDouble(),
    enableUserBlocking = getBoolean("enableUserBlocking", true),
    enableTopicBlocking = getBoolean("enableTopicBlocking", true),
    topicBlockingThreshold = getInt("topicBlockingThreshold", 1),
    adBlockSettings = FeedAdBlockSettings(
        blockZhihuAdPlatform = getBoolean("blockZhihuAdPlatform", true),
        blockZhihuSchool = getBoolean("blockZhihuSchool", true),
        blockWeChatOfficialAccount = getBoolean("blockWeChatOfficialAccount", true),
        blockPaidContent = getBoolean("blockPaidContent", true),
    ),
)
