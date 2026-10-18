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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZhihuAndroidAppIdentityTest {
    @Test
    fun userAgentUsesRealDeviceFieldsAndUnifiedVersion() {
        assertEquals(
            "com.zhihu.android/Futureve/11.4.0 Mozilla/5.0 (Linux; Android 14; 小米 14 Pro " +
                "Build/UKQ1.230804.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/57.0.1000.10 Mobile Safari/537.36",
            ZhihuAndroidAppIdentity.userAgent(DEVICE),
        )
    }

    @Test
    fun xAppZaEncodesValuesAndOmitsInstaller() {
        val appZa = ZhihuAndroidAppIdentity.xAppZa(DEVICE)
        assertEquals(
            "OS=Android&Release=14&Model=%E5%B0%8F%E7%B1%B3+14+Pro&VersionName=11.4.0&VersionCode=40408&" +
                "Product=com.zhihu.android&Width=1200&Height=2600&DeviceType=AndroidPhone&Brand=%E5%B0%8F%E7%B1%B3",
            appZa,
        )
        assertTrue("Installer" !in appZa)
    }

    private companion object {
        val DEVICE = ZhihuPhoneLoginDeviceInfo(
            timezoneOffsetSeconds = 28_800,
            appInstallTimeMillis = 0,
            notificationEnabled = true,
            bluetoothAvailable = true,
            phoneBrand = "小米",
            phoneModel = "小米 14 Pro",
            androidRelease = "14",
            buildId = "UKQ1.230804.001",
            screenWidthPx = 1200,
            screenHeightPx = 2600,
            cpuType = "arm64-v8a",
            cpuCount = 8,
            cpuUsage = "0.0",
            totalMemoryMegabytes = 0,
            freeMemoryMegabytes = 0,
            totalStorageMegabytes = 0,
            freeStorageMegabytes = 0,
        )
    }
}
