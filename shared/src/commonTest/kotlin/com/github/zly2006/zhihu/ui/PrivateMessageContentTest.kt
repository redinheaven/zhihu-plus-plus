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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.ui.graphics.Color
import com.github.zly2006.zhihu.data.ZhihuPrivateMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateMessageContentTest {
    @Test
    fun parsesVisibleHtmlAnchorsOnly() {
        val content = ZhihuPrivateMessage(
            text = "周报已生成，<a href=\"https://www.zhihu.com/creator/weekly\">查看周报</a>；活动地址见下方。",
        ).displayContent(Color.Blue) {}

        assertEquals("周报已生成，查看周报；活动地址见下方。", content.text)
        assertEquals(1, content.getLinkAnnotations(0, content.length).size)
    }

    @Test
    fun keepsDangerousAndRelativeTargetsAsPlainText() {
        val content = ZhihuPrivateMessage(
            text =
                "<a href=\"javascript:alert(1)\">危险链接</a> " +
                    "<a href=\"/question/123\">相对链接</a>",
        ).displayContent(Color.Blue) {}

        assertEquals("危险链接 相对链接", content.text)
    }
}
