package tk.hacker1024.epimetheus.service.data

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.artSize
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.PandoraData
import tk.hacker1024.libepimetheus.data.Song

internal const val GENERIC_ART_URL = "https://www.pandora.com/web-version/1.25.1/images/album_500.png"

internal const val EXTRAS_RATING_KEY = "rating"
internal const val EXTRAS_RATING_PENDING_KEY = "ratingPending"
internal const val EXTRAS_TRACK_OBJECT_ARRAY_KEY = "track"

internal abstract class EpimetheusMediaProvider(protected val context: Context, protected val user: User, protected val defaultArtBitmap: Bitmap, protected val callback: EpimetheusMediaProvider.Callback) {
    /**
     * The title to show in the notification.
     */
    abstract val title: String

    /**
     * The [ConcatenatingMediaSource] to play.
     */
    val concatenatingMediaSource: ConcatenatingMediaSource = ConcatenatingMediaSource()

    /**
     * The [Player.EventListener] to attach.
     */
    abstract val playerEventListener: Player.EventListener

    /**
     * The list of [PandoraData] music items.
     */
    protected val songHolders: MutableList<SongHolder> = mutableListOf()

    /**
     * The song queue.
     */
    abstract fun getQueue(): List<MediaSessionCompat.QueueItem>

    /**
     * Get the metadata for the first song in the playlist.
     */
    open fun getCurrentMetadata(duration: Long): MediaMetadataCompat? {
        if (songHolders.size == 0) return null

        val uri = songHolders[0].getUri()
        val bitmap = songHolders[0].getBitmap(context) {
            callback.iconLoaded(0)
        }
        return MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songHolders[0].song.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, songHolders[0].song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, songHolders[0].song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, songHolders[0].song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, uri)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, uri)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, songHolders[0].song.name)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, songHolders[0].song.artist + " - " + songHolders[0].song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, uri)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            .build()
    }

    /**
     * Skip to the song at the given index.
     */
    open fun skipTo(index: Long) {
        for (i in 0 until index) {
            delete(0)
        }
    }

    /**
     * Skips to the next item.
     */
    open fun skip() = skipTo(1)

    /**
     * Deletes the item at the given index.
     */
    open fun delete(index: Int) {
        concatenatingMediaSource.removeMediaSource(index)
        songHolders.removeAt(index)
    }

    /**
     * Rate the song.
     */
    abstract suspend fun rate(index: Int, rating: RatingCompat)

    /**
     * Shelf the song for a month.
     */
    abstract suspend fun tired(id: String)

    /**
     * ExtractorMediaSource.Factory
     */
    protected val extractorMediaSourceFactory =
        ExtractorMediaSource.Factory(DefaultHttpDataSourceFactory("khtml"))

    /**
     * Loads music for the given user.
     * @return True if successful.
     */
    abstract suspend fun load(user: User): Boolean

    abstract fun getChildren(parentId: String): List<MediaBrowserCompat.MediaItem>

    internal interface Callback {
        fun mustPrepare()
        fun playingNew()
        fun queueUpdated()
        fun iconLoaded(index: Int)
        suspend fun mustLoad()
    }

    internal inner class SongHolder(internal val song: Song, private var uri: String? = null, private var bitmap: Bitmap? = null) {
        internal inline val loading get() = uri != null && bitmap == null
        internal inline val loaded get() = bitmap != null && uri != null

        fun getUri(): String {
            return uri ?: GENERIC_ART_URL
        }

        fun getBitmap(context: Context, callback: (() -> Unit)? = null): Bitmap {
            load(context, callback)
            return bitmap ?: defaultArtBitmap
        }

        fun load(context: Context, callback: (() -> Unit)? = null) {
            if (!(loading || loaded)) {
                uri = song.getArtUrl(context.artSize)
                GlideApp
                    .with(context)
                    .asBitmap()
                    .load(uri)
                    .submit()
                    .apply {
                        GlobalScope.launch {
                            try {
                                bitmap = get()
                                callback?.invoke()
                            } catch (e: Exception) {
                                uri = null
                                bitmap = null
                            }
                        }
                    }
            }
        }
    }
}