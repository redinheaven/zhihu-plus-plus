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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备注最多字数。 */
const val USER_NOTE_MAX_LENGTH = 9

@Serializable
private data class UserNote(
    val name: String,
    val note: String,
)

/**
 * 作者备注：以用户 ID 为键、整体存成 JSON 放在偏好 `userNotes` 里（随「数据同步」一起同步）。
 * 安卓推荐卡片在详情加载前没有作者 ID，此时按作者名回退匹配。
 */
object UserNotes {
    private const val KEY = "userNotes"
    private var loaded = false
    private val notes = mutableStateMapOf<String, UserNote>()

    private fun load(settings: SettingsStore) {
        if (loaded) return
        loaded = true
        settings
            .getStringOrNull(KEY)
            ?.let { runCatching { Json.decodeFromString<Map<String, UserNote>>(it) }.getOrNull() }
            ?.let(notes::putAll)
    }

    fun get(settings: SettingsStore, userId: String?, userName: String?): String? {
        load(settings)
        return userId?.takeIf { it.isNotEmpty() }?.let { notes[it]?.note }
            ?: userName?.let { name -> notes.values.firstOrNull { it.name == name }?.note }
    }

    fun set(settings: SettingsStore, userId: String, userName: String, note: String) {
        load(settings)
        val trimmed = note.trim().take(USER_NOTE_MAX_LENGTH)
        if (trimmed.isEmpty()) notes.remove(userId) else notes[userId] = UserNote(userName, trimmed)
        settings.putString(KEY, Json.encodeToString(notes.toMap()))
    }
}

/** 作者名右侧的备注，没有备注时不占位。 */
@Composable
fun AuthorNoteText(
    userId: String?,
    userName: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val note = UserNotes.get(rememberSettingsStore(), userId, userName) ?: return
    Text(
        text = "（$note）",
        style = style,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}
