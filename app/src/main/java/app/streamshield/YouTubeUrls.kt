package app.streamshield

import java.net.URI
import java.net.URLEncoder

object YouTubeUrls {
    const val HOME = "https://m.youtube.com/"
    const val SHORTS = "https://m.youtube.com/shorts"
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun isYouTube(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in hosts && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)

    fun normalize(url: String): String? = runCatching {
        val input = url.trim()
        if (!isYouTube(input)) return null
        val uri = URI(input)
        if (uri.host.equals("youtu.be", true)) {
            val id = uri.path.trim('/').takeIf { videoId.matches(it) } ?: return null
            "${HOME}watch?v=$id" + (uri.rawQuery?.let { "&$it" } ?: "")
        } else {
            "https://m.youtube.com${uri.rawPath.ifEmpty { "/" }}" +
                (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        }
    }.getOrNull()

    fun searchOrUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(Regex("^[A-Za-z][A-Za-z0-9+.-]*:"))) return normalize(trimmed)
        if (hosts.any { trimmed.startsWith("$it/", true) || trimmed.equals(it, true) }) return normalize("https://$trimmed")
        return "${HOME}results?search_query=${URLEncoder.encode(trimmed, "UTF-8")}" 
    }

    fun fromSharedText(text: String): String? = Regex("https://[^\\s<>]+")
        .findAll(text).mapNotNull { normalize(it.value.trimEnd('.', ',', ')', ']')) }.firstOrNull()

    fun videoKey(url: String): String? = runCatching {
        val normalized = normalize(url) ?: return null
        val uri = URI(normalized)
        val path = uri.path.split('/').filter { it.isNotEmpty() }
        val id = if (path.firstOrNull() in listOf("shorts", "live", "embed")) path.getOrNull(1)
        else if (uri.path == "/watch") uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
        else null
        id?.takeIf { videoId.matches(it) }
    }.getOrNull()
}
