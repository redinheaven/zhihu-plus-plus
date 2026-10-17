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
import com.github.zly2006.zhihu.platform.platformName
import com.mikepenz.aboutlibraries.Libs
import java.io.File
import java.util.Properties

internal fun desktopVersionName(): String =
    System.getProperty("zhihu.version")
        ?: DeveloperInfoSnapshot::class.java.`package`?.implementationVersion
        ?: readDesktopVersionFromGradleProperties()
        ?: "0.0.0"

private fun readDesktopVersionFromGradleProperties(): String? {
    var dir = File(System.getProperty("user.dir"))
    repeat(6) {
        val gradleProperties = File(dir, "gradle.properties")
        if (gradleProperties.isFile) {
            val properties = Properties()
            gradleProperties.inputStream().use(properties::load)
            return properties.getProperty("app.versionName")?.takeIf { it.isNotBlank() }
        }
        dir = dir.parentFile ?: return null
    }
    return null
}

@Composable
actual fun rememberDeveloperInfo(): DeveloperInfoSnapshot =
    DeveloperInfoSnapshot(networkStatus = "网络状态：桌面端使用系统网络")

@Composable
actual fun rememberOpenSourceLicensesLibraries(): Libs = remember {
    loadDesktopAboutLibrariesJson()
        ?.takeIf { it.isNotBlank() }
        ?.let { json ->
            runCatching {
                Libs.Builder().withJson(json).build()
            }.getOrElse { Libs(emptyList(), emptySet()) }
        } ?: Libs(emptyList(), emptySet())
}

private fun loadDesktopAboutLibrariesJson(): String? {
    val resourceJson = Thread
        .currentThread()
        .contextClassLoader
        ?.getResourceAsStream("aboutlibraries.json")
        ?.bufferedReader()
        ?.use { it.readText() }
    if (!resourceJson.isNullOrBlank()) {
        return resourceJson
    }

    return listOf(
        "app/build/generated/aboutLibraries/liteDebug/res/raw/aboutlibraries.json",
        "app/build/generated/aboutLibraries/fullDebug/res/raw/aboutlibraries.json",
        "app/build/intermediates/packaged_res/liteDebug/packageLiteDebugResources/raw/aboutlibraries.json",
        "app/build/intermediates/packaged_res/fullDebug/packageFullDebugResources/raw/aboutlibraries.json",
    ).firstNotNullOfOrNull { path ->
        File(path)
            .takeIf { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
    }
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
