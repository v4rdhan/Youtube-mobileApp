package app.streamshield

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.streamshield.blocking.ProtectionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import java.io.ByteArrayInputStream
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progress: LinearProgressIndicator
    private lateinit var web: WebView
    private lateinit var nativeScreen: FrameLayout
    private lateinit var navigation: BottomNavigationView
    private lateinit var protection: ProtectionManager
    private lateinit var savedVideos: SavedVideos
    private var screen = Screen.BROWSER
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var documentScript: ScriptHandler? = null
    @Volatile private var pageUrl = YouTubeUrls.HOME
    private var pageTitle = ""
    private var pendingUrl = YouTubeUrls.HOME
    private var engineStarted = false
    private var errorVisible = false
    private var rendererGone = false
    private val backHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                fullscreen != null -> leaveFullscreen()
                screen == Screen.SETTINGS -> {
                    if (navigation.selectedItemId == R.id.nav_saved) showSaved() else showBrowser()
                }
                screen == Screen.SAVED -> { navigation.selectedItemId = R.id.nav_home }
                web.canGoBack() -> { hideError(); web.goBack() }
            }
            updateBack()
        }
    }
    private enum class Screen { BROWSER, SAVED, SETTINGS }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        toolbar = findViewById(R.id.toolbar)
        progress = findViewById(R.id.progress)
        web = findViewById(R.id.webview)
        nativeScreen = findViewById(R.id.native_screen)
        navigation = findViewById(R.id.navigation)
        protection = ProtectionManager(this)
        savedVideos = SavedVideos(this)
        pendingUrl = incomingUrl(intent) ?: state?.getString("url")?.let(YouTubeUrls::normalize) ?: YouTubeUrls.HOME
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left, system.top, system.right, maxOf(system.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        onBackPressedDispatcher.addCallback(this, backHandler)
        configureWebView()
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        navigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> navigate(YouTubeUrls.HOME)
                R.id.nav_shorts -> navigate(YouTubeUrls.SHORTS)
                R.id.nav_saved -> showSaved()
                R.id.action_protection -> { showSettings(); return@setOnItemSelectedListener false }
                R.id.action_more -> {
                    showMore(navigation.findViewById(R.id.action_more))
                    return@setOnItemSelectedListener false
                }
            }
            true
        }
        showStarting()
        protection.start {
            if (isDestroyed || isFinishing) return@start
            engineStarted = true
            refreshDocumentScript()
            updateProtectionIcon()
            if (screen == Screen.BROWSER) nativeScreen.isVisible = false
            web.loadUrl(pendingUrl)
            if (screen == Screen.SETTINGS) showSettings()
            if (!it) message(R.string.engine_error_detail)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (YouTubeUrls.isYouTube(url)) return false
                if (request.hasGesture()) openExternal(url)
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                val source = request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: pageUrl
                if (!protection.shouldBlock(request.url.toString(), source, request.requestHeaders, request.method)) return null
                return WebResourceResponse("text/plain", "UTF-8", 200, "OK",
                    mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                pageUrl = url ?: YouTubeUrls.HOME
                errorVisible = false
                if (screen == Screen.BROWSER) nativeScreen.isVisible = false
                progress.isVisible = screen == Screen.BROWSER
                updateBack()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                pageUrl = url ?: pageUrl
                injectCosmetics()
                updateBack()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.isVisible = false
                if (url != null) pageUrl = url
                injectCosmetics()
                updateBack()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showError()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                showError()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) { pageTitle = title.orEmpty() }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return }
                fullscreen = view
                fullscreenCallback = callback
                root.isVisible = false
                (window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(-1, -1))
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                updateBack()
            }
            override fun onHideCustomView() = leaveFullscreen()
        }
    }

    private fun leaveFullscreen() {
        val view = fullscreen ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreen = null
        root.isVisible = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        updateBack()
    }

    private fun cssScript(selectors: String): String = """
        (function() {
          const selectors = $selectors;
          function apply() {
            // The native navigation owns the bottom edge, even with protection off.
            let layout = document.getElementById('streamshield-layout');
            if (!layout) { layout = document.createElement('style'); layout.id = 'streamshield-layout'; document.documentElement.appendChild(layout); }
            layout.textContent = 'ytm-pivot-bar-renderer { display: none !important; } ytm-app { padding-bottom: 0 !important; }';
            let style = document.getElementById('streamshield-cosmetics');
            if (!style) { style = document.createElement('style'); style.id = 'streamshield-cosmetics'; document.documentElement.appendChild(style); }
            style.textContent = '';
            for (const selector of selectors) { try { style.sheet.insertRule(selector + ' { display: none !important; }', style.sheet.cssRules.length); } catch (e) {} }
          }
          if (document.documentElement) apply();
          else { const observer = new MutationObserver(function() { if (document.documentElement) { observer.disconnect(); apply(); } }); observer.observe(document, { childList: true, subtree: true }); }
        })();
    """.trimIndent()

    private fun refreshDocumentScript() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) documentScript?.remove()
        documentScript = null
        if (!rendererGone && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentScript = WebViewCompat.addDocumentStartJavaScript(web,
                cssScript(protection.selectors(YouTubeUrls.HOME)), setOf("https://*.youtube.com", "https://youtube.com"))
        }
    }

    private fun injectCosmetics() {
        if (!rendererGone && YouTubeUrls.isYouTube(pageUrl)) web.evaluateJavascript(cssScript(protection.selectors(pageUrl)), null)
    }

    private fun navigate(url: String) {
        pendingUrl = url
        showBrowser()
        if (rendererGone) { recreate(); return }
        if (engineStarted) web.loadUrl(url)
    }

    private fun showBrowser() {
        screen = Screen.BROWSER
        toolbar.isVisible = false
        navigation.isVisible = true
        nativeScreen.isVisible = errorVisible || !engineStarted
        web.isVisible = true
        if (!rendererGone) web.onResume()
        if (errorVisible) showError()
        updateBack()
    }

    private fun nativePage(title: Int, target: Screen): LinearLayout {
        screen = target
        if (!rendererGone) {
            web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
            web.onPause()
        }
        web.isVisible = false
        progress.isVisible = false
        navigation.isVisible = target == Screen.SAVED
        toolbar.isVisible = true
        toolbar.setTitle(title)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.navigationContentDescription = getString(androidx.appcompat.R.string.abc_action_bar_up_description)
        nativeScreen.removeAllViews()
        nativeScreen.isVisible = true
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }
        scroll.addView(column, ViewGroup.LayoutParams(-1, -2))
        nativeScreen.addView(scroll)
        updateBack()
        return column
    }

    private fun showSettings() {
        val column = nativePage(R.string.settings, Screen.SETTINGS)
        column.addView(label(getString(when {
            protection.failed -> R.string.engine_failed
            protection.enabled -> R.string.protection_on
            else -> R.string.protection_off
        }), com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium))
        column.addView(body(resources.getQuantityString(R.plurals.block_count, protection.blocked, protection.blocked)))
        val toggle = MaterialSwitch(this).apply {
            setText(R.string.enable_protection)
            isChecked = protection.enabled
            isEnabled = protection.ready
            minHeight = dp(64)
            setOnCheckedChangeListener { _, checked ->
                protection.enabled = checked
                refreshDocumentScript()
                updateProtectionIcon()
                if (!rendererGone && engineStarted) web.reload()
                showSettings()
            }
        }
        column.addView(toggle, spaced())
        section(column, R.string.native_engine, R.string.engine_description)
        if (protection.failed) column.addView(body(getString(R.string.engine_error_detail)))
        section(column, R.string.filter_lists, R.string.filter_detail)
        val status = if (protection.updatedAt == 0L) getString(R.string.starter_active)
            else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(protection.updatedAt))
        column.addView(body(status))
        val update = button(if (protection.busy) R.string.updating else R.string.update_filters) { button ->
            button.isEnabled = false
            button.setText(R.string.updating)
            protection.updateLists { success ->
                if (isDestroyed) return@updateLists
                if (success) filtersChanged()
                if (screen == Screen.SETTINGS) showSettings()
                message(if (success) R.string.filters_updated else R.string.filters_failed)
            }
        }.apply { isEnabled = !protection.busy }
        column.addView(update, spaced())
        column.addView(label(getString(R.string.custom_rules), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium), sectionSpace())
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.custom_rules_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val editor = TextInputEditText(inputLayout.context).apply {
            minLines = 3
            maxLines = 8
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(protection.customRules)
        }
        inputLayout.addView(editor, LinearLayout.LayoutParams(-1, -2))
        column.addView(inputLayout, spaced())
        column.addView(button(R.string.apply_rules) { action ->
            val rules = editor.text?.toString().orEmpty()
            if (rules.toByteArray().size > 256 * 1024) { inputLayout.error = getString(R.string.rules_too_large); return@button }
            inputLayout.error = null
            action.isEnabled = false
            protection.applyCustom(rules) { success ->
                if (isDestroyed) return@applyCustom
                action.isEnabled = true
                if (success) filtersChanged()
                message(if (success) R.string.rules_applied else R.string.rules_failed)
            }
        }.apply { isEnabled = !protection.busy }, spaced())
        section(column, R.string.playback_note, R.string.playback_detail)
        column.addView(body(getString(R.string.limits_detail)))
        section(column, R.string.licenses, R.string.licenses_detail)
        column.addView(body(getString(R.string.independent)), sectionSpace())
    }

    private fun filtersChanged() {
        refreshDocumentScript()
        updateProtectionIcon()
        if (!rendererGone && engineStarted) web.reload()
    }

    private fun showSaved() {
        val column = nativePage(R.string.saved, Screen.SAVED)
        column.addView(label(getString(R.string.saved_title), com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium))
        val videos = savedVideos.all()
        if (videos.isEmpty()) {
            column.addView(label(getString(R.string.saved_empty), com.google.android.material.R.style.TextAppearance_Material3_TitleLarge), sectionSpace())
            column.addView(body(getString(R.string.saved_detail)))
        }
        videos.forEach { video ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val play = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = video.title.removeSuffix(" - YouTube")
                isAllCaps = false
                minHeight = dp(64)
                maxLines = 3
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { navigate(video.url) }
            }
            row.addView(play, LinearLayout.LayoutParams(0, -2, 1f))
            val remove = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                setIconResource(R.drawable.ic_remove)
                contentDescription = getString(R.string.remove_saved) + ": " + video.title
                setOnClickListener { savedVideos.remove(video.id); showSaved(); message(R.string.removed) }
            }
            row.addView(remove, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(8) })
            column.addView(row, spaced())
        }
    }

    private fun showMore(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.save_video)
        popup.menu.add(0, 2, 1, R.string.reload)
        popup.menu.add(0, 3, 2, R.string.share)
        popup.menu.add(0, 4, 3, R.string.open_browser)
        popup.setOnMenuItemClickListener { choice ->
            when (choice.itemId) {
                1 -> message(if (savedVideos.add(pageTitle, pageUrl)) R.string.saved_feedback else R.string.save_first)
                2 -> { hideError(); if (rendererGone) recreate() else if (engineStarted) web.reload() }
                3 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, pageUrl)
                }, getString(R.string.share)))
                4 -> openExternal(pageUrl)
            }
            true
        }
        popup.show()
    }

    private fun updateProtectionIcon() {
        val menu = navigation.menu.findItem(R.id.action_protection)
        menu.icon?.mutate()?.setTint(color(if (protection.enabled && protection.ready)
            androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant))
        menu.contentDescription = getString(if (protection.enabled && protection.ready) R.string.protection_on else R.string.protection_off)
    }

    private fun openExternal(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "https") return
        // Resolve a general web link first so a YouTube link cannot reopen this app.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")).addCategory(Intent.CATEGORY_BROWSABLE)
        val candidates = packageManager.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }.filter { it.packageName != packageName && it.exported }
        val preferred = packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        val destination = candidates.firstOrNull { it.packageName == preferred?.packageName && it.name == preferred.name }
            ?: candidates.firstOrNull()
        if (destination == null) { message(R.string.no_browser); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).apply { component = ComponentName(destination.packageName, destination.name) }) }
        catch (_: ActivityNotFoundException) { message(R.string.no_browser) }
    }

    private fun showStarting() {
        nativeScreen.removeAllViews()
        nativeScreen.isVisible = true
        nativeScreen.addView(label(getString(R.string.starting), com.google.android.material.R.style.TextAppearance_Material3_BodyLarge).apply {
            gravity = android.view.Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showError() {
        progress.isVisible = false
        errorVisible = true
        if (screen != Screen.BROWSER) return
        nativeScreen.removeAllViews()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        column.addView(label(getString(R.string.connection_error), com.google.android.material.R.style.TextAppearance_Material3_TitleLarge))
        column.addView(body(getString(R.string.connection_detail)), spaced())
        column.addView(button(R.string.retry) {
            hideError()
            if (rendererGone) recreate() else web.loadUrl(pageUrl)
        }, spaced())
        nativeScreen.addView(column, FrameLayout.LayoutParams(-1, -1))
        nativeScreen.isVisible = true
    }

    private fun hideError() { errorVisible = false; if (screen == Screen.BROWSER) nativeScreen.isVisible = false }

    private fun label(text: String, appearance: Int) = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(appearance)
        setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
    }
    private fun body(text: String) = label(text, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium).apply {
        setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(0, dp(8), 0, dp(8))
    }
    private fun section(column: LinearLayout, title: Int, detail: Int) {
        column.addView(label(getString(title), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium), sectionSpace())
        column.addView(body(getString(detail)))
    }
    private fun button(title: Int, action: (MaterialButton) -> Unit) = MaterialButton(this).apply {
        setText(title)
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { action(this) }
    }
    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    private fun sectionSpace() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
    private fun color(attribute: Int) = MaterialColors.getColor(root, attribute)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun message(text: Int) { Snackbar.make(root, text, Snackbar.LENGTH_LONG).show() }
    private fun updateBack() {
        backHandler.isEnabled = fullscreen != null || screen != Screen.BROWSER || (!rendererGone && web.canGoBack())
    }

    private fun incomingUrl(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString?.let(YouTubeUrls::normalize)
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(YouTubeUrls::fromSharedText)
        else -> null
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incomingUrl(intent)?.let(::navigate) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("url", pendingUrl.takeIf { !engineStarted } ?: pageUrl); super.onSaveInstanceState(outState) }
    override fun onPause() {
        if (!rendererGone) {
            web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
            web.onPause()
        }
        CookieManager.getInstance().flush()
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized && screen == Screen.BROWSER && !rendererGone) web.onResume() }
    override fun onDestroy() {
        leaveFullscreen()
        if (!rendererGone && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) documentScript?.remove()
        protection.close()
        if (!rendererGone) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }
}
