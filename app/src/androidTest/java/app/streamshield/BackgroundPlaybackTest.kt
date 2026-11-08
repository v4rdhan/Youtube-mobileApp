package app.streamshield

import android.app.Notification
import android.app.NotificationManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.PowerManager
import android.util.Base64
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun audibleMediaContinuesWithScreenOffAndNotificationPauseStopsIt() = checkPlayback(video = false)

    @Test fun videoAudioContinuesWithScreenOffAndNotificationPauseStopsIt() = checkPlayback(video = true)

    private fun checkPlayback(video: Boolean) {
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var ready = false
            waitUntil {
                scenario.onActivity { activity ->
                    ready = activity.findViewById<BottomNavigationView>(R.id.navigation).menu
                        .findItem(R.id.action_protection).contentDescription != null
                }
                ready
            }
            val web = AtomicReference<WebView>()
            val media = if (video) videoFixture() else """
                <audio id="audio" loop src="data:audio/wav;base64,${audioData()}"></audio>
                <script>document.getElementById('audio').play();</script>
            """.trimIndent()
            scenario.onActivity { activity ->
                web.set(activity.findViewById(R.id.webview))
                web.get().settings.mediaPlaybackRequiresUserGesture = false
                web.get().loadDataWithBaseURL(YouTubeUrls.HOME, """
                    <!doctype html><html><head><title>Background audio fixture</title></head><body>
                    $media
                    <script>
                      document.addEventListener('visibilitychange', function() {
                        if (document.hidden) document.getElementById('audio').pause();
                      });
                    </script></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            val notifications = context.getSystemService(NotificationManager::class.java)
            waitUntil { state(web.get()).optDouble("time") > 0.5 && notifications.activeNotifications.isNotEmpty() }
            val before = state(web.get()).getDouble("time")
            if (video) assertTrue("Fixture has a real video track", state(web.get()).getInt("width") > 0)
            val notification = notifications.activeNotifications.single().notification
            assertTrue("Playback is associated with a media session", notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))
            shell("input keyevent 223")
            waitUntil { !context.getSystemService(PowerManager::class.java).isInteractive }
            waitUntil {
                val current = state(web.get())
                !current.optBoolean("paused", true) && current.optDouble("time") > before + 3
            }
            assertFalse("Screen remains off during playback", context.getSystemService(PowerManager::class.java).isInteractive)
            if (video) {
                val token = BundleCompat.getParcelable(notification.extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
                assertNotNull("Media session token is present", token)
                MediaController(context, token!!).transportControls.pause()
            } else {
                notifications.activeNotifications.single().notification.actions.single().actionIntent.send()
            }
            waitUntil { state(web.get()).optBoolean("paused") && notifications.activeNotifications.isEmpty() }
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            scenario.close()
        }
    }

    private fun state(web: WebView): JSONObject {
        val latch = CountDownLatch(1)
        val value = AtomicReference("{}")
        instrumentation.runOnMainSync {
            web.evaluateJavascript("(function(){var a=document.getElementById('audio');return a ? {paused:a.paused,time:a.currentTime,width:a.videoWidth||0} : {};})()") {
                value.set(it); latch.countDown()
            }
        }
        assertTrue("WebView responds", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(value.get())
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for playback state")
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    private fun audioData(): String {
        val sampleRate = 8000
        val samples = sampleRate * 20
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { buffer.putShort((sin(2 * Math.PI * 440 * it / sampleRate) * 1000).toInt().toShort()) }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    // Produce a real VP8/Opus clip offline so this catches WebView's video-specific
    // window suspension, which an audio-only fixture cannot reproduce.
    private fun videoFixture() = """
        <canvas id="canvas" width="32" height="32"></canvas><video id="audio" loop></video>
        <script>
          (async function() {
            const canvas = document.getElementById('canvas');
            const context = canvas.getContext('2d');
            const stream = canvas.captureStream(5);
            const audio = new AudioContext();
            await audio.resume();
            const oscillator = audio.createOscillator();
            const gain = audio.createGain(); gain.gain.value = 0.03;
            const destination = audio.createMediaStreamDestination();
            oscillator.connect(gain).connect(destination); oscillator.start();
            stream.addTrack(destination.stream.getAudioTracks()[0]);
            const recorder = new MediaRecorder(stream, {mimeType:'video/webm;codecs=vp8,opus'});
            const chunks = [];
            recorder.ondataavailable = event => chunks.push(event.data);
            const draw = setInterval(function() {
              context.fillStyle = 'rgb(' + (Date.now() % 255) + ',80,100)';
              context.fillRect(0,0,32,32);
            }, 200);
            recorder.onstop = async function() {
              clearInterval(draw); oscillator.stop(); stream.getTracks().forEach(track => track.stop());
              await audio.close();
              const video = document.getElementById('audio');
              video.src = URL.createObjectURL(new Blob(chunks, {type:recorder.mimeType}));
              await video.play();
            };
            recorder.start(); setTimeout(function() { recorder.stop(); }, 6000);
          })();
        </script>
    """.trimIndent()
}
