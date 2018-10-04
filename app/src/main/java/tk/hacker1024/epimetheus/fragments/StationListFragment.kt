package tk.hacker1024.epimetheus.fragments

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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.android.synthetic.main.fragment_station_list.view.*
import kotlinx.android.synthetic.main.station_card.view.*
import org.json.JSONException
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.PandoraViewModel
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.User

//TODO dont load every time, use activity viewmodel

class StationListFragment : Fragment() {
    private lateinit var viewModel: PandoraViewModel
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the ViewModel
        viewModel = ViewModelProviders.of(requireActivity())[PandoraViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        StationListFragmentArgs.fromBundle(arguments).user?.also {
            findNavController().currentDestination!!.setDefaultArguments(
                Bundle().apply { putParcelable("user", it) }
            )
            user = it
        }

        viewModel.getStationList(user).observe(this, Observer {
            if (it != null) {
                view?.recyclerview_station_list?.adapter?.notifyDataSetChanged()
                view?.recyclerview_station_list?.visibility = View.VISIBLE
                view?.station_list_swipe_refresh_layout?.isRefreshing = false

                try {
                    for (stationListItem in it) {
                        Picasso.get()
                            .run {
                                stationListItem.getArtUrl(130).let { artUrl ->
                                    if (artUrl != null) {
                                        load(artUrl)
                                    } else {
                                        load(R.drawable.ic_generic_album_art_rounded)
                                    }
                                }
                            }
                            .fetch()
                    }
                } catch (e: JSONException) { }
            } else {
                (requireActivity() as MainActivity).networkError {
                    view?.recyclerview_station_list?.visibility = View.INVISIBLE
                    viewModel.loadStations(user)
                }
            }
        })

        return inflater.inflate(R.layout.fragment_station_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.station_list_swipe_refresh_layout.setOnRefreshListener {
            view.recyclerview_station_list?.visibility = View.INVISIBLE
            viewModel.loadStations(user)
        }

        view.recyclerview_station_list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = StationListAdapter()
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onStart() {
        super.onStart()

        if (view?.station_list_swipe_refresh_layout?.isRefreshing == true) {
            view?.recyclerview_station_list?.visibility = View.INVISIBLE
            viewModel.loadStations(user)
        }
    }

    private class StationListAdapterViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card)
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
            holder.card.station_name.text = viewModel.getStationList(user).value!![holder.adapterPosition].name

            // Bind the station art
            Picasso.get()
                .run {
                    viewModel.getStationList(user).value!![holder.adapterPosition].getArtUrl(500).let { artUrl ->
                        if (artUrl != null) {
                            load(artUrl)
                        } else {
                            load(R.drawable.ic_generic_album_art_rounded)
                        }
                    }
                }
                // While the picture is downloading, show a music note icon
                .placeholder(R.drawable.ic_generic_album_art_rounded)
                // Apply rounded corners
                .transform(RoundedCornersTransformation(25, 0))
                // Insert into the ImageView
                .into(
                    holder.card.station_logo
                )

            holder.card.setOnClickListener {
                findNavController().navigate(
                    R.id.playlistFragment,
                    bundleOf (
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
