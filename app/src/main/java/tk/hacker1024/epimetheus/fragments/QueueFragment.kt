package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.*
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_playlist.*
import kotlinx.android.synthetic.main.song_card_inactive.view.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import tk.hacker1024.epimetheus.*
import tk.hacker1024.epimetheus.service.*
import tk.hacker1024.epimetheus.service.data.EXTRAS_RATING_KEY
import tk.hacker1024.epimetheus.service.data.EXTRAS_RATING_PENDING_KEY
import tk.hacker1024.epimetheus.service.data.EXTRAS_TRACK_OBJECT_ARRAY_KEY
import tk.hacker1024.epimetheus.service.data.StationProvider
import tk.hacker1024.libepimetheus.data.search.inline.TrackHolder
import java.io.Serializable
import kotlin.math.max

/**
 * This fragment needs to do the following things:
 *
 * If (start argument = true):
 *     - Start the music service, with the given source
 *     - Display the queue
 *     x Show the playing song duration and position
 *     - Dynamically update the colours
 *
 * If accessed from the navigation drawer or media control widget (start argument = false):
 *     - Check if anything's playing
 *     - Display the queue
 *     x Show the playing song duration and position
 *     - Dynamically update the colours
 */

class QueueFragment : Fragment() {
    private val viewModel by lazy {
        ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]
    }
    private var mediaController: MediaControllerCompat? = null

    private val playingId by lazy { if (arguments!!.getBoolean("start")) arguments!!.getInt(INTENT_ID_KEY) else mediaController?.extras?.getInt(INTENT_ID_KEY, 0) ?: 0 }

    lateinit var mQueueRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_playlist, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Start the music service, if appropriate.
        QueueFragmentArgs.fromBundle(arguments).also { arguments ->
            if (arguments.start) {
                val intent = Intent(requireContext(), EpimetheusMusicService::class.java)
                intent.putExtra(INTENT_USER_KEY, viewModel.user.value)
                val source = EpimetheusMusicService.Source.getSourceById(arguments.source)
                when (source) {
                    EpimetheusMusicService.Source.STATION -> {
                        intent.putParcelableArrayListExtra(
                            StationProvider.INTENT_STATIONS_KEY,
                            viewModel.getStationList().value!!
                        )
                        intent.putExtra(INTENT_SESSION_ID_KEY, viewModel.getStationList().value!![arguments.id].hashCode())
                    }
                }
                intent.putExtra(INTENT_SOURCE_KEY, source)
                intent.putExtra(INTENT_ID_KEY, arguments.id)

                ContextCompat.startForegroundService(requireContext(), intent)
            }
        }

        // Initialize the RecyclerView
        mQueueRecyclerView = queue_recyclerview
        mQueueRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = QueueAdapter()

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        (requireActivity() as MainActivity).toolbar_layout.elevation =
                                if ((layoutManager as LinearLayoutManager)
                                        .findFirstCompletelyVisibleItemPosition() == 0) 0f else 10.8f
                    }
                }
            )

            adapter!!.registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    private fun color(nextFirst: Int) {
                        (findViewHolderForAdapterPosition(nextFirst) as QueueAdapter.ViewHolder?)?.apply {
                            colorSongStatic(0)
                            colorSongDynamic(mediaController!!.queue[0].description.iconBitmap!!)
                        }
                    }

                    override fun onChanged() {
                        menu?.close()
                        menu = null
                    }

                    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                        menu?.close()
                        menu = null
                    }

                    override fun onItemRangeChanged(
                        positionStart: Int,
                        itemCount: Int,
                        payload: Any?
                    ) {
                        menu?.close()
                        menu = null
                    }

                    override fun onItemRangeMoved(
                        fromPosition: Int,
                        toPosition: Int,
                        itemCount: Int
                    ) {
                        menu?.close()
                        menu = null

                        if (0 in toPosition until toPosition + itemCount union (fromPosition until fromPosition + itemCount)) {
                            color(max(toPosition + itemCount, fromPosition + itemCount))
                        }
                    }

                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        menu?.close()
                        menu = null

                        if (0 in positionStart until positionStart + itemCount) {
                            color(positionStart + itemCount)
                        }
                    }

                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                        menu?.close()
                        menu = null

                        if (0 in positionStart until positionStart + itemCount) {
                            color(positionStart + itemCount)
                        }
                    }
                }
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
        inflater.inflate(R.menu.playlist_menu, menu)

    override fun onStart() {
        super.onStart()

        eventBus.register(this)

        if (arguments!!.getBoolean("start")) {
            (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).show()
        }

        (requireActivity() as MainActivity).connectMediaBrowser {
            mediaController = MediaControllerCompat.getMediaController(requireActivity())
            setHasOptionsMenu(playingId >= 1)
            mediaController!!.registerCallback(controllerCallback)
            mediaController!!.queue?.also {
                controllerCallback.onQueueChanged(it)
            }
            (mQueueRecyclerView.findViewHolderForAdapterPosition(0) as QueueAdapter.ViewHolder?)?.apply {
                colorSongStatic(0)
                colorSongDynamic(mediaController!!.queue[0].description.iconBitmap!!)
            }
            progressUpdater.start()
        }
    }

    override fun onStop() {
        super.onStop()

        eventBus.unregister(this)

        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.feedback -> {
                if (playingId >= 1) {
                    findNavController().navigate(
                        QueueFragmentDirections.actionQueueFragmentToFeedbackFragment(playingId)
                    )
                }
                true
            }
            else -> false
        }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>) {
            (mQueueRecyclerView.adapter as QueueAdapter).submitList(
                List(queue.size) {
                    queue[it].description
                }
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceEvent(event: MusicServiceEvent) {
        if (event.disconnect) {
            findNavController().navigateUp()
        }
    }

    private val progressUpdater = object : Runnable {
        private val progressHandler = Handler()

        val position; get() = mediaController!!.playbackState?.position ?: 0L
        val positionString; get() =
            if (duration <= 0L) "--" else (position).run {
                (if (this < 0L) 0 else this / 1000).run {
                    "${this / 60}:${String.format("%02d", this % 60)}"
                }
            }

        val duration; get() = mediaController!!.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        val durationString; get() =
            if (duration <= 0L) "--" else (duration / 1000).run {
                "${this / 60}:${String.format("%02d", this % 60)}"
            }

        @SuppressLint("SetTextI18n")
        override fun run() {
            if (mediaController != null) {
                (mQueueRecyclerView.findViewHolderForAdapterPosition(0) as? QueueAdapter.ViewHolder)
                    ?.mProgress?.text = "$positionString / $durationString"
                progressHandler.postDelayed(this, 250)
            }
        }

        fun start() = progressHandler.post(this)
    }

    private var menu: Menu? = null
    private inner class QueueAdapter : ListAdapter<MediaDescriptionCompat, QueueAdapter.ViewHolder>(QueueComparatorCallback()) {
        inner class ViewHolder(private val songLayout: LinearLayout) : RecyclerView.ViewHolder(songLayout), View.OnCreateContextMenuListener {
            private val mSongAlbumArt = songLayout.song_album_art
            private val mSongTitle = songLayout.song_title
            private val mSongArtist = songLayout.song_artist
            private val mSongAlbum = songLayout.song_album
            private val mBanProgress = songLayout.ban_progress
            private val mLoveProgress = songLayout.love_progress
            private val mBanThumb = songLayout.ban_thumb
            private val mLoveThumb = songLayout.love_thumb
            private val mPlaySong = songLayout.play_song
            internal val mProgress = songLayout.progress

            private var rating = RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
            private var ratingLoading = RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)

            fun colorSongStatic(position: Int) {
                @ColorInt val foregroundColor: Int
                @ColorInt val backgroundColor: Int
                if (position == 0) {
                    foregroundColor = 0xFF_FF_FF_FF.toInt()
                    backgroundColor = 0x8A_00_00_00.toInt()

                    mPlaySong.visibility = View.GONE
                    mProgress.visibility = View.VISIBLE
                } else {
                    foregroundColor = 0x8A_00_00_00.toInt()
                    backgroundColor = 0x00_00_00_00

                    mProgress.visibility = View.GONE
                    mPlaySong.visibility = View.VISIBLE
                }

                songLayout.setBackgroundColor(backgroundColor)
                mSongTitle.setTextColor(foregroundColor)
                mSongArtist.setTextColor(foregroundColor)
                mSongAlbum.setTextColor(foregroundColor)
                mLoveProgress.indeterminateDrawable.setTint(foregroundColor)
                mBanProgress.indeterminateDrawable.setTint(foregroundColor)
                mLoveThumb.setColorFilter(foregroundColor)
                mBanThumb.setColorFilter(foregroundColor)
                mPlaySong.setColorFilter(foregroundColor)
                mProgress.setTextColor(foregroundColor)
            }

            fun setLabels(mediaDescription: MediaDescriptionCompat) {
                mSongTitle.text = mediaDescription.title
                mSongArtist.text = mediaDescription.subtitle
                mSongAlbum.text = mediaDescription.description
            }

            fun setRatings(position: Int, mediaDescription: MediaDescriptionCompat) {
                rating =
                        if (mediaDescription.extras!!.containsKey(EXTRAS_RATING_KEY)) {
                            RatingCompat.newThumbRating(mediaDescription.extras!!.getBoolean(EXTRAS_RATING_KEY))
                        } else {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        }

                ratingLoading =
                        if (mediaDescription.extras!!.containsKey(EXTRAS_RATING_PENDING_KEY)) {
                            RatingCompat.newThumbRating(mediaDescription.extras!!.getBoolean(EXTRAS_RATING_PENDING_KEY))
                        } else {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        }

                setRatingColors(position)
                setRatingVisibility()
            }

            private fun setRatingColors(position: Int) {
                @ColorInt val unselectedColor: Int =
                    if (position == 0) {
                        0xFF_FF_FF_FF.toInt()
                    } else {
                        0x8A_00_00_00.toInt()
                    }

                if (!ratingLoading.isRated) {
                    if (rating.isRated) {
                        if (rating.isThumbUp) {
                            mBanThumb.setColorFilter(unselectedColor)
                            mLoveThumb.setColorFilter(Color.BLUE)
                        } else {
                            mLoveThumb.setColorFilter(unselectedColor)
                            mBanThumb.setColorFilter(Color.BLUE)
                        }
                    } else {
                        mLoveThumb.setColorFilter(unselectedColor)
                        mBanThumb.setColorFilter(unselectedColor)
                    }
                }
            }

            private fun setRatingVisibility() {
                if (ratingLoading.isRated) {
                    if (ratingLoading.isThumbUp) {
                        mLoveThumb.visibility = View.GONE
                        mLoveProgress.visibility = View.VISIBLE
                    } else {
                        mBanThumb.visibility = View.GONE
                        mBanProgress.visibility = View.VISIBLE
                    }
                } else {
                    mLoveProgress.visibility = View.GONE
                    mBanProgress.visibility = View.GONE
                    mLoveThumb.visibility = View.VISIBLE
                    mBanThumb.visibility = View.VISIBLE
                }
            }

            fun setAlbumArt(mediaDescription: MediaDescriptionCompat, animate: Boolean, colorDynamic: Boolean) {
                if (animate) {
                    GlideApp
                        .with(requireContext())
                        .load(mediaDescription.iconBitmap)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(mSongAlbumArt.drawable)
                        .into(mSongAlbumArt)
                } else {
                    GlideApp
                        .with(requireContext())
                        .asBitmap()
                        .load(mediaDescription.iconBitmap)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .placeholder(R.drawable.ic_album_png)
                        .into(mSongAlbumArt)
                }
                mediaDescription.iconBitmap?.also { if (colorDynamic) colorSongDynamic(it) }
            }

            fun colorSongDynamic(bitmap: Bitmap) {
                Palette.Builder(bitmap).generate { palette: Palette? ->
                    if (palette != null) {
                        songLayout.setBackgroundColor(palette.getDarkVibrantColor(Color.DKGRAY))
                        mSongTitle.setTextColor(palette.getLightVibrantColor(Color.WHITE))

                        palette.getLightMutedColor(Color.WHITE).also { lightMutedColor ->
                            mSongArtist.setTextColor(lightMutedColor)
                            mSongAlbum.setTextColor(lightMutedColor)
                        }
                    }
                }
            }

            init {
                songLayout.setOnClickListener {
                    @Suppress("UNCHECKED_CAST")
                    findNavController().navigate(
                        QueueFragmentDirections.songDetails(
                            TrackHolder(
                                getItem(adapterPosition).extras!!.getSerializable(
                                    EXTRAS_TRACK_OBJECT_ARRAY_KEY
                                ) as Array<Serializable>
                            ).track
                        )
                    )
                }

                mLoveThumb.setOnClickListener {
                    it.visibility = View.GONE
                    mLoveProgress.visibility = View.VISIBLE
                    mediaController!!.transportControls.setRating(
                        if (rating.isRated && rating.isThumbUp) {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        } else {
                            RatingCompat.newThumbRating(true)
                        },
                        bundleOf(
                            RATING_BUNDLE_INDEX_KEY to adapterPosition
                        )
                    )
                }

                mBanThumb.setOnClickListener {
                    it.visibility = View.GONE
                    mBanProgress.visibility = View.VISIBLE
                    mediaController!!.transportControls.setRating(
                        if (rating.isRated && !rating.isThumbUp) {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        } else {
                            RatingCompat.newThumbRating(false)
                        },
                        bundleOf(
                            RATING_BUNDLE_INDEX_KEY to adapterPosition
                        )
                    )
                }

                mPlaySong.setOnClickListener {
                    mediaController!!.transportControls.skipToQueueItem(adapterPosition.toLong())
                }

                songLayout.setOnCreateContextMenuListener(this)
            }

            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                requireActivity().menuInflater.inflate(R.menu.song_menu, menu)
                menu.setHeaderTitle(mSongTitle.text)

                if (adapterPosition == 0) {
                    menu.removeItem(R.id.play)
                } else {
                    menu.findItem(R.id.play).setOnMenuItemClickListener {
                        mPlaySong.callOnClick()
                        this@QueueFragment.menu = null
                        true
                    }
                }

                menu.findItem(R.id.tired).setOnMenuItemClickListener {
                    mediaController!!.transportControls.sendCustomAction(
                        CUSTOM_ACTION_ADD_TIRED,
                        bundleOf(
                            TIRED_BUNDLE_ID_KEY to getItem(adapterPosition).mediaId
                        )
                    )
                    this@QueueFragment.menu = null
                    true
                }

                this@QueueFragment.menu = menu
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.song_card_inactive,
                        parent,
                        false
                    ) as LinearLayout
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.colorSongStatic(position)
            getItem(position).also { description ->
                holder.setLabels(description)
                holder.setAlbumArt(description, false, position == 0)
                holder.setRatings(position, description)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                (payloads[0] as Map<String, Boolean>).apply {
                    if (get(QueueComparatorCallback.PAYLOAD_BUNDLE_KEY_FIRST) == true) {
                        holder.colorSongStatic(position)
                    }
                    if (get(QueueComparatorCallback.PAYLOAD_BUNDLE_KEY_RATING) == true) {
                        holder.setRatings(position, getItem(position))
                    }
                    if (get(QueueComparatorCallback.PAYLOAD_BUNDLE_KEY_ART) == true) {
                        holder.setAlbumArt(getItem(position), true, position == 0)
                    }
                }
            } else onBindViewHolder(holder, position)
        }
    }
}

/**
 * ItemCallback for the queue ListAdapter.
 */
private class QueueComparatorCallback : DiffUtil.ItemCallback<MediaDescriptionCompat>() {
    override fun areItemsTheSame(oldItem: MediaDescriptionCompat, newItem: MediaDescriptionCompat) =
        oldItem.mediaId == newItem.mediaId

    override fun areContentsTheSame(oldItem: MediaDescriptionCompat, newItem: MediaDescriptionCompat) =
        oldItem.iconUri == newItem.iconUri &&
        oldItem.extras!!.containsKey(EXTRAS_RATING_KEY) == newItem.extras!!.containsKey(EXTRAS_RATING_KEY) &&
        oldItem.extras!!.containsKey(EXTRAS_RATING_PENDING_KEY) == newItem.extras!!.containsKey(EXTRAS_RATING_PENDING_KEY) &&
        oldItem.extras!!.getBoolean(EXTRAS_RATING_KEY) == newItem.extras!!.getBoolean(EXTRAS_RATING_KEY) &&
        oldItem.extras!!.getBoolean(EXTRAS_RATING_PENDING_KEY) == newItem.extras!!.getBoolean(EXTRAS_RATING_PENDING_KEY)

    override fun getChangePayload(oldItem: MediaDescriptionCompat, newItem: MediaDescriptionCompat) =
        mapOf(
            PAYLOAD_BUNDLE_KEY_ART to (oldItem.iconUri != newItem.iconUri),
            PAYLOAD_BUNDLE_KEY_RATING to (
                        oldItem.extras!!.containsKey(EXTRAS_RATING_KEY) != newItem.extras!!.containsKey(EXTRAS_RATING_KEY) ||
                        oldItem.extras!!.containsKey(EXTRAS_RATING_PENDING_KEY) != newItem.extras!!.containsKey(EXTRAS_RATING_PENDING_KEY) ||
                        oldItem.extras!!.getBoolean(EXTRAS_RATING_KEY) != newItem.extras!!.getBoolean(EXTRAS_RATING_KEY) ||
                        oldItem.extras!!.getBoolean(EXTRAS_RATING_PENDING_KEY) != newItem.extras!!.getBoolean(EXTRAS_RATING_PENDING_KEY)
                    )
        )

    companion object {
        internal const val PAYLOAD_BUNDLE_KEY_FIRST = "first"
        internal const val PAYLOAD_BUNDLE_KEY_RATING = EXTRAS_RATING_KEY
        internal const val PAYLOAD_BUNDLE_KEY_ART = "art"
    }
}