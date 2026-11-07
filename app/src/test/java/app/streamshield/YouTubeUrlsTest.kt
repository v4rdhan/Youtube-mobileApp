package app.streamshield

import org.junit.Assert.*
import org.junit.Test

class YouTubeUrlsTest {
    @Test fun normalizesSharedVideoAndKeepsTimestamp() {
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=15", YouTubeUrls.normalize("https://youtu.be/dQw4w9WgXcQ?t=15"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrls.videoKey("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }
    @Test fun rejectsHostSpoofingAndExecutableSchemes() {
        for (input in listOf("https://youtube.com.evil.org/watch?v=test", "https://youtube.com@evil.org/", "javascript:alert(1)", "file:///etc/passwd", "http://youtube.com/", "https://m.youtube.com:8080/")) {
            assertNull(input, YouTubeUrls.normalize(input))
        }
    }
    @Test fun searchesAndExtractsLinksFromShareMessages() {
        assertEquals("https://m.youtube.com/results?search_query=rust+on+android", YouTubeUrls.searchOrUrl("rust on android"))
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeUrls.fromSharedText("Watch this https://youtu.be/dQw4w9WgXcQ"))
        assertNull(YouTubeUrls.searchOrUrl("javascript:alert(1)"))
        assertNull(YouTubeUrls.videoKey("https://m.youtube.com/results?search_query=test"))
    }
}
