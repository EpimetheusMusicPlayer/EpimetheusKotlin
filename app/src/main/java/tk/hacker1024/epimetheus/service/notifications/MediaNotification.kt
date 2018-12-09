package tk.hacker1024.epimetheus.service.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.EpimetheusMusicService

private const val CHANNEL_ID = "media"
private const val NAME = "Media"
private const val DESCRIPTION = "Media notifications with controls."

/**
 * Media notification
 */

internal class MediaNotification(
    private val context: Context,
    private val sessionToken: MediaSessionCompat.Token
) {
    private val notificationManager by lazy {
        NotificationManagerCompat.from(context)
    }

    private val playAction by lazy {
        NotificationCompat.Action(
            R.drawable.ic_play_arrow_black_24dp,
            "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PLAY
            )
        )
    }

    private val pauseAction by lazy {
        NotificationCompat.Action(
            R.drawable.ic_pause_black_24dp,
            "Pause",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PAUSE
            )
        )
    }

    internal val builder by lazy {
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_epimetheus_notification)
            .setContentTitle("Loading...")
            .addAction(
                R.drawable.ic_stop_black_24dp,
                "Stop",
                PendingIntent.getService(
                    context,
                    0,
                    Intent(context, EpimetheusMusicService::class.java).putExtra("close", true),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                R.drawable.ic_fast_rewind_black_24dp,
                "Rewind",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_REWIND
                )
            )
            .addAction(pauseAction)
            .addAction(
                R.drawable.ic_fast_forward_black_24dp,
                "Fast-forward",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_FAST_FORWARD
                )
            )
            .addAction(
                R.drawable.ic_skip_next_black_24dp,
                "Skip",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowCancelButton(false)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
                    .setShowActionsInCompactView(0, 2, 4)
                    .setMediaSession(sessionToken)
            )
    }

    private lateinit var mediaSession: MediaSessionCompat

    internal inline val notification get() = builder.build()

    internal fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = DESCRIPTION
                ContextCompat.getSystemService(context, NotificationManager::class.java)!!.createNotificationChannel(this)
            }
        }
    }

    private fun post() = notificationManager.notify(NOTIFICATION_ID, notification)

    fun attachMediaSession(mediaSession: MediaSessionCompat) {
        this.mediaSession = mediaSession
        this.mediaSession.controller.registerCallback(sessionCallback)
    }

    fun detachMediaSession() {
        if (::mediaSession.isInitialized) mediaSession.controller.unregisterCallback(sessionCallback)
    }

    private val sessionCallback by lazy { SessionCallback() }
    private inner class SessionCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            builder.apply {
                setSubText(metadata?.description?.description)
                setContentTitle(metadata?.description?.title)
                setContentText(metadata?.description?.subtitle)
                setLargeIcon(metadata?.description?.iconBitmap)
            }
            post()
        }

        @SuppressLint("RestrictedApi")
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            when (state.state) {
                PlaybackStateCompat.STATE_PAUSED -> {
                    builder.mActions[2] = playAction
                    post()
                }

                PlaybackStateCompat.STATE_PLAYING -> {
                    builder.mActions[2] = pauseAction
                    post()
                }
            }
        }
    }

    internal companion object {
        internal const val NOTIFICATION_ID = 1
    }
}

internal fun EpimetheusMusicService.startForeground(mediaNotification: MediaNotification) {
    startForeground(MediaNotification.NOTIFICATION_ID, mediaNotification.notification)
}