package tk.hacker1024.epimetheus.service.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.MediaSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.service.INTENT_ID_KEY
import tk.hacker1024.epimetheus.tryNetworkOperation
import tk.hacker1024.libepimetheus.*
import tk.hacker1024.libepimetheus.data.Song
import tk.hacker1024.libepimetheus.data.Station
import tk.hacker1024.libepimetheus.data.search.inline.toTrackHolder

internal class StationProvider(context: Context, user: User, private val stations: List<Station>, private val stationIndex: Int, defaultArtBitmap: Bitmap, callback: Callback) : EpimetheusMediaProvider(context, user, defaultArtBitmap, callback) {
    override val title get() = station.name
    private inline val station get() = stations[stationIndex]

    override val playerEventListener = object : Player.EventListener {
        override fun onPositionDiscontinuity(reason: Int) {
            when (reason) {
                Player.DISCONTINUITY_REASON_PERIOD_TRANSITION -> {
                    GlobalScope.launch {
                        delete(0)
                        if (songHolders.size == 0) callback.mustLoad()
                        callback.playingNew()
                    }
                }
                else -> {}
            }
        }

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
            callback.queueUpdated()
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            GlobalScope.launch {
                delete(0)
                if (songHolders.size == 0) callback.mustLoad()
                callback.mustPrepare()
                callback.playingNew()
            }
        }
    }

    override fun skipTo(index: Long) {
        GlobalScope.launch {
            super.skipTo(index)
            if (songHolders.size == 0) callback.mustLoad()
            callback.playingNew()
        }
    }

    override suspend fun rate(index: Int, rating: RatingCompat) {
        tryNetworkOperation {
            if (rating.isRated) {
                songHolders[index].song.addFeedback(rating.isThumbUp, user)
            } else {
                songHolders[index].song.deleteFeedback(user)
            }
        }
        callback.queueUpdated()
    }

    override suspend fun tired(id: String) {
        songHolders.find { it.song.uniqueId == id }?.apply {
            song.addTired(user)
        }
    }

    override suspend fun load(user: User): Boolean {
        try { station.getPlaylist(user) } catch (e: Exception) { return false }
            .filterNot {
                it.trackType == Song.TrackType.ARTIST_MESSAGE
            }
            .also { loadedSongs ->
                for (song in loadedSongs) {
                    songHolders.add(SongHolder(song))
                }
                concatenatingMediaSource.addMediaSources(
                    List<MediaSource>(loadedSongs.size) {
                        extractorMediaSourceFactory.createMediaSource(loadedSongs[it].audioUri)
                    }
                )
                if (songHolders.size - loadedSongs.size == 0) callback.playingNew()
            }
        return true
    }

    override fun getChildren(parentId: String): List<MediaBrowserCompat.MediaItem> {
        return emptyList() // TODO list stations as children
    }

    override fun getQueue(): List<MediaSessionCompat.QueueItem> {
        return List(songHolders.size) {
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(songHolders[it].song.uniqueId)
                    .setTitle(songHolders[it].song.name)
                    .setSubtitle(songHolders[it].song.artist)
                    .setDescription(songHolders[it].song.album)
                    .setIconUri(if (!songHolders[it].loaded) null else Uri.parse(songHolders[it].getUri()))
                    .setIconBitmap(
                        songHolders[it].getBitmap(context) {
                            callback.queueUpdated()
                        }
                    )
                    .setExtras(
                        Bundle().apply {
                            putSerializable(EXTRAS_TRACK_OBJECT_ARRAY_KEY, songHolders[it].song.toTrackHolder().array)

                            songHolders[it].song.settingFeedback.apply {
                                if (isRated) putBoolean(EXTRAS_RATING_PENDING_KEY, isThumbUp)
                            }

                            songHolders[it].song.rating.apply {
                                if (isRated) putBoolean(EXTRAS_RATING_KEY, isThumbUp)
                            }
                        }
                    )
                    .build(),
                it.toLong()
            )
        }
    }

    internal companion object {
        internal const val INTENT_STATIONS_KEY = "stations"

        internal fun create(context: Context, user: User, intent: Intent, defaultArtBitmap: Bitmap, callback: Callback) =
            if (intent.hasExtra(INTENT_STATIONS_KEY) && intent.hasExtra(INTENT_ID_KEY)) {
                StationProvider(
                    context,
                    user,
                    intent.getParcelableArrayListExtra(INTENT_STATIONS_KEY)!!,
                    intent.getIntExtra(INTENT_ID_KEY, -1),
                    defaultArtBitmap,
                    callback
                )
            } else null
    }
}