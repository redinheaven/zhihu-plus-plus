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

package com.github.zly2006.zhihu.data

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.extractImageUrl
import com.github.zly2006.zhihu.viewmodel.filter.OfflineContent
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * 内容详情缓存管理器
 * 避免对同一内容发起重复的 API 请求
 */
object ContentDetailCache {
    private data class CacheEntry(
        val content: DataHolder.Content,
        val timestamp: Long,
    )

    private data class CacheKey(
        val contentType: String,
        val contentId: String,
    )

    private val cache = mutableMapOf<CacheKey, CacheEntry>()
    private val mutex = Mutex()
    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10分钟
    private const val MAX_CACHE_SIZE = 100

    /**
     * 获取内容详情，优先从缓存读取
     */
    suspend fun getOrFetch(
        navDestination: NavDestination,
        fetcher: suspend (NavDestination) -> DataHolder.Content?,
    ): DataHolder.Content? {
        val (contentType, contentId) = extractContentInfo(navDestination) ?: return null
        val key = CacheKey(contentType, contentId)

        // 尝试从缓存读取
        mutex.withLock {
            cache[key]?.let { entry ->
                if (Clock.System.now().toEpochMilliseconds() - entry.timestamp < CACHE_EXPIRY_MS) {
                    Log.d("ContentDetailCache", "Cache hit for $contentType:$contentId")
                    return entry.content
                } else {
                    cache.remove(key)
                }
            }
        }

        // 缓存未命中，从 API 获取
        Log.d("ContentDetailCache", "Cache miss for $contentType:$contentId, fetching...")
        val content = fetcher(navDestination) ?: return null

        // 存入缓存
        mutex.withLock {
            // LRU 淘汰策略
            if (cache.size >= MAX_CACHE_SIZE) {
                val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
                oldestKey?.let { cache.remove(it) }
            }
            cache[key] = CacheEntry(content, Clock.System.now().toEpochMilliseconds())
        }

        return content
    }

    /**
     * 从 NavDestination 提取内容类型和 ID
     */
    internal fun extractContentInfo(navDestination: NavDestination): Pair<String, String>? = when (navDestination) {
        is Article -> {
            val type = when (navDestination.type) {
                ArticleType.Answer -> "answer"
                ArticleType.Article -> "article"
            }
            Pair(type, navDestination.id.toString())
        }
        is Question -> {
            Pair("question", navDestination.questionId.toString())
        }
        is Pin -> {
            Pair("pin", navDestination.id.toString())
        }
        else -> null
    }
}

private const val OFFLINE_KEEP_DAYS = 7
private const val OFFLINE_MAX_COUNT = 2000

/**
 * 离线保存正文时预下载其中的图片，由平台设置（Android 只在不按流量计费的网络下执行）。
 */
var offlineImagePrefetcher: (List<String>) -> Unit = {}

/** 网络恢复（系统确认可以上网）时由平台发出，离线状态的首页据此自动刷新；目前只有 Android 发出。 */
val networkReconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

/**
 * 系统当前是否有已验证可上网的网络；目前只有 Android 更新，其他平台恒为 true。
 * 用来区分「真的断网」和「联着网但域名解析偶发失败」（刚从后台回来、Wi-Fi 切换、DNS 抖动）。
 */
val networkAvailable = MutableStateFlow(true)

/** 保存一条内容详情 JSON 供离线阅读。 */
private suspend fun saveOfflineContent(key: String, type: String, json: JsonObject) {
    runCatching {
        val dao = getContentFilterDatabase().offlineContentDao()
        val now = Clock.System.now().toEpochMilliseconds()
        dao.upsert(OfflineContent(key, type, json.toString(), now))
        dao.prune(before = now - OFFLINE_KEEP_DAYS * 24 * 60 * 60 * 1000L, keep = OFFLINE_MAX_COUNT)
        val contentImages = json["content"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { html -> Ksoup.parse(html).select("img").mapNotNull { extractImageUrl(it::attr) } }
            .orEmpty()
        // 作者头像一并下载，离线时卡片和正文页才有头像
        val avatar = (json["author"] as? JsonObject)?.get("avatar_url")?.jsonPrimitive?.contentOrNull
        (contentImages + listOfNotNull(avatar))
            .filter { it.startsWith("http") }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let(offlineImagePrefetcher)
    }.onFailure { Log.e("ContentDetailCache", "Failed to save offline content $key", it) }
}

fun zhihuContentDetailUrl(destination: NavDestination): String? = when (destination) {
    is Article -> when (destination.type) {
        ArticleType.Article -> "https://www.zhihu.com/api/v4/articles/${destination.id}"
        ArticleType.Answer -> "https://www.zhihu.com/api/v4/answers/${destination.id}"
    }

    is Question -> "https://www.zhihu.com/api/v4/questions/${destination.questionId}"
    is Pin -> "https://www.zhihu.com/api/v4/pins/${destination.id}"
    else -> null
}

fun zhihuContentDetailInclude(destination: NavDestination): String = when (destination) {
    is Article -> when (destination.type) {
        ArticleType.Article -> "content,topics,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,relationship,ip_info,relationship.vote,author.badge_v2"
        ArticleType.Answer -> ".settings,content,editable_content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,attachment,reaction,ip_info,pagination_info,endorsements,question.topics,question.author,reaction.relation.voting,author.badge_v2,settings.table_of_contents.enabled"
    }

    is Question -> "read_count,visit_count,answer_count,voteup_count,comment_count,follower_count,detail,excerpt,author,relationship.is_following,topics"
    is Pin -> "topics"
    else -> ""
}

suspend fun fetchZhihuContentDetail(
    destination: NavDestination,
    fetchJson: suspend (String, String) -> JsonObject?,
): DataHolder.Content? {
    val url = zhihuContentDetailUrl(destination) ?: return null
    val include = zhihuContentDetailInclude(destination)
    // 离线阅读：联网时把详情原始 JSON 存进本地数据库，请求失败（如断网）时回退到已保存的版本
    val (type, id) = ContentDetailCache.extractContentInfo(destination) ?: return null
    val offlineKey = "$type:$id"
    val json = try {
        fetchJson(url, include)?.also { fetched ->
            if (fetched.containsKey("id")) saveOfflineContent(offlineKey, type, fetched)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        runCatching { getContentFilterDatabase().offlineContentDao().get(offlineKey) }
            .getOrNull()
            ?.let { Json.parseToJsonElement(it.json).jsonObject }
            ?: throw e
    } ?: return null

    return when (destination) {
        is Article -> decodeArticleContentDetail(destination, json)
        is Question -> decodeQuestionContentDetail(json)
        is Pin -> decodePinContentDetail(json)
        else -> null
    }
}

suspend fun ContentDetailCache.getOrFetchContentDetail(
    destination: NavDestination,
    fetchJson: suspend (String, String) -> JsonObject?,
): DataHolder.Content? =
    runCatching {
        getOrFetch(destination) { navDestination ->
            fetchZhihuContentDetail(navDestination, fetchJson)
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.e("ContentDetailCache", "Failed to fetch content detail for $destination", error)
        null
    }

fun decodeArticleContentDetail(
    article: Article,
    json: JsonObject,
): DataHolder.Content {
    val normalizedJson = normalizeLongIdContentDetailJson(json)
    return when (article.type) {
        ArticleType.Answer -> ZhihuJson.decodeJson<DataHolder.Answer>(normalizedJson)
        ArticleType.Article -> ZhihuJson.decodeJson<DataHolder.Article>(normalizedJson)
    }
}

fun decodeQuestionContentDetail(json: JsonObject): DataHolder.Question =
    ZhihuJson.decodeJson(normalizeLongIdContentDetailJson(json))

fun decodePinContentDetail(json: JsonObject): DataHolder.Pin =
    ZhihuJson.decodeJson(json)

private fun normalizeLongIdContentDetailJson(jo: JsonObject): JsonObject = buildJsonObject {
    jo.entries.forEach { (key, value) ->
        if (key == "id") {
            put(key, JsonPrimitive(value.jsonPrimitive.long))
        } else {
            put(key, value)
        }
    }
}
