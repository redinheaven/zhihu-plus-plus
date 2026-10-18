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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.cachedContentDetail
import com.github.zly2006.zhihu.data.fetchZhihuContentDetail
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SEARCH_BLOCK_TAGS = "p, h1, h2, h3, h4, h5, h6, li, blockquote, pre, figcaption"

/** 本地收藏库里的一条回答或文章：只存标题和按段落拆好的纯文本，供关键词搜索 */
@Serializable
data class CollectionSearchEntry(
    val type: ArticleType,
    val id: Long,
    val title: String,
    val paragraphs: List<String>,
)

data class CollectionSearchHit(
    val entry: CollectionSearchEntry,
    val sentence: String,
)

/**
 * 本 fork：收藏夹关键词搜索。收藏夹列表接口不带正文，离线正文又只留 7 天，所以第一次搜索时把所有收藏夹的回答和文章
 * 列一遍，本地没有正文的逐条拉详情，抽成纯文本存进 [indexFile]（永久保留）；之后每次进程第一次搜索只补新收藏的、
 * 去掉已取消收藏的。
 */
class CollectionSearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var hits by mutableStateOf<List<CollectionSearchHit>>(emptyList())
        private set

    /** 同步中为（已处理，待拉正文总数）；列收藏夹条目阶段总数为 0 */
    var syncProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var syncError by mutableStateOf<String?>(null)
        private set

    private var entries: Map<String, CollectionSearchEntry> = emptyMap()
    private var syncStarted = false
    private var searchJob: Job? = null
    private val indexJson = Json { ignoreUnknownKeys = true }

    fun updateQuery(value: String) {
        query = value
        search()
    }

    private fun search() {
        val keyword = query.trim()
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val result = if (keyword.isEmpty()) {
                emptyList()
            } else {
                entries.values
                    .asSequence()
                    .flatMap { entry ->
                        entry.paragraphs.asSequence().filter { it.contains(keyword, ignoreCase = true) }.flatMap { paragraph ->
                            // 按句末标点切句，只留含关键词的句子
                            paragraph
                                .split(SENTENCE_END)
                                .filter { it.contains(keyword, ignoreCase = true) }
                                .map { CollectionSearchHit(entry, it.trim()) }
                        }
                    }.take(MAX_HITS)
                    .toList()
            }
            withContext(Dispatchers.Main) { hits = result }
        }
    }

    fun ensureSynced(environment: ZhihuApiEnvironment, urlToken: String, indexFile: Path) {
        if (syncStarted) return
        syncStarted = true
        viewModelScope.launch(Dispatchers.Default) {
            entries = runCatching {
                if (SystemFileSystem.exists(indexFile)) {
                    indexJson
                        .decodeFromString<List<CollectionSearchEntry>>(SystemFileSystem.source(indexFile).buffered().use { it.readString() })
                        .associateBy { "${it.type}:${it.id}" }
                } else {
                    emptyMap()
                }
            }.getOrDefault(emptyMap())
            search()
            withContext(Dispatchers.Main) {
                syncProgress = 0 to 0
                syncError = null
            }
            try {
                suspend fun allPages(firstUrl: String): List<JsonElement> = buildList {
                    var url = firstUrl
                    while (true) {
                        val json = environment.fetchJson(url, "") ?: break
                        json["data"]?.jsonArray?.let(::addAll)
                        val paging = json["paging"]?.let { ZhihuJson.decodeJson<ZhihuPaging>(it) } ?: break
                        if (paging.isEnd || paging.next.isBlank()) break
                        url = paging.next
                        delay(NETWORK_INTERVAL_MS)
                    }
                }

                val collected = linkedMapOf<String, Article>()
                allPages("https://www.zhihu.com/api/v4/people/$urlToken/collections").forEach { collection ->
                    val collectionId = collection.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                    allPages("https://www.zhihu.com/api/v4/collections/$collectionId/items").forEach { item ->
                        val article = runCatching { ZhihuJson.decodeJson<CollectionItem>(item) }
                            .getOrNull()
                            ?.content
                            ?.navDestination as? Article
                        if (article != null) collected["${article.type}:${article.id}"] = article
                    }
                }

                entries = entries.filterKeys { it in collected }
                val missing = collected.filterKeys { it !in entries }.values.toList()
                var failed = 0
                missing.forEachIndexed { index, article ->
                    withContext(Dispatchers.Main) { syncProgress = index to missing.size }
                    val detail = cachedContentDetail(article)
                        ?: run {
                            // 联网拉的放慢，不在几分钟里连发几百个请求
                            delay(NETWORK_INTERVAL_MS)
                            runCatching {
                                fetchZhihuContentDetail(article, prefetchImages = false) { url, include -> environment.fetchJson(url, include) }
                            }.getOrNull()
                        }
                    val (title, html) = when (detail) {
                        is DataHolder.Answer -> detail.question.title to detail.content
                        is DataHolder.Article -> detail.title to detail.content
                        else -> null to null
                    }
                    if (title == null || html == null) {
                        failed++
                    } else {
                        val body = Ksoup.parseBodyFragment(html).body()
                        val blocks = body.select(SEARCH_BLOCK_TAGS).filter { it.select(SEARCH_BLOCK_TAGS).size == 1 }
                        val paragraphs = (if (blocks.isEmpty()) listOf(body.text()) else blocks.map { it.text() })
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                        entries = entries + ("${article.type}:${article.id}" to CollectionSearchEntry(article.type, article.id, title, paragraphs))
                    }
                    if (index % 20 == 19) saveIndex(indexFile)
                }
                saveIndex(indexFile)
                if (failed > 0) withContext(Dispatchers.Main) { syncError = "$failed 条收藏没拿到正文，未加入搜索" }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CollectionSearch", "Failed to sync collection index", e)
                saveIndex(indexFile)
                withContext(Dispatchers.Main) { syncError = "建立收藏索引失败：${e.message}" }
                // 下次搜索时重试
                syncStarted = false
            } finally {
                withContext(Dispatchers.Main) { syncProgress = null }
                search()
            }
        }
    }

    private fun saveIndex(indexFile: Path) {
        runCatching {
            val serialized = indexJson.encodeToString(entries.values.toList())
            SystemFileSystem.sink(indexFile).buffered().use { it.writeString(serialized) }
        }
    }

    private companion object {
        const val MAX_HITS = 300
        const val NETWORK_INTERVAL_MS = 1_500L
        val SENTENCE_END = Regex("(?<=[。！？!?；;])")
    }
}

/**
 * 给 [html] 里 [keyword] 的一处出现包上 `<mark>`，供正文高亮并滚动定位：优先选所在段落含 [sentence] 的那处，
 * 找不到时用第一处。关键词被加粗、链接等格式拆开时找不到，原样返回。
 */
fun markCollectionSearchKeyword(
    html: String,
    keyword: String,
    sentence: String,
): String {
    val document = Ksoup.parseBodyFragment(html)
    document.outputSettings().prettyPrint(false)
    val candidates = document
        .body()
        .getAllElements()
        .flatMap { it.textNodes() }
        .filter { it.getWholeText().contains(keyword, ignoreCase = true) }
    val target: TextNode = candidates.firstOrNull { node ->
        (node.parent() as? Element)
            ?.closest(SEARCH_BLOCK_TAGS)
            ?.text()
            ?.contains(sentence.trim()) == true
    } ?: candidates.firstOrNull() ?: return html
    val start = target.getWholeText().indexOf(keyword, ignoreCase = true).takeIf { it >= 0 } ?: return html
    val matched = if (start > 0) target.splitText(start) else target
    if (keyword.length < matched.getWholeText().length) matched.splitText(keyword.length)
    matched.wrap("<mark></mark>")
    return document.body().html()
}
