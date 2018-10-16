package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.get
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_media_control.view.*
import kotlinx.android.synthetic.main.fragment_playlist.*
import kotlinx.android.synthetic.main.fragment_playlist.view.*
import kotlinx.android.synthetic.main.song_card_inactive.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.MusicService

private const val ALBUM_ART_CORNER_RADIUS = 24

class PlaylistFragment : Fragment() {
    internal lateinit var viewModel: EpimetheusViewModel
    private var mediaController: MediaControllerCompat? = null
    private var cachedSize = 0

    @ColorInt private var textColorActive: Int = 0xFF_FF_FF_FF.toInt()
    @ColorInt private var textColorInactive: Int = 0x8A_00_00_00.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]

        requireContext().obtainStyledAttributes(
            intArrayOf(
                android.R.attr.textColorPrimaryInverse,
                android.R.attr.textColorSecondary
            )
        ).apply {
            @SuppressLint("ResourceType")
            textColorActive = getColor(0, 0xFF_FF_FF_FF.toInt())
            @SuppressLint("ResourceType")
            textColorInactive = getColor(1, 0x8A_00_00_00.toInt())
            recycle()
        }

        if (arguments!!.getBoolean("start")) {
            findNavController().graph[R.id.playlistFragment].setDefaultArguments(
                bundleOf(
                    "stationIndex" to arguments!!.getInt("stationIndex"),
                    "stations" to null,
                    "start" to false
                )
            )

            ContextCompat.startForegroundService(
                requireContext(),
                Intent(context, MusicService::class.java)
                    .putExtra("pandoraUserObject", viewModel.user.value!!)
                    .putExtra("stationIndex", arguments!!.getInt("stationIndex"))
                    .putParcelableArrayListExtra("stations", arguments!!.getParcelableArrayList("stations"))
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_playlist, container, false).apply {
            song_list.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = SongRecyclerAdapter()

                addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            requireActivity().toolbar_layout.elevation =
                                    if ((song_list.layoutManager!! as LinearLayoutManager)
                                            .findFirstCompletelyVisibleItemPosition() == 0) 0f else 10.8f
                        }
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!arguments!!.getBoolean("start") && !MediaControlFragment.isServiceRunning(requireContext())) {
            view.loading_widget.visibility = View.GONE
            view.empty.visibility = View.VISIBLE
            view.empty.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (!arguments!!.getBoolean("start")) {
            (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).showIfServiceRunning()
        } else {
            (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).show()
        }

        view?.song_list?.visibility = View.GONE
        view?.loading_widget?.visibility = View.VISIBLE
        (requireActivity() as MainActivity).connectMediaBrowser {
            mediaController = MediaControllerCompat.getMediaController(requireActivity())
            mediaController!!.registerCallback(controllerCallback)
            mediaController!!.queue?.apply { controllerCallback.onQueueChanged(this) }
            controllerCallback.handler.post(controllerCallback.updateProgress)
        }
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null
    }

    override fun onDestroy() {
        requireActivity().toolbar_layout.elevation = 10.8f
        super.onDestroy()
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        private var oldQueue: List<MediaSessionCompat.QueueItem> = emptyList()

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>) {
            if (mediaController?.extras?.getInt("stationIndex") == arguments!!.getInt("stationIndex")) cachedSize = queue.size
            if (queue.isEmpty() || mediaController?.extras?.getInt("stationIndex") != arguments!!.getInt("stationIndex")) {
                view?.song_list?.visibility = View.GONE
                view?.loading_widget?.visibility = View.VISIBLE
            } else {
                view?.loading_widget?.visibility = View.GONE
                view?.song_list?.visibility = View.VISIBLE
            }
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                        oldQueue[oldItemPosition].description.iconUri == queue[newItemPosition].description.iconUri &&
                        oldQueue[oldItemPosition].description.extras!!.containsKey("rating") == queue[newItemPosition].description.extras!!.containsKey("rating") &&
                        oldQueue[oldItemPosition].description.extras!!.containsKey("settingFeedback") == queue[newItemPosition].description.extras!!.containsKey("settingFeedback") &&
                        oldQueue[oldItemPosition].description.extras!!.getBoolean("rating") == queue[newItemPosition].description.extras!!.getBoolean("rating") &&
                        oldQueue[oldItemPosition].description.extras!!.getBoolean("settingFeedback") == queue[newItemPosition].description.extras!!.getBoolean("settingFeedback")

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                        oldQueue[oldItemPosition].description.title == queue[newItemPosition].description.title &&
                        oldQueue[oldItemPosition].description.description == queue[newItemPosition].description.description &&
                        oldQueue[oldItemPosition].description.subtitle == queue[newItemPosition].description.subtitle

                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Map<String, Boolean> {
                        return mapOf(
                            "art" to (oldQueue[oldItemPosition].description?.iconUri != queue[newItemPosition].description.iconUri),
                            "rating" to (
                                oldQueue[oldItemPosition].description.extras!!.containsKey("rating") != queue[newItemPosition].description.extras!!.containsKey("rating") ||
                                oldQueue[oldItemPosition].description.extras!!.containsKey("settingFeedback") != queue[newItemPosition].description.extras!!.containsKey("settingFeedback") ||
                                oldQueue[oldItemPosition].description.extras!!.getBoolean("rating") != queue[newItemPosition].description.extras!!.getBoolean("rating") ||
                                oldQueue[oldItemPosition].description.extras!!.getBoolean("settingFeedback") != queue[newItemPosition].description.extras!!.getBoolean("settingFeedback")
                            )
                        )
                    }

                    override fun getOldListSize() = oldQueue.size

                    override fun getNewListSize() = cachedSize
                },
                false
            ).apply {
                view?.song_list?.adapter.also {
                    if (it != null) dispatchUpdatesTo(it)
                    oldQueue = queue
                    (song_list.findViewHolderForAdapterPosition(0) as ViewHolder?)?.apply {
                        colorSong(
                            true,
                            if (queueItemDescription.extras!!.containsKey("rating"))
                                queueItemDescription.extras!!.getBoolean("rating")
                            else null
                        )
                    }
                }
            }
        }

        val handler = Handler()
        val updateProgress = object : Runnable {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (mediaController != null) {
                    val duration =
                        mediaController?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0

                    val positionString = if (duration <= 0L) "--" else (mediaController?.playbackState?.position ?: 0L).run {
                        (if (this < 0L) 0 else this / 1000).run {
                            "${this / 60}:${String.format("%02d", this % 60)}"
                        }
                    }
                    val durationString = if (duration <= 0L) "--" else (duration / 1000).run {
                        "${this / 60}:${String.format("%02d", this % 60)}"
                    }

                    (view?.song_list?.findViewHolderForAdapterPosition(0) as? ViewHolder)
                        ?.songCard?.progress?.text = "$positionString / $durationString"
                    handler.postDelayed(this, 200)
                }
            }
        }
    }

    var menu: Menu? = null
    @SuppressLint("ClickableViewAccessibility")
    private inner class ViewHolder(internal val songCard: LinearLayout) : RecyclerView.ViewHolder(songCard), View.OnCreateContextMenuListener {
        lateinit var queueItemDescription: MediaDescriptionCompat

        internal fun colorDynamic() {
            Palette.Builder(queueItemDescription.iconBitmap!!).generate().apply {
                val darkVibrant = getDarkVibrantColor(Color.BLACK)
                val darkMuted = getDarkMutedColor(Color.DKGRAY)
                val lightVibrant = getLightVibrantColor(Color.WHITE)
                val lightMuted = getLightMutedColor(lightVibrant)

                viewModel.apply {
                    this.darkVibrant.postValue(darkVibrant)
                    this.darkMuted.postValue(darkMuted)
                    this.lightVibrant.postValue(lightVibrant)
                    this.lightMuted.postValue(lightMuted)
                }

                activity?.runOnUiThread {
                    songCard.setBackgroundColor(darkVibrant)
                    songCard.song_title.setTextColor(lightVibrant)
                    songCard.song_artist.setTextColor(lightMuted)
                    songCard.song_album.setTextColor(lightMuted)
                }
            }
        }

        @Suppress("DEPRECATION")
        internal fun colorSong(active: Boolean, rating: Boolean?) {
            if (active) {
                GlobalScope.launch {
                    colorDynamic()
                }
                songCard.ban_progress.indeterminateDrawable.setTint(textColorActive)
                songCard.love_progress.indeterminateDrawable.setTint(textColorActive)
                songCard.ban_thumb.setColorFilter(
                    if (rating == false) 0xFF_FF_FF_00.toInt() else textColorActive
                )
                songCard.love_thumb.setColorFilter(
                    if (rating == true) 0xFF_FF_FF_00.toInt() else textColorActive
                )
                songCard.play_song.visibility = View.GONE
                songCard.progress.visibility = View.VISIBLE
            } else {
                songCard.setBackgroundColor(Color.TRANSPARENT)
                songCard.song_title.setTextColor(textColorInactive)
                songCard.song_artist.setTextColor(textColorInactive)
                songCard.song_album.setTextColor(textColorInactive)
                songCard.ban_progress.indeterminateDrawable.setTint(textColorInactive)
                songCard.love_progress.indeterminateDrawable.setTint(textColorInactive)
                songCard.ban_thumb.setColorFilter(
                    if (rating == false) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                    }
                    else textColorInactive
                )
                songCard.love_thumb.setColorFilter(
                    if (rating == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                    }
                    else textColorInactive
                )
                songCard.play_song.setColorFilter(textColorInactive)
                songCard.progress.visibility = View.GONE
                songCard.play_song.visibility = View.VISIBLE
            }
        }

        init {
            songCard.setOnCreateContextMenuListener(this)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                songCard.setOnLongClickListener {
                    showPopupMenu()
                    true
                }
            }

            songCard.play_song.setOnClickListener {
                menuPlay()
            }

            songCard.ban_thumb.setOnClickListener {
                it.visibility = View.GONE
                songCard.ban_progress.visibility = View.VISIBLE
                if (queueItemDescription.extras!!.containsKey("rating") && !queueItemDescription.extras!!.getBoolean("rating")) {
                    mediaController!!.transportControls!!.setRating(
                        RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN),
                        bundleOf(
                            "songIndex" to adapterPosition
                        )
                    )
                } else {
                    mediaController!!.transportControls!!.setRating(
                        RatingCompat.newThumbRating(false),
                        bundleOf(
                            "songIndex" to adapterPosition
                        )
                    )
                }
            }

            songCard.love_thumb.setOnClickListener {
                it.visibility = View.GONE
                songCard.love_progress.visibility = View.VISIBLE
                if (queueItemDescription.extras!!.containsKey("rating") && queueItemDescription.extras!!.getBoolean("rating")) {
                    mediaController!!.transportControls!!.setRating(
                        RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN),
                        bundleOf(
                            "songIndex" to adapterPosition
                        )
                    )
                } else {
                    mediaController!!.transportControls!!.setRating(
                        RatingCompat.newThumbRating(true),
                        bundleOf(
                            "songIndex" to adapterPosition
                        )
                    )
                }
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            requireActivity().menuInflater.inflate(R.menu.song_menu, menu)
            menu.setHeaderTitle(songCard.song_title.text)
            if (adapterPosition == 0) {
                menu.removeItem(R.id.play)
            } else {
                menu.findItem(R.id.play).setOnMenuItemClickListener {
                    menuPlay()
                    this@PlaylistFragment.menu = null
                    true
                }
            }
            menu.findItem(R.id.tired).setOnMenuItemClickListener {
                menuTired()
                this@PlaylistFragment.menu = null
                true
            }
            this@PlaylistFragment.menu = menu
        }

        fun showPopupMenu() {
            PopupMenu(requireContext(), songCard).apply {
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.play -> {
                            menuPlay()
                            this@PlaylistFragment.menu = null
                            true
                        }

                        R.id.tired -> {
                            menuTired()
                            this@PlaylistFragment.menu = null
                            true
                        }

                        else -> false
                    }
                }
                inflate(R.menu.song_menu)
                if (adapterPosition == 0) menu.removeItem(R.id.play)
                if (menu.isNotEmpty()) {
                    this@PlaylistFragment.menu = menu
                    show()
                }
            }
        }

        private fun menuPlay() {
            if (adapterPosition != 0) {
                colorSong(
                    true,
                    if (queueItemDescription.extras!!.containsKey("rating"))
                        queueItemDescription.extras!!.getBoolean("rating")
                    else null
                )
                mediaController!!.transportControls.skipToQueueItem(adapterPosition.toLong())
            }
        }

        private fun menuTired() {
            mediaController!!.transportControls.sendCustomAction(
                MusicService.addTiredAction,
                bundleOf(
                    "songIndex" to adapterPosition
                )
            )
        }
    }
    private inner class SongRecyclerAdapter : RecyclerView.Adapter<ViewHolder>() {
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
            holder.queueItemDescription = mediaController!!.queue[position].description

            if (holder.adapterPosition == 0) holder.songCard.play_song.visibility = View.GONE

            // Set the colors
            holder.colorSong(
                holder.adapterPosition == 0,
                if (holder.queueItemDescription.extras!!.containsKey("rating"))
                    holder.queueItemDescription.extras!!.getBoolean("rating")
                else null
            )

            // Bind the song title, artist, and album
            holder.songCard.song_title.text = holder.queueItemDescription.title
            holder.songCard.song_artist.text = holder.queueItemDescription.subtitle
            holder.songCard.song_album.text = holder.queueItemDescription.description
            holder.songCard.progress.text = "-- / --"

            GlideApp
                .with(this@PlaylistFragment)
                .asBitmap()
                .load(holder.queueItemDescription.iconBitmap)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(RoundedCorners(ALBUM_ART_CORNER_RADIUS))
                .into(holder.songCard.song_album_art)

            // Bind the rating progress
            if (holder.queueItemDescription.extras!!.containsKey("settingFeedback")) {
                (holder.queueItemDescription.extras!!.getBoolean("settingFeedback")).apply {
                    if (this) {
                        holder.songCard.love_thumb.visibility = View.GONE
                        holder.songCard.love_progress.visibility = View.VISIBLE
                    } else {
                        holder.songCard.ban_thumb.visibility = View.GONE
                        holder.songCard.ban_progress.visibility = View.VISIBLE
                    }
                }
            } else {
                holder.songCard.ban_progress.visibility = View.GONE
                holder.songCard.love_progress.visibility = View.GONE
                holder.songCard.ban_thumb.visibility = View.VISIBLE
                holder.songCard.love_thumb.visibility = View.VISIBLE
            }

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty() && position <= mediaController!!.queue.size) {
                holder.queueItemDescription = mediaController!!.queue[position].description
                @Suppress("UNCHECKED_CAST")
                (payloads[0] as Map<String, Boolean>).apply {
                    if (get("art") == true) {
                        GlideApp
                            .with(this@PlaylistFragment)
                            .load(holder.queueItemDescription.iconBitmap)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .transform(RoundedCorners(ALBUM_ART_CORNER_RADIUS))
                            .placeholder(holder.songCard.song_album_art.drawable)
                            .into(holder.songCard.song_album_art)

                        holder.colorSong(
                            holder.adapterPosition == 0,
                            if (holder.queueItemDescription.extras!!.containsKey("rating"))
                                holder.queueItemDescription.extras!!.getBoolean("rating")
                            else null
                        )
                    }
                    if (get("rating") == true) {
                        if (holder.queueItemDescription.extras!!.containsKey("settingFeedback")) {
                            (holder.queueItemDescription.extras!!.getBoolean("settingFeedback")).apply {
                                if (this) {
                                    holder.songCard.love_thumb.visibility = View.GONE
                                    holder.songCard.love_progress.visibility = View.VISIBLE
                                } else {
                                    holder.songCard.ban_thumb.visibility = View.GONE
                                    holder.songCard.ban_progress.visibility = View.VISIBLE
                                }
                            }
                        } else {
                            holder.songCard.ban_progress.visibility = View.GONE
                            holder.songCard.love_progress.visibility = View.GONE
                            holder.songCard.ban_thumb.visibility = View.VISIBLE
                            holder.songCard.love_thumb.visibility = View.VISIBLE
                        }
                        holder.colorSong(
                            holder.adapterPosition == 0,
                            if (holder.queueItemDescription.extras!!.containsKey("rating"))
                                holder.queueItemDescription.extras!!.getBoolean("rating")
                            else null
                        )
                    }
                }
            } else {
                return super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onViewDetachedFromWindow(holder: ViewHolder) {
            menu?.close()
        }

        override fun onViewRecycled(holder: ViewHolder) {
            menu?.close()
        }

        override fun getItemCount() = cachedSize
    }
}
