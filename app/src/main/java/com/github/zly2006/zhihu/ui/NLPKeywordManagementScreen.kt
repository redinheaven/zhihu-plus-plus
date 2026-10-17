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

package com.github.zly2006.zhihu.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.nlp.JevKeys
import com.github.zly2006.zhihu.nlp.PREF_CF_ACCOUNT_ID
import com.github.zly2006.zhihu.nlp.PREF_CF_API_TOKEN
import com.github.zly2006.zhihu.nlp.PREF_TYPESAFE_API_KEY
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.MatchedKeywordInfo
import com.github.zly2006.zhihu.viewmodel.filter.contentFilterSettings
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val NLP_KEYWORD_MANAGEMENT_TAG = "NLPKeywordManagement"

/**
 * AI 屏蔽主题管理界面，判断由 Jev 完成
 */
@Composable
fun NLPKeywordManagementScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember(context) { getContentFilterDatabase(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("屏蔽主题", "被屏蔽记录")

    var blockedKeywords by remember { mutableStateOf<List<BlockedKeyword>>(emptyList()) }
    var blockedRecords by remember { mutableStateOf<List<BlockedContentRecord>>(emptyList()) }
    var similarityThreshold by remember {
        mutableFloatStateOf(context.contentFilterSettings().nlpSimilarityThreshold.toFloat())
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var keywordToEdit by remember { mutableStateOf<BlockedKeyword?>(null) }
    var phraseInput by remember { mutableStateOf("") }

    // 加载已屏蔽的关键词和记录
    fun loadData() {
        coroutineScope.launch {
            try {
                blockedKeywords = withContext(Dispatchers.IO) {
                    database
                        .blockedKeywordDao()
                        .getAllKeywords()
                        .filter { it.getKeywordTypeEnum() == KeywordType.NLP_SEMANTIC }
                }
                blockedRecords = withContext(Dispatchers.IO) {
                    database.blockedContentRecordDao().getRecentBlockedRecords(100)
                }
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Load NLP keyword data failed", e)
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold { scaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(scaffoldPadding)
                .padding(innerPadding)
                .fillMaxWidth(),
        ) {
            // 标签页
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> NLPPhraseManagementTab(
                    blockedKeywords = blockedKeywords,
                    similarityThreshold = similarityThreshold,
                    onSimilarityThresholdChange = {
                        similarityThreshold = it
                        androidSettingsStore(context).putFloat("nlpSimilarityThreshold", it)
                    },
                    onShowAddDialog = { showAddDialog = true },
                    onEditKeyword = { keyword ->
                        keywordToEdit = keyword
                        phraseInput = keyword.keyword
                        showEditDialog = true
                    },
                    onDeleteKeyword = { keyword ->
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    database.blockedKeywordDao().deleteKeyword(keyword)
                                }
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Exception) {
                                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Delete NLP keyword failed", e)
                                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onClearAll = {
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    blockedKeywords.forEach { database.blockedKeywordDao().deleteKeyword(it) }
                                }
                                Toast.makeText(context, "已清空所有屏蔽主题", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Exception) {
                                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Clear NLP keywords failed", e)
                                Toast.makeText(context, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                1 -> BlockedRecordsTab(
                    records = blockedRecords,
                    onDeleteRecord = { record ->
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    database.blockedContentRecordDao().deleteRecord(record.id)
                                }
                                Toast.makeText(context, "已删除记录", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Exception) {
                                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Delete blocked NLP record failed", e)
                                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onClearAll = {
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    database.blockedContentRecordDao().clearAllRecords()
                                }
                                Toast.makeText(context, "已清空所有记录", Toast.LENGTH_SHORT).show()
                                loadData()
                            } catch (e: Exception) {
                                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Clear blocked NLP records failed", e)
                                Toast.makeText(context, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }

    // 手动添加短语对话框
    if (showAddDialog) {
        AddPhraseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phrase ->
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.blockedKeywordDao().insertKeyword(
                                BlockedKeyword(
                                    keyword = phrase.trim(),
                                    keywordType = KeywordType.NLP_SEMANTIC.name,
                                ),
                            )
                        }
                        Toast.makeText(context, "已添加主题", Toast.LENGTH_SHORT).show()
                        loadData()
                        showAddDialog = false
                    } catch (e: Exception) {
                        Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Add NLP phrase failed", e)
                        Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    // 编辑短语对话框
    if (showEditDialog && keywordToEdit != null) {
        EditPhraseDialog(
            initialPhrase = phraseInput,
            onDismiss = {
                showEditDialog = false
                keywordToEdit = null
            },
            onConfirm = { newPhrase ->
                coroutineScope.launch {
                    try {
                        val updated = keywordToEdit!!.copy(keyword = newPhrase.trim())
                        withContext(Dispatchers.IO) {
                            database.blockedKeywordDao().insertKeyword(updated)
                        }
                        Toast.makeText(context, "已更新主题", Toast.LENGTH_SHORT).show()
                        loadData()
                        showEditDialog = false
                        keywordToEdit = null
                    } catch (e: Exception) {
                        Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Update NLP phrase failed", e)
                        Toast.makeText(context, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@Composable
fun NLPPhraseManagementTab(
    blockedKeywords: List<BlockedKeyword>,
    similarityThreshold: Float,
    onSimilarityThresholdChange: (Float) -> Unit,
    onShowAddDialog: () -> Unit,
    onEditKeyword: (BlockedKeyword) -> Unit,
    onDeleteKeyword: (BlockedKeyword) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 说明卡片
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "AI 智能屏蔽（Jev）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "每条内容会联网交给 TypeSafe Jev 模型判断是否属于你设置的主题（Jev 不可用时改用 Cloudflare 的 clef-flash）。主题用一句话描述效果最好，例如：\"减肥方法和体重管理\"。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        item { JevApiKeyCard() }

        // 判定阈值设置
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "判定阈值: ${String.format("%.2f", similarityThreshold)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = similarityThreshold,
                        onValueChange = onSimilarityThresholdChange,
                        valueRange = 0.5f..0.95f,
                        steps = 8,
                    )
                    Text(
                        "模型认为内容属于该主题的概率超过阈值时屏蔽。阈值越高越不容易误杀，越低越不容易漏掉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 已屏蔽的短语列表
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "屏蔽主题 (${blockedKeywords.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            TextButton(onClick = onShowAddDialog) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("手动添加")
                            }
                            if (blockedKeywords.isNotEmpty()) {
                                TextButton(onClick = onClearAll) {
                                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (blockedKeywords.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "暂无屏蔽主题",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击上方「手动添加」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(blockedKeywords) { keyword ->
                ListItem(
                    headlineContent = {
                        Text(
                            keyword.keyword,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                "添加于 ${
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(keyword.createdTime))
                                }",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEditKeyword(keyword) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { onDeleteKeyword(keyword) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun BlockedRecordsTab(
    records: List<BlockedContentRecord>,
    onDeleteRecord: (BlockedContentRecord) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "最近100条被屏蔽记录",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (records.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("清空全部", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (records.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "暂无屏蔽记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(records) { record ->
                BlockedRecordItem(
                    record = record,
                    onDelete = { onDeleteRecord(record) },
                )
            }
        }
    }
}

@Composable
fun BlockedRecordItem(
    record: BlockedContentRecord,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val matchedKeywords = remember(record.matchedKeywords) {
        runCatching {
            Json.decodeFromString(
                ListSerializer(MatchedKeywordInfo.serializer()),
                record.matchedKeywords,
            )
        }.getOrElse { e ->
            Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Failed to parse matched keywords", e)
            emptyList()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${record.contentType} · ${record.authorName ?: "未知作者"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        java.text
                            .SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(record.blockedTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 显示摘要
                if (!record.excerpt.isNullOrBlank()) {
                    Text(
                        "摘要",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            record.excerpt!!,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 显示匹配的关键词
                Text(
                    "命中的主题（前3个）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                matchedKeywords.forEach { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SelectionContainer {
                            Text(
                                match.keyword,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            String.format("%.2f%%", match.similarity * 100),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除记录")
                }
            }
        }
    }
}

@Composable
fun AddPhraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加屏蔽主题") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("主题") },
                    placeholder = { Text("一句话描述，如：减肥方法和体重管理") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "提示：描述越具体，判断越准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phrase.isNotBlank()) {
                        onConfirm(phrase)
                    }
                },
                enabled = phrase.isNotBlank(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun EditPhraseDialog(
    initialPhrase: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phrase by remember { mutableStateOf(initialPhrase) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑屏蔽主题") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("主题") },
                    placeholder = { Text("一句话描述要屏蔽的主题") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "提示：描述越具体，判断越准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phrase.isNotBlank()) {
                        onConfirm(phrase)
                    }
                },
                enabled = phrase.isNotBlank(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** AI 屏蔽需要用户自己申请的 Key，只存在本机设置里；留空时用安装包构建时内置的 Key（如有） */
@Composable
private fun JevApiKeyCard() {
    val context = LocalContext.current
    val settings = remember(context) { androidSettingsStore(context) }
    var typesafe by remember { mutableStateOf(settings.getString(PREF_TYPESAFE_API_KEY, "")) }
    var cfAccountId by remember { mutableStateOf(settings.getString(PREF_CF_ACCOUNT_ID, "")) }
    var cfApiToken by remember { mutableStateOf(settings.getString(PREF_CF_API_TOKEN, "")) }
    var configured by remember { mutableStateOf(JevKeys(settings).isConfigured) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("API Key", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !configured -> "未填写，AI 屏蔽和「Jev 建议」不会生效"
                    typesafe.isBlank() && cfApiToken.isBlank() -> "正在使用安装包内置的 Key；在下面填写后以你填的为准"
                    else -> "已配置，AI 屏蔽和「Jev 建议」可以使用"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                "AI 屏蔽需要自己申请 Key，二选一即可：\n" +
                    "· TypeSafe：到 typesafe.ai 注册后获取 API Key\n" +
                    "· Cloudflare：到 dash.cloudflare.com 注册，复制账号 ID，再创建一个有 Workers AI 权限的 API Token\n" +
                    "两个都填时优先用 TypeSafe，3 秒没响应改用 Cloudflare。Key 只保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = typesafe,
                onValueChange = { typesafe = it },
                label = { Text("TypeSafe API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cfAccountId,
                onValueChange = { cfAccountId = it },
                label = { Text("Cloudflare 账号 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cfApiToken,
                onValueChange = { cfApiToken = it },
                label = { Text("Cloudflare API Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    settings.putString(PREF_TYPESAFE_API_KEY, typesafe.trim())
                    settings.putString(PREF_CF_ACCOUNT_ID, cfAccountId.trim())
                    settings.putString(PREF_CF_API_TOKEN, cfApiToken.trim())
                    configured = JevKeys(settings).isConfigured
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("保存") }
        }
    }
}
