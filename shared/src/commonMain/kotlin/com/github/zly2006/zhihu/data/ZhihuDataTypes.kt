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

import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.account.DESKTOP_SEC_CH_UA
import com.github.zly2006.zhihu.account.DESKTOP_SEC_CH_UA_MOBILE
import com.github.zly2006.zhihu.account.DESKTOP_SEC_CH_UA_PLATFORM
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.util.jsonObject
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.github.zly2006.zhihu.ui.FollowedQuestion as FollowedQuestionImpl
import com.github.zly2006.zhihu.ui.FollowedTopic as FollowedTopicImpl

const val ZHIHU_ME_URL = "https://www.zhihu.com/api/v4/me"

@Serializable
data class ZhihuAccountProfile(
    val id: String = "",
    val name: String = "",
    val urlToken: String? = null,
    val userType: String = "",
    val avatarUrl: String? = null,
)

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installZhihuCommonClientConfig(
    cookies: MutableMap<String, String>,
    userAgent: String,
    onCookieChanged: () -> Unit = {},
    enableHttpCache: Boolean = false,
) {
    if (enableHttpCache) {
        install(HttpCache)
    }
    install(HttpCookies) {
        storage = ZhihuCookieStorage(cookies, onCookieChanged)
    }
    install(ContentNegotiation) {
        json(ZhihuJson.json)
    }
    install(UserAgent) {
        agent = userAgent
    }
    // UA 自称桌面 Chrome，补上浏览器都会带的高熵客户端提示，避免「Chrome UA 没有 sec-ch-ua」这种单一规则命中
    install(DefaultRequest) {
        header("sec-ch-ua", DESKTOP_SEC_CH_UA)
        header("sec-ch-ua-mobile", DESKTOP_SEC_CH_UA_MOBILE)
        header("sec-ch-ua-platform", DESKTOP_SEC_CH_UA_PLATFORM)
    }
    install(ZhihuWebFetchHeaders)
}

/**
 * 补齐 Chrome 在 www.zhihu.com 页面里发 fetch 时必带、而 Ktor 不会带的头（对照浏览器实际请求）。
 * 放在 Send 阶段：Accept 要盖掉 ContentNegotiation 加的 application/json，浏览器 fetch 发的是通配类型。
 * 只作用于知乎域名，别的域名不带 Referer 等页面信息。
 */
private val ZhihuWebFetchHeaders = createClientPlugin("ZhihuWebFetchHeaders") {
    on(Send) { request ->
        val host = request.url.host
        if (host == "zhihu.com" || host.endsWith(".zhihu.com")) {
            val headers = request.headers
            val path = request.url.encodedPath
            val isMoments = path.startsWith("/api/v3/moments") || path.startsWith("/api/v4/web_moments")
            headers[HttpHeaders.Accept] = "*/*"
            headers[HttpHeaders.AcceptLanguage] = "zh-CN,zh;q=0.9"
            if (HttpHeaders.Referrer !in headers) {
                headers[HttpHeaders.Referrer] = if (isMoments) "https://www.zhihu.com/follow" else "https://www.zhihu.com/"
            }
            // 网页端只在首页推荐和关注动态上带这个版本号
            if (isMoments || path.startsWith("/api/v3/feed/topstory/recommend")) headers["x-api-version"] = "3.0.53"
            if (request.method != HttpMethod.Get && request.method != HttpMethod.Head) {
                headers[HttpHeaders.Origin] = "https://www.zhihu.com"
            }
            headers["sec-fetch-dest"] = "empty"
            headers["sec-fetch-mode"] = "cors"
            headers["sec-fetch-site"] = if (host == "www.zhihu.com") "same-origin" else "same-site"
        }
        proceed(request)
    }
}

suspend fun fetchVerifiedZhihuAccount(client: HttpClient): JsonObject? {
    val response = client.get(ZHIHU_ME_URL)
    if (response.status != HttpStatusCode.OK) {
        return null
    }
    return response.jsonObject()
}

suspend fun fetchVerifiedZhihuSession(
    client: HttpClient,
    cookies: Map<String, String>,
    userAgent: String = DEFAULT_ZHIHU_USER_AGENT,
): ZhihuAccountSession? {
    val account = fetchVerifiedZhihuAccount(client) ?: return null
    val profile = ZhihuJson.decodeJson<ZhihuAccountProfile>(account)
    return ZhihuAccountSession(
        login = true,
        username = profile.name,
        cookies = cookies.toMutableMap(),
        userAgent = userAgent,
        profile = ZhihuAccountProfileSnapshot(
            id = profile.id,
            name = profile.name,
            urlToken = profile.urlToken,
            userType = profile.userType,
            avatarUrl = profile.avatarUrl,
        ),
        self = account,
    )
}

private val feedNavigationJson = Json {
    ignoreUnknownKeys = true
}

val FeedDisplayItem.navDestination: NavDestination?
    get() = navDestinationJson
        ?.let { runCatching { feedNavigationJson.decodeFromString<NavDestination>(it) }.getOrNull() }
        ?: feed?.target?.navDestination

fun NavDestination.toFeedDisplayItemNavDestinationJson(): String = feedNavigationJson.encodeToString<NavDestination>(this)

val Feed.Target.navDestination: NavDestination?
    get() = when (this) {
        is Feed.AnswerTarget -> Article(
            title = question.title,
            type = ArticleType.Answer,
            id = id,
            authorName = author?.name ?: "loading...",
            authorBio = author?.headline ?: "",
            avatarSrc = author?.avatarUrl,
            excerpt = excerpt,
        )

        is Feed.ArticleTarget -> Article(
            title = title,
            type = ArticleType.Article,
            id = id,
            authorName = author.name,
            authorBio = author.headline,
            avatarSrc = author.avatarUrl,
            excerpt = excerpt,
        )

        is Feed.PinTarget -> Pin(id = id, authorName = author.name)

        is Feed.QuestionTarget -> Question(
            questionId = id,
            title = title,
        )

        is Feed.VideoTarget -> null
    }

suspend fun fetchHighestQualityZhihuVideoUrl(
    httpClient: HttpClient,
    videoId: String,
    contentId: String,
    contentType: String = "answer",
    xsrfToken: String? = null,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
): String? {
    val response = httpClient.post("https://www.zhihu.com/api/v4/video/play_info?r=$videoId") {
        contentType(ContentType.Application.Json)
        xsrfToken?.let { header("x-xsrftoken", it) }
        header("x-app-za", "OS=webplayer")
        header("x-referer", "")
        setBody(
            """{"content_id":"$contentId","content_type_str":"$contentType","video_id":"$videoId","scene_code":"answer_detail_web","is_only_video":true}""",
        )
        configureRequest()
    }

    return selectHighestQualityZhihuVideoUrl(
        ZhihuJson.json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject,
    )
}

fun selectHighestQualityZhihuVideoUrl(jsonResponse: JsonObject): String? {
    val mp4List = jsonResponse["video_play"]
        ?.jsonObject
        ?.get("playlist")
        ?.jsonObject
        ?.get("mp4")
        ?.jsonArray

    var bestVideo: JsonObject? = null
    var maxBitrate = -1

    mp4List?.forEach { videoElement ->
        val video = videoElement.jsonObject
        val bitrate = video["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        if (bitrate > maxBitrate) {
            maxBitrate = bitrate
            bestVideo = video
        }
    }

    return bestVideo
        ?.get("url")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonPrimitive
        ?.content
}

object ZhihuJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Suppress("FunctionName")
    fun snakeCaseToCamelCase(snakeCase: String): String = snakeCase
        .split("_")
        .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }
        .replaceFirstChar { it.lowercase() }

    fun snakeCaseToCamelCase(json: JsonElement): JsonElement = when (json) {
        is JsonObject -> buildJsonObject {
            for ((key, value) in json) {
                // cookie/cookies 的子键是服务端签发的动态凭据名，不是模型字段，必须逐字保留。
                put(
                    snakeCaseToCamelCase(key),
                    if (key == "cookie" || key == "cookies") value else snakeCaseToCamelCase(value),
                )
            }
        }
        is JsonArray -> buildJsonArray {
            for (item in json) {
                add(snakeCaseToCamelCase(item))
            }
        }
        else -> json
    }

    inline fun <reified T> decodeJson(json: JsonElement): T =
        this.json.decodeFromJsonElement(snakeCaseToCamelCase(json))

    fun <T> decodeJson(serializer: KSerializer<T>, json: JsonElement): T =
        this.json.decodeFromJsonElement(serializer, snakeCaseToCamelCase(json))
}

typealias FollowedQuestion = FollowedQuestionImpl
typealias FollowedTopic = FollowedTopicImpl
