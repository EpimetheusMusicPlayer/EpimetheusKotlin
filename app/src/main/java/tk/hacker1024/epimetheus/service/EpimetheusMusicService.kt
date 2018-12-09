package tk.hacker1024.epimetheus.service

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.BuildConfig
import tk.hacker1024.epimetheus.MusicServiceEvent
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.eventBus
import tk.hacker1024.epimetheus.service.data.EpimetheusMediaProvider
import tk.hacker1024.epimetheus.service.data.StationProvider
import tk.hacker1024.epimetheus.service.notifications.MediaNotification
import tk.hacker1024.epimetheus.service.notifications.startForeground
import tk.hacker1024.epimetheus.service.state.StateManager
import tk.hacker1024.libepimetheus.User

private const val SEEK_DURATION_SECONDS = 15 // TODO make setting
private const val SEEK_DURATION_MS = SEEK_DURATION_SECONDS * 1000

private const val LOG_TAG = "Epimetheus - Music Service"
private const val ACTIONS =
    PlaybackStateCompat.ACTION_PLAY or
    PlaybackStateCompat.ACTION_PAUSE or
    PlaybackStateCompat.ACTION_PLAY_PAUSE or
    PlaybackStateCompat.ACTION_STOP or
    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
    PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
    PlaybackStateCompat.ACTION_REWIND or
    PlaybackStateCompat.ACTION_FAST_FORWARD or
    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
    PlaybackStateCompat.ACTION_SET_RATING or
    PlaybackStateCompat.ACTION_SEEK_TO

internal const val INTENT_SESSION_ID_KEY = "sessionId"
internal const val INTENT_USER_KEY = "user"
internal const val INTENT_SOURCE_KEY = "source"
internal const val INTENT_ID_KEY = "id"

internal const val RATING_BUNDLE_INDEX_KEY = "index"
internal const val TIRED_BUNDLE_ID_KEY = "tired_id"

internal const val CUSTOM_ACTION_ADD_TIRED = "tk.hacker1024.epimetheus.addSongTired"

internal class EpimetheusMusicService : MediaBrowserServiceCompat() {
    internal enum class Source(val id: Int) {
        STATION(0);

        companion object {
            internal fun getSourceById(id: Int) =
                when (id) {
                    STATION.id -> STATION
                    else -> throw IllegalArgumentException("Invalid id!")
                }
        }
    }

    private var sessionId = 0 // This ID is used to check if the service has been started with the same source that's currently running.

    private val mediaNotification by lazy {
        MediaNotification(this, sessionToken!!)
    }
    private lateinit var mediaProvider: EpimetheusMediaProvider
    private val mediaPlayer by lazy {
        Log.setLogLevel(if (BuildConfig.DEBUG) Log.LOG_LEVEL_ALL else Log.LOG_LEVEL_OFF)
        ExoPlayerFactory.newSimpleInstance(this)
    }
    private val mediaPlayerHandler by lazy { Handler(mediaPlayer.applicationLooper) }
    private val playbackStateBuilder by lazy { PlaybackStateCompat.Builder() }
    private lateinit var stateManager: StateManager
    private val mediaSession by lazy { MediaSessionCompat(this, LOG_TAG) }
    lateinit var user: User

    private inline val playbackState get() = playbackStateBuilder.build()

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot("/", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mediaProvider.getChildren(parentId))
    }

    /**
     * [onCreate] will do the following things:
     *
     * 1. Initialise the [PlaybackStateCompat.Builder]
     *    Set the playback state actions
     * 2. Initialize the [MediaSessionCompat]
     *    Set the [MediaSessionCompat]'s playback state to the built [PlaybackStateCompat.Builder]
     * 3. Set the [MediaSessionCompat] callback
     * 4. Set the [EpimetheusMusicService] session token
     */
    override fun onCreate() {
        super.onCreate()

        playbackStateBuilder.setActions(ACTIONS)         // 1
        playbackStateBuilder.addCustomAction(tiredAction)
        mediaSession.setPlaybackState(playbackState)     // 2
        mediaSession.setRatingType(RatingCompat.RATING_THUMB_UP_DOWN)
        mediaSession.setCallback(MediaSessionCallback()) // 3
        sessionToken = mediaSession.sessionToken         // 4
    }

    /**
     * [onStartCommand] will do the following things:
     *
     * 1. Retrieve the [User] object
     * 2. Initialise the [EpimetheusMediaProvider]
     * 3. Create the media player
     *    Set the event listeners
     * 4. Create the [StateManager]
     * 5. Create the [MediaNotification] object
     *    Register the notification channel
     *    Attach the media session to the notification
     * 6. Start the [EpimetheusMusicService] in the foreground
     * 7. Start the media playback
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        fun stop(): Int {
            this.stop()
            return START_NOT_STICKY
        }

        if (intent == null || intent.getBooleanExtra("close", false) || !intent.hasExtra(INTENT_SESSION_ID_KEY) || !intent.hasExtra(INTENT_USER_KEY) || !intent.hasExtra(INTENT_SOURCE_KEY)) {
            return stop()
        }

        // 1
        user = intent.getParcelableExtra(INTENT_USER_KEY)!!

        if (sessionId != intent.getIntExtra(INTENT_SESSION_ID_KEY, -1)) {
            sessionId = intent.getIntExtra(INTENT_SESSION_ID_KEY, -2)

            // 2
            if (::mediaProvider.isInitialized) mediaPlayer.removeListener(mediaProvider.playerEventListener)
            mediaProvider =
                    when (intent.getSerializableExtra(INTENT_SOURCE_KEY) as Source) {
                        Source.STATION -> {
                            StationProvider.create(this, user, intent, getDrawable(R.drawable.ic_album_png)!!.toBitmap(), MediaProviderCallback())
                        }
                    } ?: return stop()

            // 3
            mediaPlayer.addListener(mediaProvider.playerEventListener)

            // 4
            if (::stateManager.isInitialized) stateManager.release()
            stateManager = StateManager(playbackStateBuilder, mediaSession, mediaPlayer, mediaProvider)

            // 5
            mediaNotification.registerNotificationChannel()
            mediaNotification.attachMediaSession(mediaSession)

            // 6
            startForeground(mediaNotification)

            // 7
            mediaSession.isActive = true
            if (intent.hasExtra("id")) {
                mediaSession.setExtras(
                    bundleOf(
                        INTENT_ID_KEY to intent.getIntExtra("id", -1)
                    )
                )
            }
            GlobalScope.launch {
                if (!mediaProvider.load(user)) stop() else {
                    mediaPlayerHandler.post {
                        mediaPlayer.prepare(mediaProvider.concatenatingMediaSource)
                        mediaPlayer.playWhenReady = true
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaNotification.detachMediaSession()
        if (::stateManager.isInitialized) stateManager.release()
        if (::mediaProvider.isInitialized) mediaPlayer.removeListener(mediaProvider.playerEventListener)
        mediaSession.release()
        mediaPlayer.release()
    }

    private fun stop() {
        eventBus.post(MusicServiceEvent(disconnect = true))
        stopForeground(true)
        stopSelf()
    }

    private inner class MediaProviderCallback : EpimetheusMediaProvider.Callback {
        override fun mustPrepare() {
            mediaPlayerHandler.post {
                mediaPlayer.prepare(mediaProvider.concatenatingMediaSource, true, false)
            }
        }

        override fun playingNew() {
            stateManager.updateMetadata(0) // TODO this may need to be posted on the main thread. Remove TODO 16/12/2018.
            mediaPlayerHandler.post {
                mediaPlayer.playWhenReady = true
            }
            if (mediaProvider.concatenatingMediaSource.size <= 2) {
                GlobalScope.launch {
                    if (!mediaProvider.load(user)) queueUpdated()
                }
            }
        }

        override fun queueUpdated() = mediaSession.setQueue(mediaProvider.getQueue())

        override fun iconLoaded(index: Int) {
            if (index == 0) {
                mediaPlayerHandler.post {
                    stateManager.updateMetadata()
                }
            }
        }

        override suspend fun mustLoad() {
            if (!mediaProvider.load(user)) stop()
        }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPause() {
            mediaPlayer.playWhenReady = false
        }

        override fun onPlay() {
            mediaPlayer.playWhenReady = true
        }

        override fun onFastForward() =
            mediaPlayer.seekTo(mediaPlayer.currentPosition + SEEK_DURATION_MS)

        override fun onRewind() =
            mediaPlayer.seekTo(mediaPlayer.currentPosition - SEEK_DURATION_MS)

        override fun onStop() = stop()

        override fun onSkipToNext() = mediaProvider.skip()

        override fun onSkipToQueueItem(id: Long) = mediaProvider.skipTo(id)

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            // TODO
        }

        override fun onSetRating(rating: RatingCompat, extras: Bundle) {
            if (extras.containsKey(RATING_BUNDLE_INDEX_KEY)) {
                GlobalScope.launch {
                    mediaProvider.rate(extras.getInt(RATING_BUNDLE_INDEX_KEY), rating)
                }
            }
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            when (action) {
                CUSTOM_ACTION_ADD_TIRED -> {
                    if (extras?.containsKey(TIRED_BUNDLE_ID_KEY) == true) {
                        GlobalScope.launch {
                            mediaProvider.tired(extras.getString(TIRED_BUNDLE_ID_KEY)!!)
                        }
                    }
                }
            }
        }
    }

    companion object {
        internal val tiredAction = PlaybackStateCompat.CustomAction.Builder(
            CUSTOM_ACTION_ADD_TIRED,
            "I'm tired of this song",
            R.drawable.ic_remove_thumb_black_24dp
        ).build()!!
    }
}