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
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
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
    // The media session
    private lateinit var mediaSession: MediaSessionCompat
    // The media session connector
    private lateinit var mediaSessionConnector: MediaSessionConnector
    // The queue navigator
    private lateinit var queueNavigator: QueueNavigator
    // The user object, initialized with a serialized object from the intent
    private lateinit var user: User
    // The list of stations
    private data class StationBitmapHolder(val station: Station, var artBitmap: Bitmap? = null, var artUri: Uri? = null)
    private lateinit var stations: List<StationBitmapHolder>
    // The station index
    private var stationIndex = -1
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
        mediaPlayer.addListener(PlayerEventListener())
        mediaPlayer.playWhenReady = false

        // Initialize the handler
        mediaPlayerHandler = Handler(mediaPlayer.applicationLooper)

        // Set up the MediaSessionCompat
        mediaSession = MediaSessionCompat(this, LOG_TAG)
        mediaSessionConnector = MediaSessionConnector(
            mediaSession,
            MediaSessionConnectorCallback(),
            MediaSessionConnector.DefaultMediaMetadataProvider(mediaSession.controller, null)
        )
        queueNavigator = QueueNavigator(mediaSession)
        mediaSessionConnector.setQueueNavigator(queueNavigator)
        mediaSessionConnector.setPlayer(mediaPlayer, MediaPlaybackPreparer())
        mediaSessionConnector.setRatingCallback(RatingCallback())
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
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
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
                mediaPlayer.playWhenReady = false
                playlist.clearSongs()
            }
            newIndex
        }

        stations = intent.getParcelableArrayListExtra<Station>("stations").let { newStations ->
            List(newStations.size) { i -> StationBitmapHolder(newStations[i]) }.also {
                if (::stations.isInitialized && stations != it) {
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
        try {
            mediaNotificationBuilder
                .setContentTitle(playlist[0].song.name)
                .setContentText("${playlist[0].song.artist} - ${playlist[0].song.album}")
                .setLargeIcon(queueNavigator.getMediaDescription(mediaPlayer, 0).iconBitmap)
            updateNotification()

            // TODO this stuff might not be necessary. Must test on MIUI.
//            val metadataBuilder = MediaMetadataCompat.Builder()
//                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.duration)
//                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playlist[0].songName)
//                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playlist[0].songName)
//                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, playlist[0].albumName)
//                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playlist[0].artistName)
//            mediaSession.setMetadata(metadataBuilder.build())

            GlobalScope.launch {
                try {
//                    val albumArt = Picasso.get().load(playlist[0].albumArtUri).get()
//                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
//                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArt)
//                    mediaSession.setMetadata(metadataBuilder.build())

//                    mediaNotificationBuilder.setLargeIcon(albumArt)
//                    NotificationManagerCompat.from(this@MusicService)
//                        .notify(1, mediaNotificationBuilder.build())
                } catch (e: IllegalArgumentException) {
                } catch (e: IOException) {
                }
            }
        } catch (e: IOException) {
            stop(MusicServiceResults.ERROR_INTERNAL)
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

    private inner class PlayerEventListener : Player.EventListener {
        override fun onPositionDiscontinuity(reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
                newSong(true)
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            stop(MusicServiceResults.ERROR_NETWORK)
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {}
        override fun onSeekProcessed() {}
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {}
        override fun onRepeatModeChanged(repeatMode: Int) {}
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {}
    }

    private inner class MediaSessionConnectorCallback : MediaSessionConnector.PlaybackController {
        override fun getSupportedPlaybackActions(player: Player?) =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_STOP

        override fun onPlay(player: Player) {
            if (!player.playWhenReady) {
                player.playWhenReady = true
                setNotificationPauseButton()
            }
        }

        override fun onPause(player: Player) {
            if (player.playWhenReady) {
                player.playWhenReady = false
                setNotificationPlayButton()
            }
        }

        override fun onRewind(player: Player) {
            player.seekTo(player.currentPosition - 15000)
        }

        override fun onFastForward(player: Player) {
            player.seekTo(player.currentPosition + 15000)
        }

        override fun onStop(player: Player) {
            stop()
        }

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle,
            cb: ResultReceiver
        ) {
            cb.send(0, null)
        }

        override fun onSeekTo(player: Player, pos: Long) {}
        override fun onSetShuffleMode(player: Player?, shuffleMode: Int) {}
        override fun onSetRepeatMode(player: Player?, repeatMode: Int) {}
        override fun getCommands() = null
    }

    private inner class QueueNavigator(mediaSession: MediaSessionCompat) :
        TimelineQueueNavigator(mediaSession) {
        override fun getSupportedQueueNavigatorActions(player: Player?): Long {
            return PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        }

        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            playlist.loadBitmapIfNeeded(windowIndex)
            return MediaDescriptionCompat.Builder()
                .setMediaId(windowIndex.toString())
                // Sometimes, I'll randomly get an IndexOutOfBoundsException. I have no idea why, and I've never seen any "null"s in the notification.
                .run {
                    try {
                        setTitle(playlist[windowIndex].song.name)
                        setSubtitle(playlist[windowIndex].song.artist)
                        setDescription(playlist[windowIndex].song.album)
                    } catch (e: IndexOutOfBoundsException) {
                        this
                    }
                }
                .setExtras(
                    bundleOf(
                        "settingFeedback" to
                                try {
                                    playlist[windowIndex].song.settingFeedback
                                } catch (e: IndexOutOfBoundsException) {
                                    RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                                },
                        "rating" to
                                try {
                                    playlist[windowIndex].song.rating
                                } catch (e: IndexOutOfBoundsException) {
                                    RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                                }
                    )
                )
                .setIconBitmap(try { playlist[windowIndex].artBitmap } catch (e: IndexOutOfBoundsException) { SongBitmapHolder.genericBitmap })
                .setIconUri(try { playlist[windowIndex].artUri } catch (e: IndexOutOfBoundsException) { SongBitmapHolder.genericUri })
                .build()
        }

        override fun onSkipToNext(player: Player?) {
            newSong(true)
        }

        override fun onSkipToQueueItem(player: Player, id: Long) {
            for (i in 0 until id) {
                playlist.removeSong(0)
            }
            newSong(false)
        }
    }

    private inner class MediaPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {
        override fun getSupportedPrepareActions() = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

        override fun onPrepareFromMediaId(mediaId: String, extras: Bundle?) {
            stationIndex = mediaId.toInt()
            mediaNotificationBuilder.setSubText(station.name)
            playlist.clearSongs()
            GlobalScope.launch { newSong(false) }
        }

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle,
            cb: ResultReceiver
        ) {
            cb.send(0, null)
        }

        override fun getCommands() = null
        override fun onPrepareFromSearch(query: String, extras: Bundle?) {}
        override fun onPrepareFromUri(uri: Uri, extras: Bundle?) {}
        override fun onPrepare() {}
    }

    private inner class RatingCallback : MediaSessionConnector.RatingCallback {
        override fun onSetRating(player: Player, rating: RatingCompat, extras: Bundle) {
            if (extras.containsKey("songIndex")) {
                GlobalScope.launch {
                    try {
                        if (rating.isRated) {
                            playlist[extras.getInt("songIndex")].song.addFeedback(rating.isThumbUp, user)
                        } else {
                            playlist[extras.getInt("songIndex")].song.deleteFeedback(user)
                        }
                        mediaPlayerHandler.post {
                            queueNavigator.onTimelineChanged(mediaPlayer)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        when (e) {
                            is IOException -> stop(MusicServiceResults.ERROR_NETWORK)
                            is PandoraException -> stop(MusicServiceResults.ERROR_PANDORA)
                            else -> stop(MusicServiceResults.ERROR_INTERNAL)
                        }
                    }
                }
            } else {
                onSetRating(player, rating)
            }
        }

        override fun onSetRating(player: Player, rating: RatingCompat) {
            GlobalScope.launch {
                try {
                    if (rating.isRated) {
                        playlist[0].song.addFeedback(rating.isThumbUp, user)
                    } else {
                        playlist[0].song.deleteFeedback(user)
                    }
                    mediaPlayerHandler.post {
                        queueNavigator.onTimelineChanged(mediaPlayer)
                    }
                } catch (e: Exception) {
                    when (e) {
                        is IOException -> stop(MusicServiceResults.ERROR_NETWORK)
                        is PandoraException -> stop(MusicServiceResults.ERROR_PANDORA)
                        else -> stop(MusicServiceResults.ERROR_INTERNAL)
                    }
                }
            }
        }

        override fun getCommands() = null

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle,
            cb: ResultReceiver
        ) {
            cb.send(0, null)
        }
    }

    data class SongBitmapHolder(
        internal val song: Song,
        internal var artBitmap: Bitmap = genericBitmap,
        internal var artUri: Uri = genericUri
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

        internal fun loadBitmapIfNeeded(index: Int) {
            if (get(index).artUri == SongBitmapHolder.genericUri) {
                GlobalScope.launch {
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
                                        if (artUri == SongBitmapHolder.genericUri) {
                                            artBitmap = get()
                                            artUri = newUri
                                            mediaPlayerHandler.post {
                                                queueNavigator.onTimelineChanged(mediaPlayer)
                                            }
                                        }
                                    }
                                }

                                if (index == 0) {
                                    GlobalScope.launch {
                                        mediaNotificationBuilder.setLargeIcon(get())
                                        updateNotification()
                                    }
                                }
                            }
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
