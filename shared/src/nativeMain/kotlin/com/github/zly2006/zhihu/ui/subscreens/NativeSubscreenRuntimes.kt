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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.platform.nativeBundledResourcePath
import com.github.zly2006.zhihu.platform.nativeIsDesktop
import com.github.zly2006.zhihu.platform.platformName
import com.mikepenz.aboutlibraries.Libs
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSFileManager

@Composable
actual fun rememberDeveloperInfo(): DeveloperInfoSnapshot =
    DeveloperInfoSnapshot(
        networkStatus = if (nativeIsDesktop) {
            "网络状态：桌面端使用系统网络"
        } else {
            "网络状态：iOS 端使用系统网络"
        },
    )

@Composable
actual fun rememberOpenSourceLicensesLibraries(): Libs = remember {
    loadNativeAboutLibrariesJson()
        ?.let { json ->
            runCatching { Libs.Builder().withJson(json).build() }
                .getOrElse { Libs(emptyList(), emptySet()) }
        } ?: Libs(emptyList(), emptySet())
}

@OptIn(ExperimentalForeignApi::class)
private fun loadNativeAboutLibrariesJson(): String? {
    val resourcePath = nativeBundledResourcePath("aboutlibraries.json") ?: return null
    val data = NSFileManager.defaultManager.contentsAtPath(resourcePath) ?: return null
    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return null
    return bytes.decodeToString().takeIf { it.isNotBlank() }
}

actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
) {
    error("$platformName 暂不支持 WebView 自定义字体设置")
}

actual val isCloudFileSyncSupported: Boolean = false

@Composable
actual fun CloudFileSyncSettings() {
    error("$platformName 暂不支持云盘文件同步")
}
