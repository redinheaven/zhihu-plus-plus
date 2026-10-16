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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import kotlin.math.roundToInt

/**
 * 按关键词屏蔽的信息流辅助弹窗。
 *
 * 用户可以手动输入，也可以点「Jev 建议」让 Jev 从标题和摘要中挑出关键词再点选（只在点击时联网请求）；按「普通屏蔽词」（可选匹配范围，每个词一条规则）或
 * 「AI 主题」（Jev 语义判断，合成一条规则）添加。它把 Feed 卡片上的一次性“屏蔽”操作转化为可维护的规则。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockByKeywordsDialogContent(
    showDialog: Boolean,
    feedTitle: String,
    feedExcerpt: String?,
    extractedKeywords: List<String>?,
    keywordInfoList: List<KeywordWithWeight>,
    isLoading: Boolean,
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onRequestSuggestions: () -> Unit,
    onConfirmKeywords: (keywords: List<String>, type: KeywordType, scope: KeywordScope) -> Unit,
) {
    val navigator = LocalNavigator.current
    var selectedKeywords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manualInput by remember { mutableStateOf("") }
    var aiMode by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(KeywordScope.ALL) }
    var showDetailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog) {
        if (!showDialog) {
            selectedKeywords = emptySet()
            manualInput = ""
            showDetailDialog = false
        }
    }

    // 普通屏蔽词：手动输入（空格分隔）和点选的关键词各自成为一条；AI 主题：优先用手动输入的一句话，否则把点选的词连成一句
    val keywords = if (aiMode) {
        listOfNotNull(manualInput.trim().ifBlank { selectedKeywords.joinToString(" ").ifBlank { null } })
    } else {
        (manualInput.split(Regex("\\s+")) + selectedKeywords).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("按关键词屏蔽") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !aiMode, onClick = { aiMode = false }, label = { Text("普通屏蔽词") })
                        FilterChip(selected = aiMode, onClick = { aiMode = true }, label = { Text("AI 主题（Jev）") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text(if (aiMode) "一句话描述要屏蔽的主题" else "手动输入屏蔽词") },
                        placeholder = { Text(if (aiMode) "如：减肥方法和体重管理" else "多个词用空格分隔") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = !aiMode,
                    )
                    if (!aiMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("匹配范围", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KeywordScope.entries.forEach { option ->
                                FilterChip(
                                    selected = scope == option,
                                    onClick = { scope = option },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("关键词建议（可点选）", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (extractedKeywords.isNullOrEmpty()) {
                            TextButton(onClick = onRequestSuggestions, enabled = !isLoading) { Text("Jev 建议") }
                        }
                    }
                    when {
                        isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Jev 正在挑选关键词...", style = MaterialTheme.typography.bodySmall)
                        }
                        extractedKeywords == null -> Text(
                            "点「Jev 建议」会把标题和摘要发给 TypeSafe，由 Jev 挑出关键词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        extractedKeywords.isEmpty() -> Text(
                            "未能提取到关键词，可直接手动输入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            extractedKeywords.forEach { keyword ->
                                val isSelected = keyword in selectedKeywords
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedKeywords = if (isSelected) selectedKeywords - keyword else selectedKeywords + keyword
                                    },
                                    label = { Text(keyword) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = if (isSelected) "已选中" else "添加",
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (aiMode) {
                            "AI 主题由 Jev 联网判断内容是否属于该主题，用词不同也能屏蔽；描述越具体越准"
                        } else {
                            "普通屏蔽词按文字匹配，每个词单独成为一条规则"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        onDismiss()
                        navigator.onNavigate(Account.RecommendSettings.Blocklist)
                    }) { Text("管理全部屏蔽规则") }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { showDetailDialog = true },
                        enabled = keywordInfoList.isNotEmpty() && !isLoading,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "详细信息",
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text("详细信息")
                    }

                    Button(
                        onClick = { onConfirmKeywords(keywords, if (aiMode) KeywordType.NLP_SEMANTIC else KeywordType.EXACT_MATCH, scope) },
                        enabled = keywords.isNotEmpty() && !isAdding,
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (aiMode) "屏蔽" else "屏蔽 (${keywords.size})")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isAdding,
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showDetailDialog) {
        KeywordDetailDialog(
            keywordInfoList = keywordInfoList,
            feedTitle = feedTitle,
            feedExcerpt = feedExcerpt,
            onDismiss = { showDetailDialog = false },
        )
    }
}

@Composable
fun KeywordDetailDialog(
    keywordInfoList: List<KeywordWithWeight>,
    feedTitle: String,
    feedExcerpt: String?,
    onDismiss: () -> Unit,
) {
    val maxWeight = keywordInfoList.maxOfOrNull { it.weight } ?: 1.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关键词详细信息") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            "内容预览",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            feedTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!feedExcerpt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                feedExcerpt.take(100) + if (feedExcerpt.length > 100) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "提取的关键词（按重要性排序）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                keywordInfoList.forEachIndexed { index, keywordInfo ->
                    KeywordInfoItem(
                        index = index + 1,
                        keywordInfo = keywordInfo,
                        maxWeight = maxWeight,
                    )
                    if (index < keywordInfoList.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "说明：数值是 Jev 判断该词属于内容具体话题的概率，越高越能代表这条内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
fun KeywordInfoItem(
    index: Int,
    keywordInfo: KeywordWithWeight,
    maxWeight: Double,
) {
    val normalizedWeight = if (maxWeight > 0) (keywordInfo.weight / maxWeight).toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 8.dp),
                    )
                    Text(
                        keywordInfo.keyword,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    formatKeywordWeight(keywordInfo.weight),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { normalizedWeight },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private fun formatKeywordWeight(weight: Double): String {
    val scaled = (weight * 10_000).roundToInt()
    val sign = if (scaled < 0) "-" else ""
    val absolute = kotlin.math.abs(scaled)
    val integer = absolute / 10_000
    val fraction = (absolute % 10_000).toString().padStart(4, '0')
    return "$sign$integer.$fraction"
}
