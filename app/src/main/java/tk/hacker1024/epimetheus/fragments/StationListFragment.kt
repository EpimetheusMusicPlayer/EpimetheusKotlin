package tk.hacker1024.epimetheus.fragments

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.get
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.fragment_station_list.view.*
import kotlinx.android.synthetic.main.station_card.view.*
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.PandoraViewModel
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.User

class StationListFragment : Fragment() {
    private lateinit var viewModel: PandoraViewModel
    private lateinit var user: User
    val artSize
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the ViewModel
        viewModel = ViewModelProviders.of(requireActivity())[PandoraViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        StationListFragmentArgs.fromBundle(arguments).user?.also {
            findNavController().graph[R.id.stationListFragment].setDefaultArguments(
                Bundle().apply { putParcelable("user", it) }
            )
            user = it
        }

        viewModel.getStationList(user).observe(this, Observer {
            if (it != null) {
                view?.recyclerview_station_list?.adapter?.notifyDataSetChanged()
                view?.recyclerview_station_list?.visibility = View.VISIBLE
                view?.station_list_swipe_refresh_layout?.isRefreshing = false
            } else {
                (requireActivity() as MainActivity).networkError {
                    view?.recyclerview_station_list?.visibility = View.INVISIBLE
                    viewModel.loadStations(user)
                }
            }
        })

        return inflater.inflate(R.layout.fragment_station_list, container, false).apply {
            recyclerview_station_list.apply {
                addOnScrollListener(
                    RecyclerViewPreloader<String>(
                        Glide.with(this),
                        object : ListPreloader.PreloadModelProvider<String> {
                            override fun getPreloadItems(position: Int) =
                                mutableListOf(
                                    viewModel.getStationList(user).value!![position].getArtUrl(
                                        if (viewModel.getStationList(user).value!![position].isShuffle) 500 else artSize
                                    )
                                )

                            override fun getPreloadRequestBuilder(item: String): RequestBuilder<Drawable> {
                                return GlideApp
                                    .with(this@StationListFragment)
                                    .load(item)
                                    .transform(RoundedCorners(8))
                                    .transition(DrawableTransitionOptions.withCrossFade())
                            }
                        },
                        ViewPreloadSizeProvider<String>(layoutInflater.inflate(R.layout.station_card, container).station_logo),
                        10
                    )
                )

                layoutManager = LinearLayoutManager(context)
                adapter = StationListAdapter()
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.station_list_swipe_refresh_layout.setOnRefreshListener {
            view.recyclerview_station_list?.visibility = View.INVISIBLE
            viewModel.loadStations(user)
        }
    }

    override fun onStart() {
        super.onStart()

        if (view?.station_list_swipe_refresh_layout?.isRefreshing == true) {
            view?.recyclerview_station_list?.visibility = View.INVISIBLE
            viewModel.loadStations(user)
        }
    }

    private class StationListAdapterViewHolder(val card: LinearLayout) :
        RecyclerView.ViewHolder(card)

    private inner class StationListAdapter : RecyclerView.Adapter<StationListAdapterViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            StationListAdapterViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.station_card,
                    parent,
                    false
                ) as LinearLayout
            )

        // TODO maybe I should make the Shuffle station stand out more...
        override fun onBindViewHolder(holder: StationListAdapterViewHolder, position: Int) {
            // Bind the station name
            holder.card.station_name.text = viewModel.getStationList(user).value!![position].name

            // Bind the station art TODO FINISH IMPLEMENTING GLIDE
            GlideApp
                .with(this@StationListFragment)
                .load(
                    viewModel.getStationList(user).value!![position].getArtUrl(
                        if (viewModel.getStationList(user).value!![position].isShuffle) 500 else artSize
                    )
                )
                .transform(RoundedCorners(8))
                .transition(DrawableTransitionOptions.withCrossFade())
                .thumbnail(
                    GlideApp
                        .with(this@StationListFragment)
                        .load(GENERIC_ART_URL)
                        .transform(RoundedCorners(8))
                )
                .into(holder.card.station_logo)

            holder.card.setOnClickListener {
                findNavController().navigate(
                    R.id.playlistFragment,
                    bundleOf(
                        "user" to user,
                        "stations" to viewModel.getStationList(user).value,
                        "stationIndex" to holder.adapterPosition
                    )
                )
            }
        }

        override fun getItemCount() = viewModel.getStationList(user).value?.size ?: 0
    }
}
