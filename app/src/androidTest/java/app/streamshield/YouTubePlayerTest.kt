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
        val latch = CountDownLatch(1)
        val value = AtomicReference("{}")
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.webview).evaluateJavascript("""
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
            """.trimIndent()) { value.set(it); latch.countDown() }
        }
        assertTrue("JavaScript evaluation completed", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(value.get())
    }

    private fun toggle(activity: MainActivity): MaterialSwitch =
        descendants(activity.findViewById(R.id.native_screen)).filterIsInstance<MaterialSwitch>().single()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
