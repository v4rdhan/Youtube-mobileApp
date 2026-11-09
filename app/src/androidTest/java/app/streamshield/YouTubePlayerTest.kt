package app.streamshield

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class YouTubePlayerTest {
    @Test fun filteredStartupWithoutAdMarkerRetriesOnceOnlyWithProtectionEnabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0) }
            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { ready = toggle(it).isEnabled }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Protection engine should start", ready)
            try {
                for (enabled in listOf(true, false)) {
                    scenario.onActivity { activity ->
                        activity.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0)
                        toggle(activity).isChecked = enabled
                        activity.onBackPressedDispatcher.onBackPressed()
                        activity.findViewById<WebView>(R.id.webview).loadDataWithBaseURL(
                            "https://m.youtube.com/watch?v=fixture",
                            """<!doctype html><html><body>
                              <div id="movie_player" class="html5-video-player"><video></video></div>
                              <script>
                                var ytInitialPlayerResponse = {
                                  adPlacements: [{}], playerAds: [{}], adSlots: [{}],
                                  videoDetails: {videoId: 'fixture'},
                                  streamingData: {formats: [{url: 'https://video.googlevideo.com/videoplayback'}]},
                                  playabilityStatus: {status: 'OK'}
                                };
                                window.fixtureToken = '$enabled';
                                window.retryCount = 0;
                                var player = document.getElementById('movie_player');
                                player.getPlayerResponse = function() { return ytInitialPlayerResponse; };
                                player.getPlayerState = function() { return 3; };
                                player.loadVideoById = function(id, position) {
                                  window.retryCount++;
                                  window.retriedId = id; window.retriedPosition = position;
                                };
                                window.fixtureStarted = Date.now();
                              </script></body></html>""",
                            "text/html", "UTF-8", null
                        )
                    }
                    var result = JSONObject()
                    val pageDeadline = System.currentTimeMillis() + 15_000
                    while (System.currentTimeMillis() < pageDeadline) {
                        result = evaluateExpression(scenario, """
                            ({token: window.fixtureToken, retries: window.retryCount,
                              id: window.retriedId, position: window.retriedPosition,
                              elapsed: Date.now() - window.fixtureStarted,
                              visibility: document.visibilityState,
                              ad: document.getElementById('movie_player')?.classList.contains('ad-showing')})
                        """.trimIndent())
                        if (result.optString("token") == "$enabled" && result.optInt("elapsed") >= 10_000) break
                        Thread.sleep(100)
                    }
                    assertEquals("Fixture loaded", "$enabled", result.optString("token"))
                    assertEquals("Test exercises visible startup", "visible", result.getString("visibility"))
                    assertFalse("No ad marker is present", result.getBoolean("ad"))
                    assertEquals("Unmarked startup retries only with Protection", if (enabled) 1 else 0, result.getInt("retries"))
                    if (enabled) {
                        assertEquals("fixture", result.getString("id"))
                        assertEquals(0, result.getInt("position"))
                    }
                }
            } finally {
                scenario.onActivity {
                    it.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0)
                    toggle(it).isChecked = true
                }
            }
        }
    }

    @Test fun activeAndBlockedPrerollsRecoverOnlyWithProtectionEnabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0) }
            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { ready = toggle(it).isEnabled }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Protection engine should start", ready)
            try {
                for (enabled in listOf(true, false)) {
                    scenario.onActivity { activity ->
                        activity.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0)
                        toggle(activity).isChecked = enabled
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                    for (skipAvailable in listOf(true, false)) {
                        val token = "$enabled-$skipAvailable"
                        scenario.onActivity { activity ->
                            activity.findViewById<WebView>(R.id.webview).loadDataWithBaseURL(
                                "https://m.youtube.com/watch?v=fixture",
                                """<!doctype html><html><body>
                                  <div id="movie_player" class="html5-video-player ad-showing">
                                    <video></video>
                                    ${if (skipAvailable) "<button class=\"ytp-skip-ad-button\">Skip</button>" else ""}
                                  </div>
                                  <script>
                                    var ytInitialPlayerResponse = {
                                      adPlacements: [{}], playerAds: [{}], adSlots: [{}],
                                      videoDetails: {videoId: 'fixture'},
                                      streamingData: {formats: [{url: 'https://video.googlevideo.com/videoplayback'}]},
                                      playabilityStatus: {status: 'OK'}
                                    };
                                    window.fixtureToken = '$token';
                                    window.skipCount = 0; window.retryCount = 0;
                                    var player = document.getElementById('movie_player');
                                    player.getPlayerResponse = function() { return ytInitialPlayerResponse; };
                                    player.loadVideoById = function(id, position) {
                                      window.retryCount++;
                                      window.retriedId = id; window.retriedPosition = position;
                                      player.classList.remove('ad-showing');
                                    };
                                    var skip = player.querySelector('button');
                                    if (skip) skip.onclick = function() {
                                      window.skipCount++; player.classList.remove('ad-showing');
                                    };
                                    window.fixtureStarted = Date.now();
                                  </script></body></html>""",
                                "text/html", "UTF-8", null
                            )
                        }
                        var result = JSONObject()
                        val pageDeadline = System.currentTimeMillis() + 10_000
                        while (System.currentTimeMillis() < pageDeadline) {
                            result = evaluateExpression(scenario, """
                                ({token: window.fixtureToken, skips: window.skipCount, retries: window.retryCount,
                                  id: window.retriedId, position: window.retriedPosition,
                                  elapsed: Date.now() - window.fixtureStarted,
                                  installed: !!window.__streamshieldPlayerProtection,
                                  visibility: document.visibilityState,
                                  rects: document.querySelector('button')?.getClientRects().length,
                                  ad: document.getElementById('movie_player')?.classList.contains('ad-showing')})
                            """.trimIndent())
                            if (result.optString("token") == token && result.optInt("elapsed") >= 2000 &&
                                result.optInt("skips") == (if (enabled && skipAvailable) 1 else 0) &&
                                result.optInt("retries") == (if (enabled && !skipAvailable) 1 else 0)) break
                            Thread.sleep(100)
                        }
                        assertEquals("Fixture loaded", token, result.optString("token"))
                        assertEquals("Skip control follows Protection: $result", if (enabled && skipAvailable) 1 else 0, result.getInt("skips"))
                        assertEquals("Blocked ad retries once: $result", if (enabled && !skipAvailable) 1 else 0, result.getInt("retries"))
                        assertEquals("Ad state is exited only with Protection", !enabled, result.getBoolean("ad"))
                        if (enabled && !skipAvailable) {
                            assertEquals("fixture", result.getString("id"))
                            assertEquals(0, result.getInt("position"))
                        }
                    }
                }
            } finally {
                scenario.onActivity {
                    it.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0)
                    toggle(it).isChecked = true
                }
            }
        }
    }

    @Test fun playerFilteringRunsBeforePlaybackAndFollowsProtectionSwitch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<BottomNavigationView>(R.id.navigation).menu.performIdentifierAction(R.id.action_protection, 0) }
            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity -> ready = toggle(activity).isEnabled }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Protection engine should start", ready)
            try {
                for ((index, enabled) in listOf(true, false, true).withIndex()) {
                    scenario.onActivity { activity ->
                        toggle(activity).isChecked = enabled
                        activity.findViewById<WebView>(R.id.webview).loadDataWithBaseURL(
                            YouTubeUrls.HOME,
                            """<!doctype html><html><body><script>
                              var ytInitialPlayerResponse = {
                                adPlacements: [{}], playerAds: [{}], adSlots: [{}],
                                videoDetails: {videoId: 'fixture'},
                                streamingData: {formats: [{url: 'https://video.googlevideo.com/videoplayback'}]},
                                playabilityStatus: {status: 'LOGIN_REQUIRED'}
                              };
                              window.startupHadAds = 'adPlacements' in ytInitialPlayerResponse;
                              window.fixtureToken = $index;
                            </script></body></html>""",
                            "text/html", "UTF-8", null
                        )
                    }
                    var result = JSONObject()
                    val pageDeadline = System.currentTimeMillis() + 10_000
                    while (System.currentTimeMillis() < pageDeadline) {
                        result = evaluate(scenario)
                        if (result.optInt("token", -1) == index && result.optBoolean("complete") &&
                            result.optBoolean("installed") == enabled) break
                        Thread.sleep(100)
                    }
                    assertEquals("Fixture loaded", index, result.optInt("token", -1))
                    assertEquals("Protection hook follows switch", enabled, result.optBoolean("installed"))
                    assertEquals("Known player ad fields follow switch", !enabled, result.getBoolean("hasAds"))
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        assertEquals("Inline player data filtered before first read", !enabled, result.getBoolean("startupHadAds"))
                    }
                    assertEquals("fixture", result.getString("videoId"))
                    assertEquals("LOGIN_REQUIRED", result.getString("status"))
                    assertEquals("https://video.googlevideo.com/videoplayback", result.getString("stream"))
                }
            } finally {
                scenario.onActivity { activity -> toggle(activity).isChecked = true }
            }
        }
    }

    private fun evaluate(scenario: ActivityScenario<MainActivity>): JSONObject {
        return evaluateExpression(scenario, """
            (function() {
              var player = window.ytInitialPlayerResponse || {};
              return {
                token: window.fixtureToken,
                complete: document.readyState === 'complete',
                installed: !!window.__streamshieldPlayerProtection,
                startupHadAds: window.startupHadAds,
                hasAds: 'adPlacements' in player || 'playerAds' in player || 'adSlots' in player,
                videoId: player.videoDetails && player.videoDetails.videoId,
                status: player.playabilityStatus && player.playabilityStatus.status,
                stream: player.streamingData && player.streamingData.formats[0].url
              };
            })()
        """.trimIndent())
    }

    private fun evaluateExpression(scenario: ActivityScenario<MainActivity>, expression: String): JSONObject {
        val latch = CountDownLatch(1)
        val value = AtomicReference("{}")
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.webview).evaluateJavascript(expression) { value.set(it); latch.countDown() }
        }
        assertTrue("JavaScript evaluation completed", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(value.get().takeUnless { it == "null" } ?: "{}")
    }

    private fun toggle(activity: MainActivity): MaterialSwitch =
        descendants(activity.findViewById(R.id.native_screen)).filterIsInstance<MaterialSwitch>().single()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
