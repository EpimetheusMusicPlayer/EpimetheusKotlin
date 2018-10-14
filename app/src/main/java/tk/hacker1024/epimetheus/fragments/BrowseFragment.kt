package tk.hacker1024.epimetheus.fragments

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.util.FixedPreloadSizeProvider
import kotlinx.android.synthetic.main.browse_card.view.*
import kotlinx.android.synthetic.main.fragment_browse.*
import kotlinx.android.synthetic.main.fragment_browse.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.Browse
import tk.hacker1024.libepimetheus.StationRecommendations
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.search.Listenable
import kotlin.math.round

class BrowseViewModel : ViewModel() {
    val recommendations = MutableLiveData<StationRecommendations>()
}

class BrowseFragment : Fragment() {
    private lateinit var user: User
    private lateinit var viewModel: BrowseViewModel

    private val artSize by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        user = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].user.value!!
        viewModel = ViewModelProviders.of(this)[BrowseViewModel::class.java]

        if (viewModel.recommendations.value == null) {
            GlobalScope.launch {
                viewModel.recommendations.postValue(Browse.getStationRecommendations(user))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) =
        inflater.inflate(R.layout.fragment_browse, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.recommendations.observe(this, Observer {
            requireActivity().runOnUiThread {
                if (artist_list.adapter == null) {
                    artist_list.apply {
                        adapter = Adapter(viewModel.recommendations.value?.artists)
                        addOnScrollListener(
                            RecyclerViewPreloader<String>(
                                Glide.with(this),
                                (adapter as Adapter),
                                FixedPreloadSizeProvider(artSize, artSize),
                                6
                            )
                        )
                    }
                } else artist_list.adapter!!.notifyDataSetChanged()

                if (genre_list.adapter == null) {
                    genre_list.apply {
                        adapter = Adapter(viewModel.recommendations.value?.genreStations)
                        addOnScrollListener(
                            RecyclerViewPreloader<String>(
                                Glide.with(this),
                                (adapter as Adapter),
                                FixedPreloadSizeProvider(artSize, artSize),
                                6
                            )
                        )
                    }
                } else genre_list.adapter!!.notifyDataSetChanged()

                view.progress_indicator.visibility = View.GONE
                view.browseFragment.visibility = View.VISIBLE
            }
        })

        artist_list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        genre_list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private class ViewHolder(val card: CardView) : RecyclerView.ViewHolder(card)

    private inner class Adapter(val dataSource: List<Listenable>?) : RecyclerView.Adapter<ViewHolder>(), ListPreloader.PreloadModelProvider<String> {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.browse_card,
                    parent,
                    false
                ) as CardView
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            getPreloadRequestBuilder(dataSource!![position].getArtUrl(artSize))
                .transition(DrawableTransitionOptions.withCrossFade())
                .thumbnail(
                    GlideApp
                        .with(this@BrowseFragment)
                        .load("https://www.pandora.com/web-version/1.34.2/images/artist_500.png")
                        .override(artSize)
                        .transform(RoundedCorners(8))
                )
                .listener(ColorExtractor(holder))
                .into(holder.card.art)

            holder.card.title.text = dataSource[position].name
            holder.card.subtitle.text = dataSource[position].listenerCount!!.run {
                if (this >= 1000000) {
                    round(this / 1000000f).toInt().run {
                        resources.getQuantityString(R.plurals.listenersMillions, this, this)
                    }
                } else {
                    round(this / 1000f).toInt().run {
                        resources.getQuantityString(R.plurals.listenersThousands, this, this)
                    }
                }
            }
        }

        override fun getItemCount() = dataSource?.size ?: 0

        override fun getPreloadItems(position: Int) =
            mutableListOf(
                dataSource?.get(position)?.getArtUrl(artSize)
            )

        override fun getPreloadRequestBuilder(item: String): RequestBuilder<Drawable> {
            return GlideApp
                .with(this@BrowseFragment)
                .load(item)
                .override(artSize)
                .centerCrop()
                .transform(RoundedCorners(8))
        }
    }

    private class ColorExtractor(val holder: ViewHolder) : RequestListener<Drawable> {
        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            Palette.Builder(resource.toBitmap()).generate().apply {
                holder.card.setCardBackgroundColor(getDarkMutedColor(Color.DKGRAY))
                holder.card.title.setTextColor(getLightVibrantColor(Color.WHITE))
                holder.card.subtitle.setTextColor(getLightMutedColor(getLightVibrantColor(Color.WHITE)))
            }
            return false
        }

        override fun onLoadFailed(
            e: GlideException?,
            model: Any,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ) = false
    }
}
