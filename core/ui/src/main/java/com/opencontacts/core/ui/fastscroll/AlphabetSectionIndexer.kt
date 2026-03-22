package com.opencontacts.core.ui.fastscroll

import androidx.compose.runtime.Immutable

object AlphabetSections {
    val ALL: List<Char> = buildList {
        add('#')
        addAll(('A'..'Z').toList())
    }
}

@Immutable
data class AlphabetIndex(
    val sectionToPosition: Map<Char, Int>,
    val activeSections: Set<Char>,
    val totalItems: Int,
) {
    fun hasSection(letter: Char): Boolean = activeSections.contains(letter)

    fun resolveNearestPosition(letter: Char): Int? {
        if (totalItems <= 0) return null
        sectionToPosition[letter]?.let { return it }

        val all = AlphabetSections.ALL
        val index = all.indexOf(letter)
        if (index == -1) return 0

        for (i in index + 1 until all.size) {
            sectionToPosition[all[i]]?.let { return it }
        }
        for (i in index - 1 downTo 0) {
            sectionToPosition[all[i]]?.let { return it }
        }
        return 0
    }
}

fun normalizeSectionLetter(rawName: String?): Char {
    val first = rawName?.trim()?.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

inline fun <T> buildAlphabetIndex(
    items: List<T>,
    crossinline nameSelector: (T) -> String?,
): AlphabetIndex {
    if (items.isEmpty()) {
        return AlphabetIndex(emptyMap(), emptySet(), 0)
    }

    val map = LinkedHashMap<Char, Int>(27)
    items.forEachIndexed { index, item ->
        val section = normalizeSectionLetter(nameSelector(item))
        if (!map.containsKey(section)) map[section] = index
    }

    return AlphabetIndex(map, map.keys, items.size)
}

inline fun <T> activeSectionForPosition(
    items: List<T>,
    position: Int,
    crossinline nameSelector: (T) -> String?,
): Char {
    if (items.isEmpty()) return '#'
    val safeIndex = position.coerceIn(0, items.lastIndex)
    return normalizeSectionLetter(nameSelector(items[safeIndex]))
}
