package tk.hacker1024.epimetheus.fragments.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_song_card.view.*
import tk.hacker1024.epimetheus.GlideRequest
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.fragments.SearchFragmentDirections
import tk.hacker1024.libepimetheus.data.search.SearchType
import tk.hacker1024.libepimetheus.data.search.Track

internal class Songs : SearchTabFragment<Track>(SearchType.TRACK) {
    companion object {
        internal const val LABEL = "Songs"
    }

    override val adapter = Adapter()

    protected inner class Adapter : PagedListAdapter<Track, Adapter.ViewHolder>(Track.DiffUtilItemCallback()) {
        internal inner class ViewHolder(item: LinearLayout) : RecyclerView.ViewHolder(item) {
            private val mSongTitle = item.song_title!!
            private val mSongArtist = item.song_artist!!
            private val mSongAlbumArt = item.song_album_art!!

            init {
                (preloadSizeProvider as ViewPreloadSizeProvider).setView(mSongAlbumArt)
                item.setOnClickListener {
                    findNavController().navigate(
                        SearchFragmentDirections.songDetails(getItem(adapterPosition)!!)
                    )
                }
            }

            internal fun setData(track: Track, request: GlideRequest<Drawable>) {
                mSongTitle.text = track.name
                mSongArtist.text = track.artistName
                request.into(mSongAlbumArt)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.search_song_card,
                    parent,
                    false
                ) as LinearLayout
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setData(
                getItem(position)!!,
                preloadModelProvider.getRequestForDisplay(position)
            )
        }
    }
}