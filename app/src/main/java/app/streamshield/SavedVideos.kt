package app.streamshield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedVideo(val id: String, val title: String, val url: String)

class SavedVideos(context: Context) {
    private val preferences = context.getSharedPreferences("saved-videos", Context.MODE_PRIVATE)

    fun all(): List<SavedVideo> = runCatching {
        val json = JSONArray(preferences.getString("videos", "[]"))
        (0 until json.length()).mapNotNull { index ->
            val item = json.getJSONObject(index)
            val url = item.getString("url")
            val id = YouTubeUrls.videoKey(url) ?: return@mapNotNull null
            SavedVideo(id, item.getString("title"), url)
        }
    }.getOrDefault(emptyList())

    fun add(title: String, url: String): Boolean {
        val id = YouTubeUrls.videoKey(url) ?: return false
        val normalized = YouTubeUrls.normalize(url) ?: return false
        write(listOf(SavedVideo(id, title.ifBlank { "YouTube video" }, normalized)) + all().filter { it.id != id })
        return true
    }

    fun remove(id: String) = write(all().filter { it.id != id })

    private fun write(videos: List<SavedVideo>) {
        val array = JSONArray()
        videos.take(500).forEach { video ->
            array.put(JSONObject().put("id", video.id).put("title", video.title).put("url", video.url))
        }
        preferences.edit().putString("videos", array.toString()).apply()
    }
}
