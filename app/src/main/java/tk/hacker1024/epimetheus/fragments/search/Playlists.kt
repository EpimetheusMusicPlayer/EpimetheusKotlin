package tk.hacker1024.epimetheus.fragments.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_playlist_card.view.*
import tk.hacker1024.epimetheus.GlideRequest
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.data.search.Playlist
import tk.hacker1024.libepimetheus.data.search.SearchType

internal class Playlists : SearchTabFragment<Playlist>(SearchType.PLAYLIST) {
    companion object {
        internal const val LABEL = "Playlists"
    }

    override val adapter = Adapter()

    protected inner class ViewHolder(item: LinearLayout) : RecyclerView.ViewHolder(item) {
        private val mPlaylistName = item.name!!
        private val mTrackCount = item.track_count!!
        private val mArt = item.art!!

        init {
            (preloadSizeProvider as ViewPreloadSizeProvider).setView(mArt)
        }

        internal fun setData(playlist: Playlist, request: GlideRequest<Drawable>) {
            mPlaylistName.text = playlist.name
            mTrackCount.text = resources.getQuantityString(R.plurals.tracksCount, playlist.totalTracks, playlist.totalTracks)
            request.into(mArt)
        }
    }

    protected inner class Adapter : PagedListAdapter<Playlist, ViewHolder>(Playlist.DiffUtilItemCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.search_playlist_card,
                    parent,
                    false
                ) as LinearLayout
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setData(
                getItem(position)!!,
                preloadModelProvider.getRequestForDisplay(position)
            )
        }
    }
}