package com.opencontacts.core.ui.fastscroll

import kotlin.math.floor

object AlphabetTouchMapper {
    fun mapYToLetter(
        y: Float,
        heightPx: Float,
        sections: List<Char> = AlphabetSections.ALL,
    ): Char {
        if (sections.isEmpty()) return '#'
        if (heightPx <= 0f) return sections.first()
        val cellHeight = heightPx / sections.size
        val rawIndex = floor(y / cellHeight).toInt()
        val clamped = rawIndex.coerceIn(0, sections.lastIndex)
        return sections[clamped]
    }
}
