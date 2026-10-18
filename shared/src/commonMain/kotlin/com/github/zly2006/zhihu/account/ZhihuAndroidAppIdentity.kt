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

package com.github.zly2006.zhihu.account

import io.ktor.http.encodeURLParameter

/**
 * 知乎 Android 客户端的出站身份：feed / 身份切换 / 手机号登录共用同一份版本族，
 * 设备字段一律取真实值（见 [phoneLoginDeviceInfo]），无法获得的字段（如安装渠道）宁可省略也不编造。
 *
 * 三个客户端曾经各自维护一套写死的版本号（10.61.0 / 11.2.0 / 11.4.0）和模拟器型号，
 * 统一到这里后，同一账号在所有接口上是同一台设备、同一个版本。
 */
object ZhihuAndroidAppIdentity {
    const val VERSION_NAME = "11.4.0"
    const val VERSION_CODE = "40408"
    const val BUILD = "release"
    const val BUNDLE_ID = "com.zhihu.android"
    const val FLAVOR = "zhihuwap64"
    const val API_VERSION = "3.0.93"
    const val ZSE93 = "101_1_1.0"

    fun userAgent(device: ZhihuPhoneLoginDeviceInfo): String =
        "com.zhihu.android/Futureve/$VERSION_NAME Mozilla/5.0 (Linux; Android ${device.androidRelease}; " +
            "${device.phoneModel} Build/${device.buildId}; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/57.0.1000.10 Mobile Safari/537.36"

    fun xAppZa(device: ZhihuPhoneLoginDeviceInfo): String =
        listOf(
            "OS=Android",
            "Release=${device.androidRelease.encoded()}",
            "Model=${device.phoneModel.encoded()}",
            "VersionName=$VERSION_NAME",
            "VersionCode=$VERSION_CODE",
            "Product=$BUNDLE_ID",
            "Width=${device.screenWidthPx}",
            "Height=${device.screenHeightPx}",
            "DeviceType=AndroidPhone",
            "Brand=${device.phoneBrand.encoded()}",
        ).joinToString("&")

    private fun String.encoded(): String = encodeURLParameter(spaceToPlus = true)
}

/**
 * 当前网络类型（x-network-type 头）：WiFi / 4G / Other。
 */
expect fun zhihuNetworkTypeName(): String
