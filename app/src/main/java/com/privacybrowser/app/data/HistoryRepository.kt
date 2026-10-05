package com.privacybrowser.app.data

import android.content.Context
import com.privacybrowser.app.model.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores normal (non-private) browsing history as a small JSON file in app-private internal
 * storage. This intentionally avoids Room/SQLite to keep the first version simple; it is called
 * out as an easy upgrade path in the README.
 *
 * IMPORTANT: no code path from a private tab may call [addVisit]. This is enforced by the
 * BrowserViewModel, which only calls this repository for non-private tabs.
 */
class HistoryRepository(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val list = mutableListOf<HistoryEntry>()
        if (file.exists()) {
            runCatching {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        HistoryEntry(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            url = o.getString("url"),
                            visitedAtEpochMs = o.getLong("visitedAt")
                        )
                    )
                }
            }
        }
        _history.value = list.sortedByDescending { it.visitedAtEpochMs }
    }

    suspend fun addVisit(title: String, url: String) = withContext(Dispatchers.IO) {
        val entry = HistoryEntry(title = title.ifBlank { url }, url = url, visitedAtEpochMs = System.currentTimeMillis())
        val updated = listOf(entry) + _history.value
        _history.value = updated
        persist(updated)
    }

    suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) {
        val updated = _history.value.filterNot { it.id == id }
        _history.value = updated
        persist(updated)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        if (file.exists()) file.delete()
    }

    private fun persist(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("url", it.url)
                    put("visitedAt", it.visitedAtEpochMs)
                }
            )
        }
        file.writeText(arr.toString())
    }
}
