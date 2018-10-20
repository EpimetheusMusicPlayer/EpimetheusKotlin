package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
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
import kotlinx.android.synthetic.main.edittext_dialog.view.*
import kotlinx.android.synthetic.main.fragment_station_list.*
import kotlinx.android.synthetic.main.fragment_station_list.view.*
import kotlinx.android.synthetic.main.station_card.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.delete
import tk.hacker1024.libepimetheus.rename

class StationListFragment : Fragment() {
    private lateinit var viewModel: EpimetheusViewModel
    private lateinit var user: User
    val artSize
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the ViewModel
        viewModel = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]
        user = viewModel.user.value!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.getStationList().observe(this, Observer {
            if (it != null) {
                view?.recyclerview_station_list?.adapter?.notifyDataSetChanged()
                view?.recyclerview_station_list?.visibility = View.VISIBLE
                view?.station_list_swipe_refresh_layout?.isRefreshing = false
            } else {
                (requireActivity() as MainActivity).networkError {
                    view?.recyclerview_station_list?.visibility = View.INVISIBLE
                    viewModel.loadStations()
                }
            }
        })

        return inflater.inflate(R.layout.fragment_station_list, container, false).apply {
            if (viewModel.getStationList().value == null) {
                post {
                    station_list_swipe_refresh_layout.isRefreshing = true
                }
            }

            recyclerview_station_list.apply {
                addOnScrollListener(
                    RecyclerViewPreloader<String>(
                        Glide.with(this),
                        object : ListPreloader.PreloadModelProvider<String> {
                            override fun getPreloadItems(position: Int) =
                                mutableListOf(
                                    viewModel.getStationList().value!![position].getArtUrl(
                                        if (viewModel.getStationList().value!![position].isShuffle) 500 else artSize
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
            viewModel.loadStations()
        }
    }

    override fun onStart() {
        super.onStart()

        view?.station_list_swipe_refresh_layout.apply {
            if (this?.isRefreshing == true || reloadOnShow) {
                view?.recyclerview_station_list?.visibility = View.INVISIBLE
                this?.post {
                    isRefreshing = true
                }
                viewModel.loadStations()
                reloadOnShow = false
            }
        }
    }

    private inner class StationListAdapterViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card) {
        init {
            card.setOnCreateContextMenuListener { menu, _, _ ->
                val station = viewModel.getStationList().value!![adapterPosition]

                requireActivity().menuInflater.inflate(R.menu.station_menu, menu)
                menu.setHeaderTitle(station.name)

                if (station.canRename) {
                    menu.findItem(R.id.rename_station).setOnMenuItemClickListener {
                        @SuppressLint("InflateParams")
                        val editText = layoutInflater.inflate(R.layout.edittext_dialog, null).input
                        editText.setText(station.name)
                        AlertDialog.Builder(requireContext())
                            .setTitle("Rename ${station.name}")
                            .setIcon(R.drawable.ic_edit_black_24dp)
                            .setView(editText.parent as View)
                            .setPositiveButton("Save") { dialog, _ ->
                                if (editText.text.toString() != station.name && editText.text.isNotEmpty()) {
                                    viewModel.getStationList().value!!.apply {
                                        indexOf(station).also { index ->
                                            this[index] = station.copy(
                                                name = editText.text.toString()
                                            )
                                            view!!.recyclerview_station_list.adapter!!.notifyItemChanged(index)
                                        }
                                    }
                                    dialog.dismiss()
                                    GlobalScope.launch {
                                        station.rename(
                                            editText.text.toString(),
                                            user
                                        )
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                        true
                    }
                } else {
                    menu.removeItem(R.id.rename_station)
                }

                if (station.canDelete && !station.isThumbprint) {
                    menu.findItem(R.id.delete_station).setOnMenuItemClickListener {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Delete ${station.name}")
                            .setIcon(R.drawable.ic_delete_black_24dp)
                            .setMessage("Are you sure?")
                            .setPositiveButton("Delete") { dialog, _ ->
                                viewModel.getStationList().value!!.apply {
                                    indexOf(station).also { index ->
                                        (requireActivity() as MainActivity).connectMediaBrowser {
                                            MediaControllerCompat.getMediaController(requireActivity())
                                                .apply {
                                                    if (
                                                        extras?.getInt("stationIndex") == index
                                                    ) {
                                                        transportControls.stop()
                                                        (childFragmentManager.findFragmentById(R.id.fragment_media_control) as MediaControlFragment).hide()
                                                    }
                                                }
                                        }
                                        removeAt(index)
                                        recyclerview_station_list.adapter!!.notifyItemRemoved(index)
                                    }
                                }
                                dialog.dismiss()
                                GlobalScope.launch {
                                    station.delete(user)
                                }
                            }
                            .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                        true
                    }
                } else {
                    menu.removeItem(R.id.delete_station)
                }
            }

            card.setOnClickListener {
                findNavController().navigate(
                    R.id.openAndPlayStationPlaylist,
                    bundleOf(
                        "stations" to viewModel.getStationList().value,
                        "stationIndex" to adapterPosition,
                        "start" to true
                    )
                )
            }
        }
    }

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
            holder.card.station_name.text = viewModel.getStationList().value!![position].name

            // Bind the station art
            GlideApp
                .with(this@StationListFragment)
                .load(
                    viewModel.getStationList().value!![position].getArtUrl(
                        if (viewModel.getStationList().value!![position].isShuffle) 500 else artSize
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
        }

        override fun getItemCount() = viewModel.getStationList().value?.size ?: 0
    }

    companion object {
        internal var reloadOnShow = false
    }
}
