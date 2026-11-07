package app.streamshield.blocking

import java.net.URI

object RequestTypes {
    fun infer(url: String, headers: Map<String, String>): String {
        val destination = headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }?.value?.lowercase()
        when (destination) {
            "script", "image", "font", "document" -> return destination
            "style" -> return "stylesheet"
            "iframe", "frame" -> return "subdocument"
            "video", "audio", "track" -> return "media"
            "empty" -> return "xmlhttprequest"
        }
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value.orEmpty().lowercase()
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> "script"
            path.endsWith(".css") || accept.contains("text/css") -> "stylesheet"
            path.endsWith(".woff2") || path.endsWith(".woff") || path.endsWith(".ttf") -> "font"
            accept.startsWith("image/") || Regex("\\.(png|jpe?g|webp|gif|svg|avif)$").containsMatchIn(path) -> "image"
            path.contains("/videoplayback") || accept.startsWith("video/") || accept.startsWith("audio/") -> "media"
            accept.contains("application/json") || path.startsWith("/youtubei/") || path.startsWith("/api/") -> "xmlhttprequest"
            else -> "other"
        }
    }
}
