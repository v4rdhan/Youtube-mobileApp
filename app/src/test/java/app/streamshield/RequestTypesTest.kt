package app.streamshield

import app.streamshield.blocking.RequestTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestTypesTest {
    @Test fun classifiesUsingBrowserHintsBeforeFileExtensions() {
        assertEquals("xmlhttprequest", RequestTypes.infer("https://example.org/script.js", mapOf("sec-fetch-dest" to "empty")))
        assertEquals("subdocument", RequestTypes.infer("https://example.org", mapOf("Sec-Fetch-Dest" to "iframe")))
    }
    @Test fun classifiesVideoAndApiRequestsWithoutHints() {
        assertEquals("media", RequestTypes.infer("https://rr1.googlevideo.com/videoplayback?x=1", emptyMap()))
        assertEquals("xmlhttprequest", RequestTypes.infer("https://m.youtube.com/youtubei/v1/player", emptyMap()))
        assertEquals("image", RequestTypes.infer("https://i.ytimg.com/vi/id/hqdefault.jpg?x=1", emptyMap()))
    }
}
