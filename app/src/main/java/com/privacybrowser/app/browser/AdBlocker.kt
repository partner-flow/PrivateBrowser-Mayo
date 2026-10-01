package com.privacybrowser.app.browser

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Simple, maintainable host-based content blocker.
 *
 * How it works: [blockedHosts] is loaded from assets/blocklist.txt (one host per line). For every
 * sub-resource request the WebView makes, [shouldBlock] checks whether the request's host matches
 * or is a subdomain of a blocked host. Matches are answered with an empty 200 response instead of
 * letting the request reach the network, which is enough to stop most ad/tracker requests without
 * touching site navigation.
 *
 * Limitations (documented honestly, per the project brief):
 *  - This is a static host list, not a full content-blocking engine (no cosmetic/element hiding,
 *    no regex/URL-pattern rules). It will not catch every tracker, and some ad networks rotate
 *    domains faster than a bundled list can track.
 *  - Blocking is best-effort: it can occasionally break a site that legitimately depends on a
 *    listed domain. Users can disable it per the Settings screen if that happens.
 *  - The list ships inside assets/blocklist.txt and only updates when the app is updated; there is
 *    no remote fetch (by design — this app does not phone home). See README for how to update it
 *    when preparing a new release.
 */
class AdBlocker private constructor(private val blockedHosts: Set<String>) {

    fun shouldBlock(request: WebResourceRequest): Boolean {
        val host = request.url.host ?: return false
        return isHostBlocked(host)
    }

    private fun isHostBlocked(host: String): Boolean {
        val lower = host.lowercase()
        if (blockedHosts.contains(lower)) return true
        // also block subdomains of a blocked host, e.g. "ads.doubleclick.net"
        return blockedHosts.any { blocked -> lower.endsWith(".$blocked") }
    }

    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    companion object {
        fun load(context: Context): AdBlocker {
            val hosts = runCatching {
                context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toSet()
                }
            }.getOrDefault(emptySet())
            return AdBlocker(hosts)
        }
    }
}
