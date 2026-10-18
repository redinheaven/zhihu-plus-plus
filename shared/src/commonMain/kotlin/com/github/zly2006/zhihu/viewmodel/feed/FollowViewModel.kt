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

import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.sourceLabel
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment

class FollowViewModel : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v3/moments?limit=10&desktop=true"

    override fun createDisplayItem(environment: FeedDisplayEnvironment, feed: Feed): FeedDisplayItem {
        val item = super.createDisplayItem(environment, feed)
        return if (item.isFiltered || feed.sourceLabel == null) {
            item
        } else {
            item.copy(details = feed.target?.detailsText ?: item.details)
        }
    }
}

class FollowRecommendViewModel : BaseFeedViewModel() {
    // 浏览器里 www.zhihu.com 关注页「推荐」的请求（web_moments），不再用 App 的 api.zhihu.com/moments_v3
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/web_moments?feed_type=recommend&page_num=0"
}
