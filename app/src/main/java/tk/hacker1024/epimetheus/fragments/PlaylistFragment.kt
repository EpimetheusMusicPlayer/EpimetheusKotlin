package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.get
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.fragment_playlist.*
import kotlinx.android.synthetic.main.fragment_playlist.view.*
import kotlinx.android.synthetic.main.song_card_inactive.view.*
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.MusicService
import tk.hacker1024.libepimetheus.User

private const val ALBUM_ART_CORNER_RADIUS = 24

class PlaylistFragment : Fragment() {
    private var mediaController: MediaControllerCompat? = null
    private var cachedSize = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments!!.getBoolean("start")) {
            findNavController().graph[R.id.playlistFragment].setDefaultArguments(
                bundleOf(
                    "user" to arguments!!.getParcelable<User>("user"),
                    "stationIndex" to arguments!!.getInt("stationIndex"),
                    "stations" to null,
                    "start" to false
                )
            )

            ContextCompat.startForegroundService(
                requireContext(),
                Intent(context, MusicService::class.java)
                    .putExtra("pandoraUserObject", arguments!!.getParcelable<User>("user"))
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
        }
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
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
                    song_list.findViewHolderForAdapterPosition(0)?.apply {
                        (it as SongRecyclerAdapter).colorSong(
                            (this as ViewHolder).songCard,
                            true,
                            if (queueItemDescription.extras!!.containsKey("rating"))
                                queueItemDescription.extras!!.getBoolean("rating")
                            else null
                        )
                    }
                }
            }
        }
    }

    private class ViewHolder(internal val songCard: LinearLayout) : RecyclerView.ViewHolder(songCard) {
        lateinit var queueItemDescription: MediaDescriptionCompat
    }
    private inner class SongRecyclerAdapter : RecyclerView.Adapter<ViewHolder>() {
        @ColorInt private val textColorActive: Int
        @ColorInt private val textColorInactive: Int

        init {
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
            try {
                holder.queueItemDescription = mediaController!!.queue[position].description

                // Set the colors
                colorSong(
                    holder.songCard,
                    holder.adapterPosition == 0,
                    if (holder.queueItemDescription.extras!!.containsKey("rating"))
                        holder.queueItemDescription.extras!!.getBoolean("rating")
                    else null
                )

                // Bind the song title, artist, and album
                holder.songCard.song_title.text = holder.queueItemDescription.title
                holder.songCard.song_artist.text = holder.queueItemDescription.subtitle
                holder.songCard.song_album.text = holder.queueItemDescription.description

                GlideApp
                    .with(this@PlaylistFragment)
                    .asBitmap()
                    .load(holder.queueItemDescription.iconBitmap)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .transform(RoundedCorners(ALBUM_ART_CORNER_RADIUS))
                    .into(holder.songCard.song_album_art)

                // Show menu on click.
                holder.songCard.setOnClickListener {
                    PopupMenu(requireContext(), it).apply {
                        setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                R.id.play -> {
                                    if (holder.adapterPosition != 0) {
                                        colorSong(
                                            holder.songCard,
                                            true,
                                            if (holder.queueItemDescription.extras!!.containsKey("rating"))
                                                holder.queueItemDescription.extras!!.getBoolean("rating")
                                            else null
                                        )
                                        mediaController!!.transportControls.skipToQueueItem(holder.adapterPosition.toLong())
                                    }
                                    true
                                }

                                R.id.tired -> {
                                    mediaController!!.transportControls.sendCustomAction(
                                        MusicService.addTiredAction,
                                        bundleOf(
                                            "songIndex" to holder.adapterPosition
                                        )
                                    )
                                    true
                                }

                                else -> false
                            }
                        }
                        inflate(R.menu.song_menu)
                        if (holder.adapterPosition == 0) menu.removeItem(R.id.play)
                        if (menu.isNotEmpty()) show()
                    }
                }

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

                holder.songCard.ban_thumb.setOnClickListener {
                    it.visibility = View.GONE
                    holder.songCard.ban_progress.visibility = View.VISIBLE
                    if (holder.queueItemDescription.extras!!.containsKey("rating") && !holder.queueItemDescription.extras!!.getBoolean("rating")) {
                        mediaController!!.transportControls!!.setRating(
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN),
                            bundleOf(
                                "songIndex" to holder.adapterPosition
                            )
                        )
                    } else {
                        mediaController!!.transportControls!!.setRating(
                            RatingCompat.newThumbRating(false),
                            bundleOf(
                                "songIndex" to holder.adapterPosition
                            )
                        )
                    }
                }

                holder.songCard.love_thumb.setOnClickListener {
                    it.visibility = View.GONE
                    holder.songCard.love_progress.visibility = View.VISIBLE
                    if (holder.queueItemDescription.extras!!.containsKey("rating") && holder.queueItemDescription.extras!!.getBoolean("rating")) {
                        mediaController!!.transportControls!!.setRating(
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN),
                            bundleOf(
                                "songIndex" to holder.adapterPosition
                            )
                        )
                    } else {
                        mediaController!!.transportControls!!.setRating(
                            RatingCompat.newThumbRating(true),
                            bundleOf(
                                "songIndex" to holder.adapterPosition
                            )
                        )
                    }
                }
            } catch (e: IndexOutOfBoundsException) {
                Log.wtf("INDEXOOB", "IndexOutOfBoundsException", e)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                holder.queueItemDescription = mediaController!!.queue[holder.adapterPosition].description
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
                        colorSong(
                            holder.songCard,
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

        override fun getItemCount() = cachedSize

        @Suppress("DEPRECATION")
        internal fun colorSong(card: LinearLayout, active: Boolean, rating: Boolean?) {
            if (active) {
                card.setBackgroundColor(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        resources.getColor(R.color.colorPrimary, null)
                    } else {
                        resources.getColor(R.color.colorPrimary)
                    }
                )
                card.song_title.setTextColor(textColorActive)
                card.song_artist.setTextColor(textColorActive)
                card.song_album.setTextColor(textColorActive)
                card.ban_progress.indeterminateDrawable.setTint(textColorActive)
                card.love_progress.indeterminateDrawable.setTint(textColorActive)
                card.ban_thumb.setColorFilter(
                    if (rating == false) 0xFF_FF_FF_00.toInt() else textColorActive
                )
                card.love_thumb.setColorFilter(
                    if (rating == true) 0xFF_FF_FF_00.toInt() else textColorActive
                )
            } else {
                card.setBackgroundColor(Color.TRANSPARENT)
                card.song_title.setTextColor(textColorInactive)
                card.song_artist.setTextColor(textColorInactive)
                card.song_album.setTextColor(textColorInactive)
                card.ban_progress.indeterminateDrawable.setTint(textColorInactive)
                card.love_progress.indeterminateDrawable.setTint(textColorInactive)
                card.ban_thumb.setColorFilter(
                    if (rating == false) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                    }
                    else textColorInactive
                )
                card.love_thumb.setColorFilter(
                    if (rating == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                    }
                    else textColorInactive
                )
            }
        }
    }
}
