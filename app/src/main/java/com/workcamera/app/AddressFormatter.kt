package com.workcamera.app

/**
 * 逆地理编码字段 → 可读地址的纯逻辑（无 Android 依赖，便于单元测试）。
 *
 * 目标形态：县/区 + 小区/街道，例如「云和县小徐云亭小区」。
 * 优先级：行政区（县/区）→ POI/小区名 → 街道名（必要时补门牌号）。
 */
object AddressFormatter {

    private val DETAIL_HINTS = listOf(
        "小区", "花园", "苑", "府", "公寓", "大厦", "广场", "中心",
        "路", "街", "巷", "道", "村", "镇", "乡", "号"
    )

    fun compose(area: String?, feature: String?, street: String?, number: String?): String? {
        val prefix = area?.trim().orEmpty().removePrefix("中国")
        val poi = feature?.trim().orEmpty()
        val road = street?.trim().orEmpty()
        val no = number?.trim().orEmpty()

        var detail = poi
        if (detail.isEmpty()) detail = road
        if (detail.isEmpty()) detail = no
        if (detail.isNotEmpty() && detail == road && no.isNotEmpty()) {
            detail += no
        }

        val text = when {
            prefix.isEmpty() -> detail
            detail.isEmpty() -> prefix
            detail.startsWith(prefix) -> detail
            prefix.contains(detail) -> prefix
            detail.contains(prefix) -> detail
            else -> prefix + detail
        }.trim()

        return text.ifEmpty { null }
    }

    /** 候选地址里挑细节更足的那条（含小区/路/门牌等关键词、且更长） */
    fun score(text: String): Int {
        var value = text.length
        for (hint in DETAIL_HINTS) {
            if (text.contains(hint)) value += 6
        }
        return value
    }

    /** 是否已经精确到街道/小区/门牌级（只有市、县算不够精确） */
    fun isDetailed(text: String): Boolean = DETAIL_HINTS.any { text.contains(it) }
}
