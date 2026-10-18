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

package com.github.zly2006.zhihu.nlp

import android.icu.text.BreakIterator
import android.util.Log
import com.github.zly2006.zhihu.BuildConfig
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

private const val TAG = "Jev"
private val client = HttpClient(Android)

/** 控制费用：开头部分足够判断主题；clef-flash 实测也只读开头约 2k token（约 3000 个汉字） */
private const val JEV_STATE_MAX_CHARS = 3000

const val PREF_TYPESAFE_API_KEY = "typesafeApiKey"
const val PREF_CF_ACCOUNT_ID = "cloudflareAccountId"
const val PREF_CF_API_TOKEN = "cloudflareApiToken"
const val JEV_MISSING_KEY_MESSAGE = "未填写 AI 屏蔽的 API Key，请到「账号 → 屏蔽列表 → AI 屏蔽主题」填写"

/** AI 屏蔽用的 Key：用户在设置里填的优先，没填时用构建时从 local.properties 注入的（作者自用包） */
class JevKeys(
    settings: SettingsStore,
) {
    val typesafe = settings.getString(PREF_TYPESAFE_API_KEY, "").trim().ifEmpty { BuildConfig.TYPESAFE_API_KEY }
    val cfAccountId = settings.getString(PREF_CF_ACCOUNT_ID, "").trim().ifEmpty { BuildConfig.CF_ACCOUNT_ID }
    val cfApiToken = settings.getString(PREF_CF_API_TOKEN, "").trim().ifEmpty { BuildConfig.CF_API_TOKEN }
    val isConfigured get() = typesafe.isNotEmpty() || (cfAccountId.isNotEmpty() && cfApiToken.isNotEmpty())
}

/**
 * 发一组 noul 判断题，返回每题“是”的概率；两个服务都不可用时返回 null。
 * 先问 TypeSafe Jev；Jev 未配置 Key、请求失败（额度用完、服务故障、网络问题）或 3 秒内没有响应时，改问 Cloudflare Workers AI 的 clef-flash。
 * 两者题型和返回格式相同，只是 Cloudflare 的返回外面多包一层 `result`。
 * Key 见 [JevKeys]：用户在 AI 屏蔽主题页填写，或构建时从 local.properties（`typesafe.apiKey`、`cloudflare.accountId`、`cloudflare.apiToken`）注入。
 * Cloudflare 只走官方 API：`*.workers.dev` 在中国大陆被 DNS 污染、无法连接。
 */
private suspend fun askJev(
    keys: JevKeys,
    state: String,
    instructions: List<String>,
    criteriaTrue: String,
    criteriaFalse: String,
): List<Double>? {
    if (instructions.isEmpty()) return null

    fun body(model: String) = buildJsonObject {
        put("state", state.take(JEV_STATE_MAX_CHARS))
        put("model", model)
        putJsonObject("questions") {
            instructions.forEachIndexed { i, instruction ->
                putJsonObject("q$i") {
                    put("type", "noul")
                    put("instructions", instruction)
                    putJsonObject("criteria") {
                        put("true", criteriaTrue)
                        put("false", criteriaFalse)
                    }
                }
            }
        }
    }.toString()

    suspend fun post(model: String, url: String, token: String, unwrap: (JsonObject) -> JsonObject): List<Double>? {
        if (token.isEmpty()) return null
        return runCatching {
            val response = client.post(url) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(body(model))
            }
            val responseText = response.bodyAsText()
            check(response.status.isSuccess()) { "HTTP ${response.status.value}: $responseText" }
            val answers = unwrap(Json.parseToJsonElement(responseText).jsonObject)["answers"]!!.jsonObject
            instructions.indices.map { answers["q$it"]!!.jsonObject["noul"]!!.jsonPrimitive.double }
        }.getOrElse {
            Log.e(TAG, "$model request failed", it)
            null
        }
    }

    // Jev 正常约 0.3～0.6 秒；卡住时网络库默认要等很久才失败，3 秒没响应就改问 clef-flash
    return withTimeoutOrNull(3000) { post("jev-latest", "https://api.typesafe.ai/v1/systemone", keys.typesafe) { it } }
        ?: keys.cfAccountId.takeIf { it.isNotEmpty() }?.let { accountId ->
            post(
                "clef-flash",
                "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/@cf/cloudflare/clef-flash",
                keys.cfApiToken,
            ) { it["result"]!!.jsonObject }
        }
}

/** AI 屏蔽：每个屏蔽主题一道判断题，返回“内容属于该主题”的概率达到阈值的主题；没填 Key 时不屏蔽。 */
class JevKeywordSemanticMatcher(
    private val settings: SettingsStore,
) : KeywordSemanticMatcher {
    /**
     * 同一内容在下拉刷新、关注页、离线列表重新过滤时不重复问 Jev：以发给 Jev 的文本为键缓存每个主题的概率，
     * 只问缓存里没有的主题（新加主题时旧主题不重问）。只存本进程内存，最多 500 条内容。
     */
    private val scoreCache = object : LinkedHashMap<String, Map<String, Double>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, Double>>) = size > 500
    }

    override suspend fun checkBlockedPhrases(
        text: String,
        blockedPhrases: List<String>,
        threshold: Double,
    ): List<Pair<String, Double>> {
        val keys = JevKeys(settings)
        if (text.isBlank() || !keys.isConfigured) return emptyList()
        val state = text.take(JEV_STATE_MAX_CHARS)
        val cached = synchronized(scoreCache) { scoreCache[state] }.orEmpty()
        val missing = blockedPhrases.filterNot { it in cached }
        val scores = if (missing.isEmpty()) {
            cached
        } else {
            val fresh = askJev(
                keys = keys,
                state = state,
                instructions = missing.map { "这条知乎内容的主要话题属于「$it」" },
                criteriaTrue = "内容主要在讨论该话题，或该话题是内容的重要组成部分",
                criteriaFalse = "内容与该话题无关，或只是顺带提及",
            )?.let { missing.zip(it).toMap() }.orEmpty()
            (cached + fresh).also { merged -> if (fresh.isNotEmpty()) synchronized(scoreCache) { scoreCache[state] = merged } }
        }
        return blockedPhrases
            .mapNotNull { phrase -> scores[phrase]?.let { phrase to it } }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
    }
}

/**
 * 关键词提取：Jev 不生成文本，所以先用系统 ICU 分词切出候选词，再让 Jev 判断每个候选词能否代表内容主题，
 * 按概率从高到低返回。只在用户点「Jev 建议」时调用，没填 Key 时抛出提示让弹窗告诉用户去哪填。
 */
class JevKeywordWeightExtractor(
    private val settings: SettingsStore,
) : KeywordWeightExtractor {
    override suspend fun extractKeywordsWithWeight(text: String, topN: Int): List<KeywordWithWeight> {
        val keys = JevKeys(settings)
        check(keys.isConfigured) { JEV_MISSING_KEY_MESSAGE }
        val breaker = BreakIterator.getWordInstance(Locale.CHINESE).apply { setText(text) }
        val candidates = generateSequence(breaker.first()) { breaker.next().takeIf { it != BreakIterator.DONE } }
            .zipWithNext { start, end -> text.substring(start, end).trim() }
            .filter { word ->
                word.length >= 2 &&
                    word.any { it.isLetter() } &&
                    !KeywordAnalyzerCore.isStopWord(word) &&
                    !KeywordAnalyzerCore.isNumberOnly(word)
            }.distinct()
            .take(40)
            .toList()
        val scores = askJev(
            keys = keys,
            state = text,
            instructions = candidates.map { "「$it」是这条内容涉及的一个具体话题、事物或领域" },
            criteriaTrue = "该词指代内容中实际讨论的具体话题、事物、概念或领域",
            criteriaFalse = "该词是疑问词、动词、时间词、程度词等泛泛的常用词",
        ) ?: return emptyList()
        return candidates
            .zip(scores) { keyword, score -> KeywordWithWeight(keyword, score) }
            .sortedByDescending { it.weight }
            .take(topN)
    }
}
