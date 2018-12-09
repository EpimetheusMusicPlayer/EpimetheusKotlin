package tk.hacker1024.epimetheus.fragments.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_album_card.view.*
import tk.hacker1024.epimetheus.GlideRequest
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.data.search.Album
import tk.hacker1024.libepimetheus.data.search.SearchType

internal class Albums : SearchTabFragment<Album>(SearchType.ALBUM) {
    companion object {
        internal const val LABEL = "Albums"
    }

    override val adapter = Adapter()

    protected inner class ViewHolder(item: LinearLayout) : RecyclerView.ViewHolder(item) {
        private val mAlbumName = item.name!!
        private val mArtistName = item.artist_name!!
        private val mTrackCount = item.track_count!!
        private val mArt = item.art!!

        init {
            (preloadSizeProvider as ViewPreloadSizeProvider).setView(mArt)
        }

        internal fun setData(album: Album, request: GlideRequest<Drawable>) {
            mAlbumName.text = album.name
            mArtistName.text = album.artistName
            mTrackCount.text = resources.getQuantityString(R.plurals.tracksCount, album.trackCount, album.trackCount)
            request.into(mArt)
        }
    }

    protected inner class Adapter : PagedListAdapter<Album, ViewHolder>(Album.DiffUtilItemCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.search_album_card,
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