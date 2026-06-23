package com.biliclient.feature.tv.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinyinEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val table: Map<String, List<String>> by lazy { loadTable() }

    fun getCandidates(pinyin: String): List<String> {
        if (pinyin.isBlank()) return emptyList()
        val p = pinyin.lowercase()
        table[p]?.let { return it.take(30) }
        return table.entries
            .filter { it.key.startsWith(p) }
            .flatMap { it.value }
            .distinct()
            .take(30)
    }

    private fun loadTable(): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        context.assets.open("pinyin_to_chars.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(" ")
                if (parts.size >= 2) {
                    val py = parts[0]
                    val chars = parts.drop(1)
                    map.getOrPut(py) { mutableListOf() }.addAll(chars)
                }
            }
        }
        return map
    }
}
