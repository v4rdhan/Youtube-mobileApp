package app.streamshield.blocking

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProtectionManager(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("protection", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val downloaded = AtomicFile(File(app.filesDir, "filter-lists.txt"))
    private val count = AtomicInteger()
    @Volatile var ready = false; private set
    @Volatile var failed = false; private set
    @Volatile var busy = false; private set
    @Volatile var enabled = preferences.getBoolean("enabled", true)
        set(value) { field = value; preferences.edit().putBoolean("enabled", value).apply() }
    val blocked: Int get() = count.get()
    val customRules: String get() = preferences.getString("custom", "").orEmpty()
    val updatedAt: Long get() = preferences.getLong("updated", 0L)

    fun start(done: (Boolean) -> Unit) = submit(done) {
        val lists = runCatching { downloaded.openRead().bufferedReader().use { it.readText() } }.getOrDefault("")
        check(NativeBridge.replaceRules(combined(lists, customRules)))
        ready = true
    }

    private fun combined(lists: String, custom: String): String =
        app.assets.open("filters/starter.txt").bufferedReader().use { it.readText() } + "\n" + lists + "\n" + custom

    private fun submit(done: (Boolean) -> Unit, action: () -> Unit) {
        if (busy) { main.post { done(false) }; return }
        busy = true
        worker.execute {
            val success = try { action(); true } catch (_: Exception) { false } catch (_: LinkageError) { false }
            if (!ready) failed = !success
            busy = false
            main.post { done(success) }
        }
    }

    fun updateLists(done: (Boolean) -> Unit) = submit(done) {
        val lists = listOf("https://easylist.to/easylist/easylist.txt", "https://easylist.to/easylist/easyprivacy.txt")
            .joinToString("\n") { download(it) }
        // Complete both downloads before touching the active engine or cached list.
        val rules = combined(lists, customRules)
        persistLists(lists)
        check(NativeBridge.replaceRules(rules))
        ready = true
        failed = false
        preferences.edit().putLong("updated", System.currentTimeMillis()).apply()
    }

    fun applyCustom(rules: String, done: (Boolean) -> Unit) = submit(done) {
        require(rules.toByteArray().size <= 256 * 1024)
        val lists = runCatching { downloaded.openRead().bufferedReader().use { it.readText() } }.getOrDefault("")
        check(NativeBridge.replaceRules(combined(lists, rules)))
        ready = true
        failed = false
        preferences.edit().putString("custom", rules).apply()
    }

    private fun persistLists(text: String) {
        val stream = downloaded.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); downloaded.finishWrite(stream) }
        catch (error: Exception) { downloaded.failWrite(stream); throw error }
    }

    private fun download(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "StreamShield/0.1 filter-updater")
        try {
            check(connection.responseCode == 200) { "Filter download failed" }
            val data = connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    check(output.size() + length <= 8 * 1024 * 1024) { "Filter list too large" }
                    output.write(buffer, 0, length)
                }
                output.toString("UTF-8")
            }
            check(data.startsWith("[Adblock") && data.lines().size > 100) { "Invalid filter list" }
            return data
        } finally { connection.disconnect() }
    }

    fun shouldBlock(url: String, source: String, headers: Map<String, String>, method: String): Boolean {
        if (!enabled || !ready) return false
        return try {
            NativeBridge.shouldBlock(url, source, RequestTypes.infer(url, headers), method).also {
                if (it) count.incrementAndGet()
            }
        } catch (_: LinkageError) { false }
    }

    fun selectors(url: String): String {
        if (!enabled || !ready) return "[]"
        return try { JSONArray(NativeBridge.cosmeticSelectors(url)).toString() }
        catch (_: Exception) { "[]" } catch (_: LinkageError) { "[]" }
    }

    override fun close() { worker.shutdown() }
}
