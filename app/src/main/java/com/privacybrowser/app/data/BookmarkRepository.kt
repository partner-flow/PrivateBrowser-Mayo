package com.privacybrowser.app.data

import android.content.Context
import com.privacybrowser.app.model.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BookmarkRepository(context: Context) {

    private val file = File(context.filesDir, "bookmarks.json")
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bookmark>()
        if (file.exists()) {
            runCatching {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Bookmark(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            url = o.getString("url"),
                            createdAtEpochMs = o.getLong("createdAt")
                        )
                    )
                }
            }
        }
        _bookmarks.value = list.sortedByDescending { it.createdAtEpochMs }
    }

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { it.url == url }

    suspend fun add(title: String, url: String) = withContext(Dispatchers.IO) {
        if (isBookmarked(url)) return@withContext
        val bookmark = Bookmark(title = title.ifBlank { url }, url = url, createdAtEpochMs = System.currentTimeMillis())
        val updated = listOf(bookmark) + _bookmarks.value
        _bookmarks.value = updated
        persist(updated)
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        val updated = _bookmarks.value.filterNot { it.url == url }
        _bookmarks.value = updated
        persist(updated)
    }

    suspend fun removeById(id: String) = withContext(Dispatchers.IO) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        persist(updated)
    }

    private fun persist(entries: List<Bookmark>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("url", it.url)
                    put("createdAt", it.createdAtEpochMs)
                }
            )
        }
        file.writeText(arr.toString())
    }
}
