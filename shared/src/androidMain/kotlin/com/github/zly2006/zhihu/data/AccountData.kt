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

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAndroidAppIdentity
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.account.currentAndroidApplicationContext
import com.github.zly2006.zhihu.account.currentAndroidZhihuAccountStore
import com.github.zly2006.zhihu.account.zhihuAndroidDeviceInfo
import com.github.zly2006.zhihu.account.zhihuNetworkTypeName
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@SuppressLint("StaticFieldLeak")
object AccountData {
    val json = ZhihuJson.json

    /**
     * 安卓接口（feed / 通知等）的出站头：版本族与 UA 统一走 [ZhihuAndroidAppIdentity]，
     * 设备相关字段取真实值，udid 有则带（见 ZhihuAccountStore.ensureMobileUdid）。
     */
    internal fun androidHeaders(context: Context): Map<String, String> = buildMap {
        val device = context.zhihuAndroidDeviceInfo()
        put("x-api-version", ZhihuAndroidAppIdentity.API_VERSION)
        put("x-app-version", ZhihuAndroidAppIdentity.VERSION_NAME)
        put("x-app-build", ZhihuAndroidAppIdentity.BUILD)
        put("x-app-bundleid", ZhihuAndroidAppIdentity.BUNDLE_ID)
        put("x-app-flavor", ZhihuAndroidAppIdentity.FLAVOR)
        put("x-app-za", ZhihuAndroidAppIdentity.xAppZa(device))
        put("x-network-type", zhihuNetworkTypeName())
        put("x-zse-93", ZhihuAndroidAppIdentity.ZSE93)
        data.mobileUdid?.takeIf { it.isNotBlank() }?.let { put("x-udid", it) }
    }

    val ANDROID_USER_AGENT: String
        get() = ZhihuAndroidAppIdentity.userAgent(currentAndroidApplicationContext().zhihuAndroidDeviceInfo())

    @Serializable
    data class Data(
        val login: Boolean = false,
        val username: String = "",
        val cookies: MutableMap<String, String> = mutableMapOf(),
        val userAgent: String = DEFAULT_ZHIHU_USER_AGENT,
        val self: Person? = null,
        val mobileAccessToken: String? = null,
        val mobileRefreshToken: String? = null,
        val mobileTokenType: String? = null,
        val mobileTokenExpiresAt: Long? = null,
        val mobileUdid: String? = null,
    )

    fun loadData(context: Context): Data {
        androidZhihuAccountStore(context)
        return data
    }

    val data: Data
        get() = currentAndroidZhihuAccountStore().session.toAndroidData()

    fun saveData(context: Context, data: Data) {
        androidZhihuAccountStore(context).save(data.toSession())
    }

    private fun Data.toSession(): ZhihuAccountSession = ZhihuAccountSession(
        login = login,
        username = username,
        cookies = cookies.toMutableMap(),
        userAgent = userAgent,
        profile = self?.let {
            ZhihuAccountProfileSnapshot(
                id = it.id,
                name = it.name,
                urlToken = it.urlToken,
                userType = it.userType,
                avatarUrl = it.avatarUrl,
            )
        },
        self = self?.let { json.encodeToJsonElement(it) },
        mobileAccessToken = mobileAccessToken,
        mobileRefreshToken = mobileRefreshToken,
        mobileTokenType = mobileTokenType,
        mobileTokenExpiresAt = mobileTokenExpiresAt,
        mobileUdid = mobileUdid,
    )

    private fun ZhihuAccountSession.toAndroidData(): Data = Data(
        login = login,
        username = username,
        cookies = cookies.toMutableMap(),
        userAgent = userAgent,
        self = self?.let {
            runCatching {
                ZhihuJson.decodeJson<Person>(it)
            }.getOrNull()
        },
        mobileAccessToken = mobileAccessToken,
        mobileRefreshToken = mobileRefreshToken,
        mobileTokenType = mobileTokenType,
        mobileTokenExpiresAt = mobileTokenExpiresAt,
        mobileUdid = mobileUdid,
    )

    /**
     * 将snake_case的JSON转换为camelCase并解析为对象
     */
    internal inline fun <reified T> decodeJson(json: JsonElement): T {
        val convertedJson = ZhihuJson.snakeCaseToCamelCase(json)
        try {
            return this.json.decodeFromJsonElement<T>(convertedJson)
        } catch (e: SerializationException) {
            Log.e("AccountData", "Failed to parse JSON: $convertedJson", e)
            throw SerializationException("Failed to parse JSON: ${e.message}\n\n$convertedJson", e)
        }
    }

    class ZhPlusJsonSerializationException(
        val originalJson: JsonElement,
        message: String,
        cause: Throwable?,
    ) : SerializationException(message, cause)

    internal fun <T> decodeJson(serializer: KSerializer<T>, json: JsonElement): T {
        val convertedJson = ZhihuJson.snakeCaseToCamelCase(json)
        try {
            return this.json.decodeFromJsonElement(serializer, convertedJson)
        } catch (e: SerializationException) {
            throw ZhPlusJsonSerializationException(convertedJson, "Failed to parse JSON: ${e.message}", e)
        }
    }
}
