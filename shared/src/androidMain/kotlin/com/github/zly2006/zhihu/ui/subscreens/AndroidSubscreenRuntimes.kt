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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.rememberArticleTtsState
import com.github.zly2006.zhihu.util.PowerSaveModeCompat
import com.github.zly2006.zhihu.viewmodel.filter.OfflineContent
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.local.getLocalContentDatabase
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.zly2006.zhihu.notification.PREF_NAME as NOTIFICATION_PREF_NAME

@Composable
actual fun rememberDeveloperInfo(): DeveloperInfoSnapshot {
    val context = LocalContext.current
    val ttsState = rememberArticleTtsState()
    return produceState(initialValue = DeveloperInfoSnapshot(), context, ttsState) {
        while (true) {
            val readingState = AndroidReadingPlayerBridge.state.value
            value = DeveloperInfoSnapshot(
                networkStatus = context.networkStatusText(),
                powerSaveModeText =
                    when (PowerSaveModeCompat.getPowerSaveMode(context)) {
                        PowerSaveModeCompat.POWER_SAVE -> "省电模式：已开启"
                        PowerSaveModeCompat.HUAWEI_POWER_SAVE -> "省电模式：华为傻逼模式已开启"
                        else -> null
                    },
                ttsState = ttsState,
                currentTtsEngineLabel = readingState.engineLabel.ifBlank { "按需初始化" },
                availableTtsEngineLabels = readingState.availableEngineLabels,
            )
            delay(1_000L)
        }
    }.value
}

private fun Context.networkStatusText(): String {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivityManager.activeNetwork
    return buildString {
        append("网络状态：")
        if (activeNetwork != null) {
            append("已连接")
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> append(" (移动数据)")
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> append(" (Wi-Fi)")
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> append(" (VPN)")
            }
        } else {
            append("未连接")
        }
    }
}

actual val isWebViewCustomFontSupported: Boolean = true

@Composable
actual fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val userMessages = androidUserMessageSink(context)
    val fontFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        val destFile = File(context.filesDir, "custom_font")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        onCustomFontNameChange(name)
        userMessages.showShortMessage("字体已设置，重新打开文章后生效")
    }
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    fontFilePicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Text("选择", modifier = Modifier.padding(start = 4.dp))
            }
            if (customFontName != null) {
                OutlinedButton(
                    onClick = {
                        File(context.filesDir, "custom_font").delete()
                        onCustomFontNameChange(null)
                        userMessages.showShortMessage("已清除自定义字体")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Text("清除", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
actual fun rememberOpenSourceLicensesLibraries(): Libs {
    val context = LocalContext.current
    return remember(context) {
        Libs
            .Builder()
            .withContext(context)
            .build()
    }
}

private const val CLOUD_SYNC_FILE_URI_KEY = "cloudSyncFileUri"
private const val CLOUD_SYNC_LAST_SUCCESS_KEY = "cloudSyncLastSuccessTime"

/** 同步文件里的设置来自这些 SharedPreferences；同步功能自身的状态不写进文件，恢复时也不覆盖。 */
private val cloudSyncPreferenceFiles = listOf(PREFERENCE_NAME, NOTIFICATION_PREF_NAME)
private val cloudSyncExcludedKeys = setOf(CLOUD_SYNC_FILE_URI_KEY, CLOUD_SYNC_LAST_SUCCESS_KEY, CLOUD_SYNC_LAST_ERROR_KEY)
private val cloudSyncFiles = listOf("account.json", "history.json")

/**
 * 不进同步文件的表：离线正文缓存体积大且 7 天过期；本地推荐的爬虫任务、结果和推荐列表是可重新生成的缓存，且靠自增 id 互相引用无法逐行合并
 * （本地推荐学到的偏好在 user_behaviors，照常同步）；room_master_table 是 Room 的结构校验表。
 */
private val cloudSyncExcludedTables = setOf(
    OfflineContent.TABLE_NAME,
    "crawling_tasks",
    "crawling_results",
    "local_feeds",
    "room_master_table",
    "android_metadata",
)

actual val isCloudFileSyncSupported: Boolean = true

/** 同步到已选的文件并把结果（成功时间或失败原因）记进设置，设置页按钮和启动时的自动同步共用。需在 IO 线程调用。 */
fun syncCloudFile(context: Context): Result<Unit> {
    val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    val result = runCatching {
        writeCloudSyncFile(context, preferences.getString(CLOUD_SYNC_FILE_URI_KEY, null)?.toUri() ?: error("未选择同步文件"))
    }.onFailure { Log.e("CloudFileSync", "同步失败", it) }
    preferences.edit {
        result
            .onSuccess {
                putLong(CLOUD_SYNC_LAST_SUCCESS_KEY, System.currentTimeMillis())
                remove(CLOUD_SYNC_LAST_ERROR_KEY)
            }.onFailure { putString(CLOUD_SYNC_LAST_ERROR_KEY, it.message ?: it.javaClass.simpleName) }
    }
    return result
}

/** 打开应用时调用：已选同步文件且距上次成功同步超过一天才同步，否则返回 null。需在 IO 线程调用。 */
fun syncCloudFileIfDue(context: Context): Result<Unit>? {
    val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    val due = preferences.contains(CLOUD_SYNC_FILE_URI_KEY) &&
        System.currentTimeMillis() - preferences.getLong(CLOUD_SYNC_LAST_SUCCESS_KEY, 0L) > 24 * 60 * 60 * 1000L
    return if (due) syncCloudFile(context) else null
}

@Composable
actual fun CloudFileSyncSettings() {
    val context = LocalContext.current
    val userMessages = androidUserMessageSink(context)
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE) }
    var fileUri by remember { mutableStateOf(preferences.getString(CLOUD_SYNC_FILE_URI_KEY, null)?.toUri()) }
    var lastSuccessTime by remember { mutableLongStateOf(preferences.getLong(CLOUD_SYNC_LAST_SUCCESS_KEY, 0L)) }
    var lastError by remember { mutableStateOf(preferences.getString(CLOUD_SYNC_LAST_ERROR_KEY, null)) }
    var busy by remember { mutableStateOf(false) }
    val fileName = remember(fileUri) {
        fileUri?.let { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: uri.toString()
        }
    }

    fun useFile(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        fileUri = uri
        preferences.edit { putString(CLOUD_SYNC_FILE_URI_KEY, uri.toString()) }
    }

    fun sync() {
        busy = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { syncCloudFile(context) }
            busy = false
            lastSuccessTime = preferences.getLong(CLOUD_SYNC_LAST_SUCCESS_KEY, 0L)
            lastError = preferences.getString(CLOUD_SYNC_LAST_ERROR_KEY, null)
            userMessages.showShortMessage(if (result.isSuccess) "同步成功" else "同步失败：$lastError")
        }
    }

    val chooseFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            useFile(uri)
            sync()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        useFile(uri)
        busy = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { restoreCloudSyncFile(context, uri) } }
            busy = false
            result
                .onSuccess {
                    // 登录会话、主题等状态在内存里还是旧的，直接重启进程让所有数据从磁盘重新加载
                    userMessages.showMessage("已恢复 $it，正在重启应用")
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                    Runtime.getRuntime().exit(0)
                }.onFailure {
                    Log.e("CloudFileSync", "恢复失败", it)
                    userMessages.showMessage("恢复失败：${it.message ?: it.javaClass.simpleName}")
                }
        }
    }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从同步文件恢复") },
            text = { Text("文件里的数据会合并进本机，本机已有的数据不会删除；同一项设置、登录信息等两边都有时以文件为准。恢复后应用会自动重启。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("选择文件") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") } },
        )
    }

    SettingItem(
        title = { Text("同步文件") },
        description = { Text(fileName ?: "未选择，点按选择保存位置") },
        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
        onClick = { chooseFileLauncher.launch("zhihupp_sync.json") },
        enabled = !busy,
    )
    SettingItem(
        title = { Text("最近同步") },
        description = {
            Column {
                Text(
                    if (lastSuccessTime > 0) {
                        "成功于 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSuccessTime))}"
                    } else {
                        "尚未同步"
                    },
                )
                lastError?.let { Text("上次失败：$it", color = MaterialTheme.colorScheme.error) }
            }
        },
        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
        endAction = {
            Button(
                onClick = { sync() },
                enabled = fileUri != null && !busy,
            ) {
                Text(if (busy) "同步中…" else "立刻同步")
            }
        },
    )
    SettingItem(
        title = { Text("从同步文件恢复") },
        description = { Text("把文件里的数据合并进本机，完成后自动重启") },
        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
        onClick = { showRestoreConfirm = true },
        enabled = !busy,
    )
}

/**
 * 同步文件格式（JSON）：
 * - `preferences`：按 SharedPreferences 文件分组，每项存成 `[类型, 值]` 以便原样恢复；
 * - `files`：[cloudSyncFiles] 的 base64 内容（登录信息、本地历史）；
 * - `databases`：两个 Room 数据库除 [cloudSyncExcludedTables] 外每张表的全部行，按列名存储，恢复时只写入当前表结构里存在的列。
 */
private fun buildLocalSyncContent(context: Context): JsonObject = buildJsonObject {
    put("version", 2)
    put("exportTime", System.currentTimeMillis())
    putJsonObject("preferences") {
        cloudSyncPreferenceFiles.forEach { name ->
            putJsonObject(name) {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                    if (key in cloudSyncExcludedKeys) return@forEach
                    val (type, element) = when (value) {
                        is Boolean -> "boolean" to JsonPrimitive(value)
                        is Int -> "int" to JsonPrimitive(value)
                        is Long -> "long" to JsonPrimitive(value)
                        is Float -> "float" to JsonPrimitive(value)
                        is String -> "string" to JsonPrimitive(value)
                        is Set<*> -> "stringSet" to JsonArray(value.map { JsonPrimitive(it as String) })
                        else -> return@forEach
                    }
                    put(key, JsonArray(listOf(JsonPrimitive(type), element)))
                }
            }
        }
    }
    putJsonObject("files") {
        cloudSyncFiles.forEach { name ->
            val file = File(context.filesDir, name)
            if (file.exists()) put(name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
    }
    putJsonObject("databases") {
        cloudSyncDatabases(context).forEach { (name, database) ->
            val db = database.openHelper.readableDatabase
            putJsonObject(name) {
                db.cloudSyncTables().forEach { table ->
                    putJsonArray(table) {
                        db.query("SELECT * FROM `$table`").use { cursor ->
                            while (cursor.moveToNext()) {
                                add(
                                    buildJsonObject {
                                        for (i in 0 until cursor.columnCount) {
                                            put(
                                                cursor.getColumnName(i),
                                                when (cursor.getType(i)) {
                                                    Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(i))
                                                    Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(i))
                                                    Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cursor.getString(i))
                                                    Cursor.FIELD_TYPE_BLOB -> buildJsonObject {
                                                        put("base64", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP))
                                                    }
                                                    else -> JsonNull
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readCloudSyncFile(context: Context, uri: Uri): JsonObject? {
    val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: error("无法读取同步文件")
    // 解析失败时直接报错，不能把看不懂的旧内容当成空文件覆盖掉
    return text.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }
}

/** 本机数据与文件里已有的数据合并后写回文件；冲突时以本机为准，文件里本机没有的数据全部保留。 */
private fun writeCloudSyncFile(context: Context, uri: Uri) {
    val local = buildLocalSyncContent(context)
    val content = readCloudSyncFile(context, uri)?.let { mergeSyncContent(context, local, it) } ?: local
    // "wt" 才保证截断旧内容；部分 DocumentsProvider 不认 "wt"，退回 "w"（这些实现里 "w" 本身就会截断）
    val output = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: context.contentResolver.openOutputStream(uri, "w")
        ?: error("无法打开同步文件")
    output.use { it.write(content.toString().toByteArray()) }
}

/** 把文件里的数据合并进本机；冲突时以文件为准，本机已有而文件里没有的数据全部保留。返回恢复内容的摘要。 */
private fun restoreCloudSyncFile(context: Context, uri: Uri): String {
    val file = readCloudSyncFile(context, uri) ?: error("同步文件是空的")
    file["databases"] ?: error("不是完整的同步文件（缺少数据库内容）")
    val local = buildLocalSyncContent(context)
    val root = mergeSyncContent(context, file, local)
    var settingCount = 0
    var rowCount = 0
    root["preferences"]?.jsonObject?.forEach { (name, entries) ->
        if (name !in cloudSyncPreferenceFiles) return@forEach
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
            entries.jsonObject.forEach { (key, entry) ->
                if (key in cloudSyncExcludedKeys) return@forEach
                val (type, value) = entry.jsonArray
                when (type.jsonPrimitive.content) {
                    "boolean" -> putBoolean(key, value.jsonPrimitive.boolean)
                    "int" -> putInt(key, value.jsonPrimitive.int)
                    "long" -> putLong(key, value.jsonPrimitive.long)
                    "float" -> putFloat(key, value.jsonPrimitive.float)
                    "string" -> putString(key, value.jsonPrimitive.content)
                    "stringSet" -> putStringSet(key, value.jsonArray.map { it.jsonPrimitive.content }.toSet())
                    else -> return@forEach
                }
                settingCount++
            }
        }
    }
    root["files"]?.jsonObject?.forEach { (name, content) ->
        if (name in cloudSyncFiles) File(context.filesDir, name).writeBytes(Base64.decode(content.jsonPrimitive.content, Base64.NO_WRAP))
    }
    cloudSyncDatabases(context).forEach { (name, database) ->
        val tables = root["databases"]?.jsonObject?.get(name)?.jsonObject ?: return@forEach
        val db = database.openHelper.writableDatabase
        val existingTables = db.cloudSyncTables()
        db.beginTransaction()
        try {
            tables.forEach { (table, rows) ->
                if (table !in existingTables) return@forEach
                val columns = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                db.delete(table, null, null)
                rows.jsonArray.forEach { row ->
                    val values = ContentValues()
                    row.jsonObject.forEach { (column, value) ->
                        if (column !in columns) return@forEach
                        when {
                            value is JsonNull -> values.putNull(column)
                            value is JsonObject -> values.put(column, Base64.decode(value.getValue("base64").jsonPrimitive.content, Base64.NO_WRAP))
                            value.jsonPrimitive.isString -> values.put(column, value.jsonPrimitive.content)
                            else -> value.jsonPrimitive.longOrNull?.let { values.put(column, it) } ?: values.put(column, value.jsonPrimitive.double)
                        }
                    }
                    db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    rowCount++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    val localRowCount = local.section("databases").values.sumOf { db -> db.jsonObject.values.sumOf { it.jsonArray.size } }
    return "$settingCount 项设置、$rowCount 条数据（新增 ${rowCount - localRowCount} 条）"
}

private const val CLOUD_SYNC_HISTORY_FILE = "history.json"
private const val CLOUD_SYNC_HISTORY_LIMIT = 1000

/**
 * 合并两份同步内容，[primary] 在冲突时优先，[secondary] 里独有的数据都会保留：
 * - 设置按键合并；文件取 [primary] 的版本，本地历史按条目取并集（与 HistoryStorage 一样最多 1000 条）；
 * - 有业务主键的表按主键合并；自增 id 的表按除 id 和 createdTime 外的所有列判重，[secondary] 独有的行去掉 id，写入时重新分配。
 */
private fun mergeSyncContent(context: Context, primary: JsonObject, secondary: JsonObject): JsonObject = buildJsonObject {
    put("version", 2)
    put("exportTime", System.currentTimeMillis())
    putJsonObject("preferences") {
        cloudSyncPreferenceFiles.forEach { name ->
            put(name, JsonObject(secondary.section("preferences", name) + primary.section("preferences", name)))
        }
    }
    putJsonObject("files") {
        val primaryFiles = primary.section("files")
        val secondaryFiles = secondary.section("files")
        (secondaryFiles + primaryFiles).forEach { (name, content) -> put(name, content) }
        val primaryHistory = primaryFiles[CLOUD_SYNC_HISTORY_FILE]
        val secondaryHistory = secondaryFiles[CLOUD_SYNC_HISTORY_FILE]
        if (primaryHistory != null && secondaryHistory != null) {
            val (primaryItems, secondaryItems) = listOf(primaryHistory, secondaryHistory).map {
                Json.parseToJsonElement(String(Base64.decode(it.jsonPrimitive.content, Base64.NO_WRAP))).jsonArray
            }
            val merged = (secondaryItems.filter { it !in primaryItems } + primaryItems).takeLast(CLOUD_SYNC_HISTORY_LIMIT)
            put(CLOUD_SYNC_HISTORY_FILE, Base64.encodeToString(JsonArray(merged).toString().toByteArray(), Base64.NO_WRAP))
        }
    }
    putJsonObject("databases") {
        cloudSyncDatabases(context).forEach { (name, database) ->
            val db = database.openHelper.readableDatabase
            putJsonObject(name) {
                db.cloudSyncTables().forEach { table ->
                    val primaryRows = primary.rows(name, table)
                    val secondaryRows = secondary.rows(name, table)
                    val keyColumns = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0) {
                                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")) to cursor.getString(cursor.getColumnIndexOrThrow("type")))
                                }
                            }
                        }
                    }
                    val autoId = keyColumns.singleOrNull()?.takeIf { it.second.equals("INTEGER", ignoreCase = true) }?.first
                    val merged = when {
                        autoId != null -> {
                            val known = primaryRows.mapTo(HashSet()) { it - autoId - "createdTime" }
                            primaryRows + secondaryRows.filter { (it - autoId - "createdTime") !in known }.map { JsonObject(it - autoId) }
                        }
                        else -> {
                            val known = primaryRows.mapTo(HashSet()) { row -> keyColumns.map { row[it.first] } }
                            primaryRows + secondaryRows.filter { row -> keyColumns.map { row[it.first] } !in known }
                        }
                    }
                    put(table, JsonArray(merged))
                }
            }
        }
    }
}

private fun JsonObject.section(vararg path: String): Map<String, JsonElement> =
    path.fold(this) { obj, key -> obj[key] as? JsonObject ?: JsonObject(emptyMap()) }

private fun JsonObject.rows(database: String, table: String): List<JsonObject> =
    (section("databases", database)[table] as? JsonArray).orEmpty().map { it.jsonObject }

private fun cloudSyncDatabases(context: Context): Map<String, RoomDatabase> = mapOf(
    "content_filter_database" to getContentFilterDatabase(context),
    "local_content_database" to getLocalContentDatabase(context),
)

private fun SupportSQLiteDatabase.cloudSyncTables(): List<String> =
    query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    } - cloudSyncExcludedTables
