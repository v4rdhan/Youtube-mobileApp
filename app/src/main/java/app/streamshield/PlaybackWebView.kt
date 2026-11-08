package app.streamshield

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/** WebView otherwise suspends video media when its window disappears. */
class PlaybackWebView(context: Context, attributes: AttributeSet? = null) : WebView(context, attributes) {
    var keepMediaAlive = false
        set(value) {
            field = value
            if (!value && windowVisibility != VISIBLE) super.onWindowVisibilityChanged(windowVisibility)
        }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepMediaAlive) VISIBLE else visibility)
    }
}
