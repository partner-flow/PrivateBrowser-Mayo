package com.privacybrowser.app.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.core.net.toUri

/**
 * Wraps Android's system DownloadManager. Using DownloadManager (rather than hand-rolling file
 * writes) means downloads survive the app being closed, show up in the system's own Downloads
 * app/notification shade, and don't require this app to manage raw file-storage permissions on
 * modern Android — files land in the public Downloads collection via
 * Environment.DIRECTORY_DOWNLOADS, which is permission-free on API 29+ (scoped storage) and only
 * needs WRITE_EXTERNAL_STORAGE on API 28 and below (declared with maxSdkVersion=28 in the manifest).
 *
 * Private-tab downloads: per the spec, a private download must not be silently added to normal
 * browsing history. DownloadManager entries aren't browsing history to begin with (they're a
 * separate system list), so no extra suppression is needed there — but callers should still avoid
 * calling HistoryRepository for a download's page visit when the tab is private, which
 * BrowserViewModel already guarantees for page loads.
 */
class DownloadsManager(private val context: Context) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** True only on API 28 and below when the legacy storage permission hasn't been granted yet. */
    fun needsLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasLegacyStoragePermission()

    /** Returns true if the download was enqueued; false if it needs a permission or the request failed. */
    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Boolean {
        if (needsLegacyStoragePermission()) return false

        return runCatching {
            val fileName = guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(url.toUri()).apply {
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                // addRequestHeader throws on a null value, so only add headers that exist.
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                // Cookies are needed for sites that gate downloads behind a login.
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
                    ?.let { addRequestHeader("Cookie", it) }
                setDescription("Downloading from ${Uri.parse(url).host ?: url}")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            downloadManager.enqueue(request)
            true
        }.getOrDefault(false)
    }

    private fun hasLegacyStoragePermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        return runCatching {
            android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        }.getOrElse {
            val lastSegment = Uri.parse(url).lastPathSegment
            val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            when {
                !lastSegment.isNullOrBlank() -> lastSegment
                !ext.isNullOrBlank() -> "download.$ext"
                else -> "download"
            }
        }
    }

    /** Queries the system DownloadManager for everything this app has requested, newest first. */
    fun queryDownloads(): List<SystemDownloadEntry> {
        val cursor = downloadManager.query(DownloadManager.Query())
        val results = mutableListOf<SystemDownloadEntry>()
        cursor?.use {
            val idIdx = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleIdx = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val uriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            while (it.moveToNext()) {
                results.add(
                    SystemDownloadEntry(
                        id = it.getLong(idIdx),
                        title = it.getString(titleIdx) ?: "Download",
                        status = it.getInt(statusIdx),
                        totalBytes = it.getLong(totalIdx),
                        downloadedBytes = it.getLong(downloadedIdx),
                        localUri = it.getString(uriIdx),
                        failureReason = it.getInt(reasonIdx)
                    )
                )
            }
        }
        return results.sortedByDescending { it.id }
    }

    fun removeDownload(id: Long) {
        runCatching { downloadManager.remove(id) }
    }

    /**
     * Returns a safe URI for opening a completed download in another app.
     *
     * Deliberately does NOT use the raw COLUMN_LOCAL_URI from [queryDownloads]: on API 24–28 that
     * can be a bare `file://` URI, and handing that to another app via ACTION_VIEW throws
     * FileUriExposedException (StrictMode) on those versions. DownloadManager's own
     * getUriForDownloadedFile() returns a `content://` URI backed by DownloadManager's provider
     * on every supported API level, which is safe to share and doesn't require this app to run
     * its own FileProvider.
     */
    fun getOpenUri(id: Long): Uri? = runCatching { downloadManager.getUriForDownloadedFile(id) }.getOrNull()
}

data class SystemDownloadEntry(
    val id: Long,
    val title: String,
    val status: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val localUri: String?,
    val failureReason: Int
) {
    val isComplete: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
    val isRunning: Boolean get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
    val progressPercent: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
}
