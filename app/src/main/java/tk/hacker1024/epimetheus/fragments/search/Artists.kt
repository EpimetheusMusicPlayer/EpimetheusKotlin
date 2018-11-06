package tk.hacker1024.epimetheus.fragments.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_artist_card.view.*
import tk.hacker1024.epimetheus.GlideRequest
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.data.search.Artist
import tk.hacker1024.libepimetheus.data.search.SearchType

internal class Artists : SearchTab<Artist>(SearchType.ARTIST) {
    companion object {
        internal const val LABEL = "Artists"
    }

    override val adapter = Adapter()

    protected inner class ViewHolder(item: LinearLayout) : RecyclerView.ViewHolder(item) {
        private val mArtistName = item.name!!
        private val mAlbumCount = item.album_count!!
        private val mTrackCount = item.track_count!!
        private val mArt = item.art!!

        init {
            (preloadSizeProvider as ViewPreloadSizeProvider).setView(mArt)
        }

        internal fun setData(artist: Artist, request: GlideRequest<Drawable>) {
            mArtistName.text = artist.name
            mAlbumCount.text = resources.getQuantityString(R.plurals.albumsCount, artist.albumCount!!, artist.albumCount)
            mTrackCount.text = resources.getQuantityString(R.plurals.tracksCount, artist.trackCount!!, artist.trackCount)
            request.into(mArt)
        }
    }

    protected inner class Adapter : PagedListAdapter<Artist, ViewHolder>(Artist.DiffUtilItemCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.search_artist_card,
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