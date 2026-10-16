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

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 联网时下载过的内容详情原始 JSON，用于断网时离线阅读。
 */
@Entity(tableName = OfflineContent.TABLE_NAME)
data class OfflineContent(
    /** 形如 `answer:123`，与 Feed 的 stableKey 一致 */
    @PrimaryKey val key: String,
    /** answer / article / question / pin */
    val type: String,
    val json: String,
    val savedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "offline_contents"
    }
}

@Dao
interface OfflineContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(content: OfflineContent)

    @Query("SELECT * FROM ${OfflineContent.TABLE_NAME} WHERE `key` = :key")
    suspend fun get(key: String): OfflineContent?

    /** 最近保存的回答和文章，供断网时的首页列表使用 */
    @Query("SELECT * FROM ${OfflineContent.TABLE_NAME} WHERE type IN ('answer', 'article') ORDER BY savedAt DESC LIMIT :limit")
    suspend fun recentReadable(limit: Int): List<OfflineContent>

    /** 删除早于 [before] 的记录，并只保留最新的 [keep] 条 */
    @Query(
        "DELETE FROM ${OfflineContent.TABLE_NAME} WHERE savedAt < :before OR `key` NOT IN " +
            "(SELECT `key` FROM ${OfflineContent.TABLE_NAME} ORDER BY savedAt DESC LIMIT :keep)",
    )
    suspend fun prune(before: Long, keep: Int)
}
