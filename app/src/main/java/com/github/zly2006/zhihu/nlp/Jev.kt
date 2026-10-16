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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

private const val TAG = "Jev"
private val client = HttpClient(Android)

/**
 * 向 TypeSafe Jev 发一组 noul 判断题，返回每题“是”的概率；未配置 Key 或请求失败时返回 null。
 * API Key 在构建时从 local.properties 的 `typesafe.apiKey` 或环境变量 `TYPESAFE_API_KEY` 注入。
 */
private suspend fun askJev(
    state: String,
    instructions: List<String>,
    criteriaTrue: String,
    criteriaFalse: String,
): List<Double>? {
    if (BuildConfig.TYPESAFE_API_KEY.isEmpty() || instructions.isEmpty()) return null
    val body = buildJsonObject {
        // 控制费用：开头部分足够判断主题
        put("state", state.take(3000))
        put("model", "jev-latest")
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
    }
    return runCatching {
        val response = client.post("https://api.typesafe.ai/v1/systemone") {
            bearerAuth(BuildConfig.TYPESAFE_API_KEY)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val responseText = response.bodyAsText()
        check(response.status.isSuccess()) { "HTTP ${response.status.value}: $responseText" }
        val answers = Json.parseToJsonElement(responseText).jsonObject["answers"]!!.jsonObject
        instructions.indices.map { answers["q$it"]!!.jsonObject["noul"]!!.jsonPrimitive.double }
    }.getOrElse {
        Log.e(TAG, "Jev request failed", it)
        null
    }
}

/** AI 屏蔽：每个屏蔽主题一道判断题，返回“内容属于该主题”的概率达到阈值的主题。 */
object JevKeywordSemanticMatcher : KeywordSemanticMatcher {
    override suspend fun checkBlockedPhrases(
        text: String,
        blockedPhrases: List<String>,
        threshold: Double,
    ): List<Pair<String, Double>> {
        if (text.isBlank()) return emptyList()
        val scores = askJev(
            state = text,
            instructions = blockedPhrases.map { "这条知乎内容的主要话题属于「$it」" },
            criteriaTrue = "内容主要在讨论该话题，或该话题是内容的重要组成部分",
            criteriaFalse = "内容与该话题无关，或只是顺带提及",
        ) ?: return emptyList()
        return blockedPhrases
            .zip(scores)
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
    }
}

/**
 * 关键词提取：Jev 不生成文本，所以先用系统 ICU 分词切出候选词，再让 Jev 判断每个候选词能否代表内容主题，
 * 按概率从高到低返回。
 */
object JevKeywordWeightExtractor : KeywordWeightExtractor {
    override suspend fun extractKeywordsWithWeight(text: String, topN: Int): List<KeywordWithWeight> {
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
