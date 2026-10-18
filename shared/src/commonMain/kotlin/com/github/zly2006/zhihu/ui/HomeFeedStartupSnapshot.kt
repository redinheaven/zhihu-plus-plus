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

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

const val AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY = "autoRefreshHomeOnStartup"
const val LEGACY_HOME_FEED_STARTUP_CACHE_FILE_NAME = "home_feed_startup_cache.json"
private const val HOME_FEED_STARTUP_CACHE_FILE_PREFIX = "home_feed_startup_cache_"
private const val HOME_FEED_STARTUP_CACHE_FILE_SUFFIX = ".json"

private const val HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS = 10

fun homeFeedStartupCacheFileName(recommendationMode: RecommendationMode): String =
    HOME_FEED_STARTUP_CACHE_FILE_PREFIX + recommendationMode.key + HOME_FEED_STARTUP_CACHE_FILE_SUFFIX

/** 阅读期间预取好、还没看过的下一轮刷新；冷启动时优先显示（不论是否开启启动自动刷新），用后删除 */
fun homeFeedPrefetchCacheFileName(recommendationMode: RecommendationMode): String =
    "home_feed_prefetch_" + recommendationMode.key + HOME_FEED_STARTUP_CACHE_FILE_SUFFIX

/** 切出 App 时的当前列表；进程在后台被系统杀掉后切回时原样恢复，不刷新 */
fun homeFeedCurrentListCacheFileName(recommendationMode: RecommendationMode): String =
    "home_feed_current_" + recommendationMode.key + HOME_FEED_STARTUP_CACHE_FILE_SUFFIX

fun homeFeedStartupCacheFileNames(): List<String> =
    listOf(LEGACY_HOME_FEED_STARTUP_CACHE_FILE_NAME) + RecommendationMode.entries.map(::homeFeedStartupCacheFileName)

/** [maxItems]：启动快照只要首屏；预取的下一轮和切出时的当前列表要整批存，不能丢 */
fun encodeHomeFeedStartupSnapshot(items: List<FeedDisplayItem>, maxItems: Int = HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS): String? {
    // raw 的多态内容自带 type 字段，会与 kotlinx.serialization 的默认类型判别字段冲突；启动恢复也不需要这份可重新获取的详情缓存。
    val snapshotItems = items.take(maxItems).map { it.copy(raw = null) }
    if (snapshotItems.isEmpty()) return null

    return try {
        ZhihuJson.json.encodeToString(snapshotItems)
    } catch (error: Exception) {
        Log.e("HomeFeedStartupSnapshot", "Failed to encode home feed startup snapshot", error)
        null
    }
}

fun decodeHomeFeedStartupSnapshot(serialized: String?): List<FeedDisplayItem> {
    if (serialized.isNullOrBlank()) return emptyList()

    return runCatching {
        ZhihuJson
            .json
            .decodeFromString<List<FeedDisplayItem>>(serialized)
    }.getOrDefault(emptyList())
}
