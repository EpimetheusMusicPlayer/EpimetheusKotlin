package tk.hacker1024.epimetheus.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.*
import tk.hacker1024.libepimetheus.data.Song
import tk.hacker1024.libepimetheus.data.Station
import java.io.IOException

internal const val GENERIC_ART_URL = "https://www.pandora.com/web-version/1.25.1/images/album_500.png"

private const val LOG_TAG = "EpimetheusMediaService" // This tag is used for the media session
private const val MEDIA_NOTIFICATION_CHANNEL_ID = "media" // This is the channel ID for the media notification

internal const val RESULTS_BROADCAST_FILTER = "tk.hacker1024.epimetheus.serviceStateChange"

internal enum class MusicServiceResults(var message: String = "") {
    REQUEST_CLOSE_APP,
    REQUEST_STOP_MUSIC,
    ERROR_INTERNAL,
    ERROR_NETWORK,
    ERROR_PANDORA
}

internal class MusicService : MediaBrowserServiceCompat() {
    // The album art size
    private val artSize
        get() = PreferenceManager.getDefaultSharedPreferences(this).getString(
            "art_size",
            "500"
        )!!.toInt()
    // The thread of the media player
    private lateinit var mediaPlayerHandler: Handler
    // Notification builder - cached to save resources
    private lateinit var mediaNotificationBuilder: NotificationCompat.Builder
    // The media player
    private lateinit var mediaPlayer: SimpleExoPlayer
    private val playerEventListener = PlayerEventListener()
    // The media session
    private lateinit var mediaSession: MediaSessionCompat
    // The playback state builder
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    // The user object, initialized with a serialized object from the intent
    private lateinit var user: User
    // The list of stations
    private data class StationBitmapHolder(val station: Station, var artBitmap: Bitmap? = null, var artUri: Uri? = null)
    private lateinit var stations: List<StationBitmapHolder>
    // The station index
    private var stationIndex = -2
    // The station object
    private val station get() = stations[stationIndex].station
    // The song playlist. Functions added to load and remove songs. Manages an ExoPlayer ConcatenatingMediaSource.
    private val playlist = Playlist()

    override fun onCreate() {
        super.onCreate()

        // Set up the MediaPlayer
        mediaPlayer = ExoPlayerFactory.newSimpleInstance(
            this,
            DefaultRenderersFactory(this),
            DefaultTrackSelector()
        )
        mediaPlayer.addListener(playerEventListener)
        mediaPlayer.playWhenReady = false

        // Initialize the handler
        mediaPlayerHandler = Handler(mediaPlayer.applicationLooper)

        // Set up the MediaSessionCompat
        playbackStateBuilder.setActions(
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
        )
        mediaSession = MediaSessionCompat(this, LOG_TAG)
        mediaSession.setPlaybackState(playbackStateBuilder.build())
        mediaSession.setCallback(Callback())
        sessionToken = mediaSession.sessionToken

        // Register the notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                MEDIA_NOTIFICATION_CHANNEL_ID,
                "Media",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media notifications with controls."
                getSystemService(NotificationManager::class.java).createNotificationChannel(this)
            }
        }

        // Create the media notification
        mediaNotificationBuilder = NotificationCompat.Builder(
            this,
            MEDIA_NOTIFICATION_CHANNEL_ID
        )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_epimetheus_outline)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_stop_black_24dp,
                "Stop",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MusicService::class.java).putExtra("close", true),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                R.drawable.ic_fast_rewind_black_24dp,
                "Rewind",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_REWIND
                )
            )
            .addAction(
                R.drawable.ic_pause_black_24dp,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_PAUSE
                )
            )
            .addAction(
                R.drawable.ic_fast_forward_black_24dp,
                "Fast-forward",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_FAST_FORWARD
                )
            )
            .addAction(
                R.drawable.ic_skip_next_black_24dp,
                "Skip",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(
                MediaStyle()
                    .setShowCancelButton(false)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
                    .setShowActionsInCompactView(0, 2, 4)
                    .setMediaSession(sessionToken)
            )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.getBooleanExtra("close", false)) {
            stop(MusicServiceResults.REQUEST_CLOSE_APP)
            return START_NOT_STICKY
        }

        // Start the service in the foreground
        startForeground(1, mediaNotificationBuilder.build())

        if (!intent.hasExtra("pandoraUserObject") && !::user.isInitialized) {
            stop(MusicServiceResults.ERROR_INTERNAL)
            return START_NOT_STICKY
        }

        if (!intent.hasExtra("stationIndex") && stationIndex == -1) {
            stop(MusicServiceResults.ERROR_INTERNAL)
            return START_NOT_STICKY
        }

        if (!intent.hasExtra("stations") && !::stations.isInitialized) {
            stop(MusicServiceResults.ERROR_INTERNAL)
            return START_NOT_STICKY
        }

        // Get data from the intent. If any of it is different to the current values, load the songs again.
        user = intent.getParcelableExtra<User>("pandoraUserObject").let { newUser ->
            if (::user.isInitialized && user != newUser) {
                mediaPlayer.playWhenReady = false
                playlist.clearSongs()
            }
            newUser
        }

        stationIndex = intent.getIntExtra("stationIndex", -1).let { newIndex ->
            if (stationIndex != newIndex) {
                if (stationIndex != -2) {
                    mediaPlayer.playWhenReady = false
                    playlist.clearSongs()
                }
                mediaSession.setExtras(
                    bundleOf(
                        "stationIndex" to newIndex
                    )
                )
            }
            newIndex
        }

        stations = intent.getParcelableArrayListExtra<Station>("stations").let { newStations ->
            List(newStations.size) { i -> StationBitmapHolder(newStations[i]) }.also {
                if (::stations.isInitialized && station != newStations[stationIndex]) {
                    mediaPlayer.playWhenReady = false
                    playlist.clearSongs()
                }

                if (!::stations.isInitialized || stations != it) {
                    GlobalScope.launch {
                        for (stationHolder in it) {
                            stationHolder.station.getArtUrl(artSize).also { artUrl ->
                                stationHolder.artBitmap = GlideApp
                                    .with(this@MusicService)
                                    .asBitmap()
                                    .load(artUrl)
                                    .submit()
                                    .get()
                                stationHolder.artUri = artUrl.toUri()
                            }
                        }
                    }
                }
            }
        }

        // Set the media notification subtitle
        mediaNotificationBuilder.setSubText(station.name)

        GlobalScope.launch {
            // Get the generic album art icon
            SongBitmapHolder.setGenericBitmap(this@MusicService)
            if (playlist.size == 0) {
                // Start a new song if the playlist is empty
                newSong(false)
            }
        }

        // Activate the media session
        mediaSession.isActive = true

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stop()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot("@stations/", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == "@stations/" && ::stations.isInitialized && stationIndex != -1) {
            result.detach()
            GlobalScope.launch {
                result.sendResult(
                    MutableList(stations.size) { i ->
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId(i.toString())
                                .setTitle(stations[i].station.name)
                                .run {
                                    if (stationIndex == i) {
                                        setSubtitle("Playing now")
                                    } else this
                                }
                                .setIconBitmap(stations[i].artBitmap)
                                .setIconUri(stations[i].artUri)
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    }
                )
            }
        } else {
            result.sendResult(null)
        }
    }

    override fun onLoadItem(itemId: String, result: Result<MediaBrowserCompat.MediaItem>) {
        result.detach()
        GlobalScope.launch {
            result.sendResult(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(itemId)
                        .setTitle(stations[itemId.toInt()].station.name)
                        .run {
                            if (stationIndex == itemId.toInt()) {
                                setSubtitle("Playing now")
                            } else this
                        }
                        .setIconBitmap(stations[itemId.toInt()].artBitmap)
                        .setIconUri(stations[itemId.toInt()].artUri)
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }
    }

    private fun updateNotification() {
        NotificationManagerCompat.from(this@MusicService)
            .notify(1, mediaNotificationBuilder.build())
    }

    private fun newSong(removeFirstSong: Boolean) {
        try {
            mediaPlayerHandler.post { mediaPlayer.playWhenReady = false }
            if (playlist.size == 0) {
                playlist.loadSongs()
                mediaPlayerHandler.post { mediaPlayer.prepare(playlist.mediaSource, true, false) }
            }
            if (removeFirstSong) playlist.removeSong(0)
            setNotificationPauseButton()
            mediaPlayerHandler.post { mediaPlayer.playWhenReady = true }
            updateMetadata()
            if (playlist.size <= 2) {
                GlobalScope.launch { playlist.loadSongs() }
            }
        } catch (e: IOException) {
            stop(MusicServiceResults.ERROR_NETWORK)
        } catch (p: PandoraException) {
            stop(MusicServiceResults.ERROR_PANDORA.apply { message = p.message })
        }
    }

    private fun updateMetadata() {
        mediaPlayerHandler.post {
            try {
                mediaNotificationBuilder
                    .setContentTitle(playlist[0].song.name)
                    .setContentText("${playlist[0].song.artist} - ${playlist[0].song.album}")
                    .setLargeIcon(playlist[0].artBitmap)
                updateNotification()

                mediaSession.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.duration)
                        .putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                            playlist[0].song.name
                        )
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playlist[0].song.name)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, playlist[0].song.album)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playlist[0].song.artist)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, playlist[0].artBitmap)
                        .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, playlist[0].artUri.toString())
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, playlist[0].artBitmap)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, playlist[0].artUri.toString())
                        .build()
                )

                playlist.loadBitmapIfNeeded(0) {
                    mediaNotificationBuilder.setLargeIcon(it)
                    updateNotification()

                    mediaSession.setMetadata(
                        MediaMetadataCompat.Builder(mediaSession.controller.metadata)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, playlist[0].artBitmap)
                            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, playlist[0].artUri.toString())
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, playlist[0].artBitmap)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, playlist[0].artUri.toString())
                            .build()
                    )
                }
            } catch (e: IOException) {
                stop(MusicServiceResults.ERROR_NETWORK)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setNotificationPlayButton() {
        mediaNotificationBuilder.mActions.removeAt(2)
        mediaNotificationBuilder.mActions.add(
            2,
            NotificationCompat.Action(
                R.drawable.ic_play_arrow_black_24dp,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_PLAY
                )
            )
        )
        NotificationManagerCompat.from(this).notify(1, mediaNotificationBuilder.build())
    }

    @SuppressLint("RestrictedApi")
    private fun setNotificationPauseButton() {
        mediaNotificationBuilder.mActions.removeAt(2)
        mediaNotificationBuilder.mActions.add(
            2,
            NotificationCompat.Action(
                R.drawable.ic_pause_black_24dp,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@MusicService,
                    PlaybackStateCompat.ACTION_PAUSE
                )
            )
        )
        NotificationManagerCompat.from(this).notify(1, mediaNotificationBuilder.build())
    }

    private fun stop(error: MusicServiceResults = MusicServiceResults.REQUEST_STOP_MUSIC) {
        mediaPlayerHandler.post {
            mediaPlayer.playWhenReady = false
            mediaSession.isActive = false
            mediaSession.release()
            mediaPlayer.release()
        }
        stopForeground(true)
        stopSelf()
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(RESULTS_BROADCAST_FILTER)
                .putExtra("disconnect", true)
                .putExtra("error", error)
                .putExtra("message", error.message)
        )
    }

    inner class Callback : MediaSessionCompat.Callback() {
        override fun onPause() {
            mediaPlayer.playWhenReady = false
            mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    mediaPlayer.currentPosition,
                    mediaPlayer.playbackParameters.speed
                ).build()
            )
            setNotificationPlayButton()
        }

        override fun onPlay() {
            mediaPlayer.playWhenReady = true
            mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    mediaPlayer.currentPosition,
                    mediaPlayer.playbackParameters.speed
                ).build()
            )
            setNotificationPauseButton()
        }

        override fun onFastForward() {
            mediaPlayer.seekTo(mediaPlayer.currentPosition + 1500)
        }

        override fun onRewind() {
            mediaPlayer.seekTo(mediaPlayer.currentPosition - 1500)
        }

        override fun onSeekTo(pos: Long) {
            mediaPlayer.seekTo(pos)
        }

        override fun onStop() {
            stop()
        }

        override fun onSkipToNext() {
            newSong(true)
        }

        override fun onSkipToQueueItem(id: Long) {
            for (i in 0 until id) {
                playlist.removeSong(0)
            }
            newSong(false)
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
            if (stationIndex != mediaId.toInt()) {
                stationIndex = mediaId.toInt()
                mediaNotificationBuilder.setSubText(station.name)
                playlist.clearSongs()
                GlobalScope.launch { newSong(false) }
            } else {
                mediaPlayer.prepare(playlist.mediaSource, false, false)
            }
        }

        override fun onSetRating(rating: RatingCompat, extras: Bundle) {
            if (extras.containsKey("songIndex")) {
                GlobalScope.launch {
                    try {
                        if (rating.isRated) {
                            playlist[extras.getInt("songIndex")].song.addFeedback(rating.isThumbUp, user)
                        } else {
                            playlist[extras.getInt("songIndex")].song.deleteFeedback(user)
                        }
                        mediaPlayerHandler.post {
                            playerEventListener.onTimelineChanged(
                                mediaPlayer.currentTimeline,
                                null,
                                Player.TIMELINE_CHANGE_REASON_DYNAMIC
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        when (e) {
                            is IOException -> stop(MusicServiceResults.ERROR_NETWORK)
                            is PandoraException -> stop(MusicServiceResults.ERROR_PANDORA)
                            else -> {
                                Log.e(LOG_TAG, e.message, e)
                                stop(MusicServiceResults.ERROR_INTERNAL)
                            }
                        }
                    }
                }
            } else {
                Log.w(LOG_TAG, "Rating set without songIndex in bundle. Not doing anything.")
            }
        }

        override fun onSetRating(rating: RatingCompat?) {
            Log.w(LOG_TAG, "Rating set without songIndex in bundle. Not doing anything.")
        }
    }

    private inner class PlayerEventListener : Player.EventListener {
        override fun onPositionDiscontinuity(reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
                newSong(true)
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            stop(MusicServiceResults.ERROR_NETWORK)
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> playbackStateBuilder.setState(
                    PlaybackStateCompat.STATE_BUFFERING,
                    mediaPlayer.currentPosition,
                    1f
                )

                Player.STATE_READY -> playbackStateBuilder.setState(
                    if (mediaPlayer.playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    mediaPlayer.currentPosition,
                    mediaPlayer.playbackParameters.speed
                )

                Player.STATE_IDLE -> {
                    playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_NONE,
                        mediaPlayer.currentPosition,
                        mediaPlayer.playbackParameters.speed
                    )
                }

                Player.STATE_ENDED -> {
                    playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_NONE,
                        mediaPlayer.currentPosition,
                        mediaPlayer.playbackParameters.speed
                    )
                }
            }
            mediaSession.setPlaybackState(playbackStateBuilder.build())
        }

        override fun onSeekProcessed() {
            mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                    mediaSession.controller.playbackState.state,
                    mediaPlayer.currentPosition,
                    mediaPlayer.playbackParameters.speed
                ).build()
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                    mediaSession.controller.playbackState.state,
                    mediaPlayer.currentPosition,
                    playbackParameters.speed
                ).build()
            )
        }

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
            mediaSession.setQueue(
                List(timeline.windowCount) { i ->
                    if (i <= playlist.size) {
                        playlist[i].run {
                            MediaSessionCompat.QueueItem(
                                MediaDescriptionCompat.Builder()
                                    .setMediaId(i.toString())
                                    .setTitle(song.name)
                                    .setSubtitle(song.artist)
                                    .setDescription(song.album)
                                    .setExtras(
                                        Bundle().apply {
                                            song.settingFeedback.apply {
                                                if (isRated) putBoolean(
                                                    "settingFeedback",
                                                    isThumbUp
                                                )
                                            }
                                            song.rating.apply {
                                                if (isRated) putBoolean("rating", isThumbUp)
                                            }
                                        }
                                    )
                                    .setIconUri(artUri)
                                    .setIconBitmap(artBitmap)
                                    .build(),
                                i.toLong()
                            )
                        }
                    } else {
                        MediaSessionCompat.QueueItem(
                            MediaDescriptionCompat.Builder().build(),
                            0
                        )
                    }
                }
            )
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {}

        override fun onLoadingChanged(isLoading: Boolean) {}
        override fun onRepeatModeChanged(repeatMode: Int) {}
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
    }

    data class SongBitmapHolder(
        internal val song: Song,
        internal var artBitmap: Bitmap = genericBitmap,
        internal var artUri: Uri = genericUri,
        internal var loaded: Boolean = false
    ) {
        companion object {
            internal lateinit var genericBitmap: Bitmap
            val genericUri = GENERIC_ART_URL.toUri()

            internal fun setGenericBitmap(context: Context) {
                genericBitmap = GlideApp
                    .with(context)
                    .asBitmap()
                    .load(GENERIC_ART_URL)
                    .submit()
                    .get()
            }
        }
    }
    internal inner class Playlist : ArrayList<SongBitmapHolder>() {
        internal var mediaSource = ConcatenatingMediaSource()
        private val extractorMediaSourceFactory =
            ExtractorMediaSource.Factory(DefaultHttpDataSourceFactory("khtml"))

        // Loads more songs
        internal fun loadSongs() {
            try {
                station.getPlaylist(user).also { newSongs ->
                    val newMediaSources = List<MediaSource>(newSongs.size) {
                        extractorMediaSourceFactory.createMediaSource(newSongs[it].audioUri)
                    }

                    for (song in newSongs) {
                        add(SongBitmapHolder(song))
                    }
                    mediaSource.addMediaSources(newMediaSources)

                    // Download the album art in a background thread, and update the notification
                    for (i in 0 until newSongs.size) {
                        loadBitmapIfNeeded(size - newSongs.size + i)
                    }
                }
            } catch (e: IOException) {
                stop(MusicServiceResults.ERROR_NETWORK)
            }
        }

        internal fun loadBitmapIfNeeded(index: Int, callback: ((bitmap: Bitmap) -> Unit)? = null) {
            GlobalScope.launch {
                if (index <= lastIndex) {
                    if (!get(index).loaded) {
                        get(index).song.getArtUrl(artSize).also {
                            GlideApp
                                .with(this@MusicService)
                                .asBitmap()
                                .load(it)
                                .submit()
                                .apply {
                                    get(index).apply {
                                        it.toUri().also { newUri ->
                                            get()
                                            if (!loaded) {
                                                artBitmap = get()
                                                artUri = newUri
                                                loaded = true

                                                mediaPlayerHandler.post {
                                                    playerEventListener.onTimelineChanged(
                                                        mediaPlayer.currentTimeline,
                                                        null,
                                                        Player.TIMELINE_CHANGE_REASON_DYNAMIC
                                                    )
                                                }
                                            }
                                            callback?.invoke(artBitmap)
                                        }
                                    }
                                }
                        }
                    } else {
                        callback?.invoke(get(index).artBitmap)
                    }
                }
            }
        }

        internal fun removeSong(index: Int) {
            mediaSource.removeMediaSource(index)
            super.removeAt(index)
        }

        internal fun clearSongs() {
            mediaSource = ConcatenatingMediaSource()
            super.clear()
        }
    }
}
