package tk.hacker1024.epimetheus.fragments.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_station_card.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.GlideRequest
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.dialogs.showAddStationConfirmationDialog
import tk.hacker1024.epimetheus.dialogs.showStationAddedSnackbar
import tk.hacker1024.epimetheus.dialogs.showStationAddingSnackbar
import tk.hacker1024.epimetheus.fragments.StationListFragment
import tk.hacker1024.libepimetheus.data.search.GenreStation
import tk.hacker1024.libepimetheus.data.search.SearchType
import java.io.IOException

internal class Stations : SearchTab<GenreStation>(SearchType.STATION) {
    companion object {
        internal const val LABEL = "Stations"
    }

    override val adapter = Adapter()

    protected inner class Adapter : PagedListAdapter<GenreStation, Adapter.ViewHolder>(GenreStation.DiffUtilItemCallback()) {
        internal inner class ViewHolder(item: LinearLayout) : RecyclerView.ViewHolder(item) {
            private val mStationName = item.name!!
            private val mArt = item.art

            init {
                (preloadSizeProvider as ViewPreloadSizeProvider).setView(mArt)

                item.setOnClickListener {
                    showAddStationConfirmationDialog(
                        mStationName.text,
                        requireContext(),
                        ok = { dialog, _ ->
                            dialog.dismiss()
                            showStationAddingSnackbar(mStationName.text, view!!)
                            GlobalScope.launch {
                                try {
                                    currentList!![adapterPosition]!!.add(user)
                                    StationListFragment.reloadOnShow = true
                                    showStationAddedSnackbar(mStationName.text, view!!, View.OnClickListener {
                                        findNavController().popBackStack(R.id.stationListFragment, false)
                                    })
                                } catch (e: IOException) {
                                    (requireActivity() as MainActivity).networkError {}
                                }
                            }
                        }
                    )
                }
            }

            internal fun setData(station: GenreStation, request: GlideRequest<Drawable>) {
                mStationName.text = station.name
                request.into(mArt)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.search_station_card,
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