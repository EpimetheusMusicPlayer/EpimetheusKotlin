package tk.hacker1024.epimetheus.fragments

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.get
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_playlist.*
import kotlinx.android.synthetic.main.fragment_playlist.view.*
import kotlinx.android.synthetic.main.song_card_inactive.view.*
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.MusicService
import tk.hacker1024.libepimetheus.User

// TODO BUG: When switching to another station without stopping the old station, the loading widget won't show; the screen will be blank until it loads.

class PlaylistFragment : Fragment() {
    private var mediaController: MediaControllerCompat? = null
    private var cachedQueue: List<MediaSessionCompat.QueueItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments!!.getInt("stationIndex") != -1) {
            findNavController().graph[R.id.playlistFragment].setDefaultArguments(
                bundleOf(
                    "user" to arguments!!.getParcelable<User>("user"),
                    "stationIndex" to -1,
                    "stations" to null
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_playlist, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (arguments!!.getInt("stationIndex") == -1 && !MediaControlFragment.isServiceRunning(requireContext())) {
            view.loading_widget.visibility = View.GONE
            view.empty.visibility = View.VISIBLE
            view.empty.setOnClickListener {
                findNavController().navigateUp()
            }
        }

        view.song_list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = SongRecyclerAdapter()
        }
    }

    override fun onStart() {
        super.onStart()

        if (arguments!!.getInt("stationIndex") == -1) {
            (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).showIfServiceRunning()
        } else {
            (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).show()
        }

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
            if (queue.size == 0) {
                view?.song_list?.visibility = View.GONE
                view?.loading_widget?.visibility = View.VISIBLE
            } else {
                view?.loading_widget?.visibility = View.GONE
                view?.song_list?.visibility = View.VISIBLE
            }
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    infix fun RatingCompat.sameAs(other: RatingCompat) = this.isRated == other.isRated && this.isThumbUp == other.isThumbUp

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                        oldQueue[oldItemPosition].description.iconUri == queue[newItemPosition].description.iconUri &&
                        oldQueue[oldItemPosition].description.extras!!.getParcelable<RatingCompat>("rating")!! sameAs queue[newItemPosition].description.extras!!.getParcelable("rating")!! &&
                        oldQueue[oldItemPosition].description.extras!!.getParcelable<RatingCompat>("settingFeedback")!! sameAs queue[newItemPosition].description.extras!!.getParcelable("settingFeedback")!!

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                        oldQueue[oldItemPosition].description.title == queue[newItemPosition].description.title &&
                        oldQueue[oldItemPosition].description.description == queue[newItemPosition].description.description &&
                        oldQueue[oldItemPosition].description.subtitle == queue[newItemPosition].description.subtitle

                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Map<String, Boolean> {
                        return mapOf(
                            "art" to (oldQueue[oldItemPosition].description?.iconUri != queue[newItemPosition].description.iconUri),
                            "rating" to (
                                !(oldQueue[oldItemPosition].description.extras!!.getParcelable<RatingCompat>("rating")!! sameAs queue[newItemPosition].description.extras!!.getParcelable("rating")!!) ||
                                !(oldQueue[oldItemPosition].description.extras!!.getParcelable<RatingCompat>("settingFeedback")!! sameAs queue[newItemPosition].description.extras!!.getParcelable("settingFeedback")!!)
                            )
                        )
                    }

                    override fun getOldListSize() = cachedQueue.size

                    override fun getNewListSize() = queue.size
                },
                false
            ).apply {
                view?.song_list?.adapter.also {
                    cachedQueue = queue
                    if (it != null) dispatchUpdatesTo(it)
                    oldQueue = queue
                    song_list.findViewHolderForAdapterPosition(0)?.apply {
                        (it as SongRecyclerAdapter).colorSong(
                            (this as ViewHolder).songCard,
                            true,
                            queueItemDescription.extras!!.getParcelable("rating")!!
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
                holder.queueItemDescription = cachedQueue[holder.adapterPosition].description

                // Set the colors
                colorSong(
                    holder.songCard,
                    holder.adapterPosition == 0,
                    holder.queueItemDescription.extras!!.getParcelable("rating")!!
                )

                // Bind the song title, artist, and album
                holder.songCard.song_title.text = holder.queueItemDescription.title
                holder.songCard.song_artist.text = holder.queueItemDescription.subtitle
                holder.songCard.song_album.text = holder.queueItemDescription.description

                // Bind the album art
                holder.songCard.song_album_art.setImageBitmap(
                        holder.queueItemDescription.iconBitmap
                )

                // Skip to the song on click.
                holder.songCard.setOnClickListener {
                    if (holder.adapterPosition != 0) {
                        colorSong(
                            holder.songCard,
                            true,
                            holder.queueItemDescription.extras!!.getParcelable("rating")!!
                        )
                        mediaController!!.transportControls.skipToQueueItem(holder.adapterPosition.toLong())
                    }
                }

                // Bind the rating progress
                holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("settingFeedback")!!.apply {
                    if (isRated) {
                        if (this.isThumbUp) {
                            holder.songCard.love_thumb.visibility = View.GONE
                            holder.songCard.love_progress.visibility = View.VISIBLE
                        } else {
                            holder.songCard.ban_thumb.visibility = View.GONE
                            holder.songCard.ban_progress.visibility = View.VISIBLE
                        }
                    } else {
                        holder.songCard.ban_progress.visibility = View.GONE
                        holder.songCard.love_progress.visibility = View.GONE
                        holder.songCard.ban_thumb.visibility = View.VISIBLE
                        holder.songCard.love_thumb.visibility = View.VISIBLE
                    }
                }

                holder.songCard.ban_thumb.setOnClickListener {
                    it.visibility = View.GONE
                    holder.songCard.ban_progress.visibility = View.VISIBLE
                    mediaController!!.transportControls.setRating(
                        if (holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("rating")!!.isRated && !holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("rating")!!.isThumbUp) {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        } else {
                            RatingCompat.newThumbRating(false)
                        },
                        bundleOf(
                            "songIndex" to holder.adapterPosition
                        )
                    )
                }

                holder.songCard.love_thumb.setOnClickListener {
                    it.visibility = View.GONE
                    holder.songCard.love_progress.visibility = View.VISIBLE
                    mediaController!!.transportControls.setRating(
                        if (holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("rating")!!.isRated && holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("rating")!!.isThumbUp) {
                            RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN)
                        } else {
                            RatingCompat.newThumbRating(true)
                        },
                        bundleOf(
                            "songIndex" to holder.adapterPosition
                        )
                    )
                }
            } catch (e: IndexOutOfBoundsException) {}
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                holder.queueItemDescription = mediaController!!.queue[holder.adapterPosition].description
                @Suppress("UNCHECKED_CAST")
                (payloads[0] as Map<String, Boolean>).apply {
                    if (get("art") == true) {
                        holder.songCard.song_album_art.apply {
                            val bitmap = mediaController!!.queue[holder.adapterPosition].description.iconBitmap
                            animate().setDuration(200).alpha(0f).setListener(
                                object : Animator.AnimatorListener {
                                    override fun onAnimationStart(animation: Animator?) {}
                                    override fun onAnimationCancel(animation: Animator?) {}
                                    override fun onAnimationRepeat(animation: Animator?) {}

                                    override fun onAnimationEnd(animation: Animator?) {
                                        setImageBitmap(bitmap)
                                        animate().setDuration(200).alpha(1f).start()
                                    }
                                }
                            ).start()
                        }
                    }
                    if (get("rating") == true) {
                        holder.queueItemDescription.extras!!.getParcelable<RatingCompat>("settingFeedback")!!.apply {
                            if (isRated) {
                                if (this.isThumbUp) {
                                    holder.songCard.love_thumb.visibility = View.GONE
                                    holder.songCard.love_progress.visibility = View.VISIBLE
                                } else {
                                    holder.songCard.ban_thumb.visibility = View.GONE
                                    holder.songCard.ban_progress.visibility = View.VISIBLE
                                }
                            } else {
                                holder.songCard.ban_progress.visibility = View.GONE
                                holder.songCard.love_progress.visibility = View.GONE
                                holder.songCard.ban_thumb.visibility = View.VISIBLE
                                holder.songCard.love_thumb.visibility = View.VISIBLE
                            }
                        }
                        colorSong(holder.songCard, holder.adapterPosition == 0, holder.queueItemDescription.extras!!.getParcelable("rating")!!)
                    }
                }
            } else {
                return super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun getItemCount() = mediaController?.queue?.size ?: 0

        @Suppress("DEPRECATION")
        internal fun colorSong(card: LinearLayout, active: Boolean, rating: RatingCompat) {
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
                    if (rating.isRated && !rating.isThumbUp) 0xFF_FF_FF_00.toInt() else textColorActive
                )
                card.love_thumb.setColorFilter(
                    if (rating.isRated && rating.isThumbUp) 0xFF_FF_FF_00.toInt() else textColorActive
                )
            } else {
                card.setBackgroundColor(Color.TRANSPARENT)
                card.song_title.setTextColor(textColorInactive)
                card.song_artist.setTextColor(textColorInactive)
                card.song_album.setTextColor(textColorInactive)
                card.ban_progress.indeterminateDrawable.setTint(textColorInactive)
                card.love_progress.indeterminateDrawable.setTint(textColorInactive)
                card.ban_thumb.setColorFilter(
                    if (rating.isRated && !rating.isThumbUp) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                    }
                    else textColorInactive
                )
                card.love_thumb.setColorFilter(
                    if (rating.isRated && rating.isThumbUp) {
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
