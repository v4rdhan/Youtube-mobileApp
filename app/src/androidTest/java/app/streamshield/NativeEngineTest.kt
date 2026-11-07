package app.streamshield

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.streamshield.blocking.NativeBridge
import app.streamshield.blocking.ProtectionManager
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test fun jniLoadsTheSuppliedEngineAndRespectsRules() {
        val rules = context.assets.open("filters/starter.txt").bufferedReader().use { it.readText() }
        assertTrue(NativeBridge.replaceRules(rules))
        assertTrue(NativeBridge.shouldBlock("https://googleads.g.doubleclick.net/pagead/id", source, "script", "GET"))
        assertFalse(NativeBridge.shouldBlock("https://rr1.googlevideo.com/videoplayback?id=sample", source, "media", "GET"))
        assertFalse(NativeBridge.shouldBlock("invalid", source, "other", "GET"))
        val selectors = JSONArray(NativeBridge.cosmeticSelectors(source))
        assertTrue((0 until selectors.length()).map(selectors::getString).contains("ytm-ad-slot-renderer"))
    }

    @Test fun concurrentRequestChecksRemainSafeWhileRulesAreReplaced() {
        val rules = "||ads.example.org^\n@@||ads.example.org/allowed.js"
        assertTrue(NativeBridge.replaceRules(rules))
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (0 until 4).map {
                pool.submit<Boolean> {
                    repeat(50) {
                        check(NativeBridge.shouldBlock("https://ads.example.org/ad.js", source, "script", "GET"))
                        check(!NativeBridge.shouldBlock("https://ads.example.org/allowed.js", source, "script", "GET"))
                    }
                    true
                }
            }
            repeat(10) { assertTrue(NativeBridge.replaceRules(rules)) }
            results.forEach { assertTrue(it.get(20, TimeUnit.SECONDS)) }
        } finally { pool.shutdownNow() }
    }

    @Test fun protectionToggleReallyBypassesNetworkFiltering() {
        val manager = ProtectionManager(context)
        val wasEnabled = manager.enabled
        val started = CountDownLatch(1)
        try {
            manager.start { assertTrue(it); started.countDown() }
            assertTrue(started.await(20, TimeUnit.SECONDS))
            manager.enabled = true
            assertTrue(manager.shouldBlock("https://googleads.g.doubleclick.net/pagead/id", source, emptyMap(), "GET"))
            assertEquals(1, manager.blocked)
            manager.enabled = false
            assertFalse(manager.shouldBlock("https://googleads.g.doubleclick.net/pagead/id", source, emptyMap(), "GET"))
            assertEquals(1, manager.blocked)
        } finally { manager.enabled = wasEnabled; manager.close() }
    }
}
