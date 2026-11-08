package app.streamshield

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/** Keeps the existing WebView playback alive while its activity's screen is off.
 * The activity owns playback; closing its task or pausing media ends this service.
 */
class PlaybackService : Service() {
    private lateinit var session: MediaSession
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:playback")
            .apply { setReferenceCounted(false) }
        session = MediaSession(this, "StreamShield").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = pausePlayback()
                override fun onStop() = pausePlayback()
            })
            setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
            isActive = true
        }
    }

    @SuppressLint("WakelockTimeout") // Released on pause, navigation, renderer loss, task removal, or activity destruction.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            pausePlayback()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_name) }
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java)
            .setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_playback)
            .setContentTitle(title)
            .setContentText(getString(R.string.screen_off_playback))
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), getString(R.string.pause_playback), pause).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        if (!wakeLock.isHeld) wakeLock.acquire()
        return START_NOT_STICKY
    }

    private fun pausePlayback() {
        sendBroadcast(Intent(ACTION_PAUSE).setPackage(packageName))
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { pausePlayback(); super.onTaskRemoved(rootIntent) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        session.isActive = false
        session.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "app.streamshield.PAUSE_PLAYBACK"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
