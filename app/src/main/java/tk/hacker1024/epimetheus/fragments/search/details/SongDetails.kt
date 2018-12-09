package tk.hacker1024.epimetheus.fragments.search.details

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.details_song.view.*
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.data.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.data.search.details.TrackDetails
import tk.hacker1024.libepimetheus.getDetails

class SongDetails : DetailsFragment<TrackDetails>(R.layout.details_song) {
    override val name get() = SongDetailsArgs.fromBundle(arguments).track.name
    override val onlineData get() = SongDetailsArgs.fromBundle(arguments).track.getDetails(user)

    override fun bindData(layout: View, data: TrackDetails) {
        layout.apply {
            name_label.apply {
                text = data.name
                isSelected = true
            }
            artist_label.apply {
                text = data.artistName
                isSelected = true
            }
            album_label.apply {
                text = data.albumName
                isSelected = true
            }

            if (data.lyricSnippet.isNotEmpty()) {
                lyricsHeader.visibility = View.VISIBLE
                lyricsContent.visibility = View.VISIBLE

                @SuppressLint("SetTextI18n")
                lyricsContent.text = data.lyricSnippet + "…"
            }

            GlideApp
                .with(this)
                .load(data.getArtUrl(artSize))
                .centerCrop()
                .transform(RoundedCorners(8))
                .transition(DrawableTransitionOptions.withCrossFade())
                .thumbnail(
                    GlideApp
                        .with(this)
                        .load(GENERIC_ART_URL)
                        .transform(RoundedCorners(8))
                )
                .into(album_art)
        }
    }
}