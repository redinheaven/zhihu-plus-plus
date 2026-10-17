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

import kotlinx.serialization.Serializable

@Serializable
data class OfficialBadge(
    val title: String,
    val description: String,
    val iconUrl: String = "",
    val nightIconUrl: String = "",
    val url: String = "",
    val type: String = "",
    val detailType: String = "",
) {
    val isGenericCertification: Boolean
        get() = title == "认证"

    val isUsefulInList: Boolean
        get() = !isGenericCertification

    /** 作者页显示为「认证信息」的个人认证（如「认证信息: XX 公司工程师」） */
    val isIdentityCertification: Boolean
        get() = title == "认证" || title == "已认证的个人"
}

/** 本 fork 屏蔽带「认证信息」的作者：首页、问题回答列表和搜索结果都不显示其内容（关注的人除外） */
fun DataHolder.BadgeV2?.hasIdentityCertification(): Boolean = officialBadgeDetails().any { it.isIdentityCertification }

fun DataHolder.BadgeV2?.officialBadge(): OfficialBadge? {
    this ?: return null
    val details = officialBadgeDetails()
    val primary = details.firstOrNull { it.type != "identity" && it.iconUrl.isNotBlank() }
        ?: details.firstOrNull { it.iconUrl.isNotBlank() }
        ?: return null

    return primary
        .copy(
            iconUrl = icon.ifBlank { primary.iconUrl },
            nightIconUrl = nightIcon.ifBlank { primary.nightIconUrl },
        )
}

fun DataHolder.BadgeV2?.officialBadgeDetails(): List<OfficialBadge> {
    this ?: return emptyList()
    val details = detailBadges
        ?.mapNotNull(DataHolder.BadgeV2.Badge::asOfficialBadge)
        .orEmpty()
    if (details.isNotEmpty()) return details
    return mergedBadges
        ?.mapNotNull(DataHolder.BadgeV2.Badge::asOfficialBadge)
        .orEmpty()
}

private fun DataHolder.BadgeV2.Badge.asOfficialBadge(): OfficialBadge? {
    if (badgeStatus != null && badgeStatus != "passed") return null
    val title = title.takeIf { it.isNotBlank() } ?: return null
    val description = description.takeIf { it.isNotBlank() } ?: title
    return OfficialBadge(
        title = title,
        description = description,
        iconUrl = icon,
        nightIconUrl = nightIcon,
        url = url,
        type = type,
        detailType = detailType,
    )
}
