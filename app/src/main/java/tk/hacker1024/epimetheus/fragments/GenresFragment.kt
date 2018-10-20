package tk.hacker1024.epimetheus.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.fragment_genres.view.*
import kotlinx.android.synthetic.main.station_card.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.dialogs.showAddStationConfirmationDialog
import tk.hacker1024.epimetheus.dialogs.showStationAddedSnackbar
import tk.hacker1024.epimetheus.dialogs.showStationAddingSnackbar
import tk.hacker1024.epimetheus.service.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.search.GenreCategory
import tk.hacker1024.libepimetheus.data.search.GenreStation
import tk.hacker1024.libepimetheus.getGenres
import java.io.IOException

class GenresViewModel : ViewModel() {
    lateinit var genreCategory: GenreCategory
    val genres = MutableLiveData<List<GenreStation>>()

    fun loadGenres(user: User) {
        genres.postValue(genreCategory.getGenres(user))
    }
}

class GenresFragment : Fragment() {
    lateinit var viewModel: GenresViewModel
    private val artSize by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this)[GenresViewModel::class.java]
        viewModel.genreCategory = GenresFragmentArgs.fromBundle(arguments).category
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_genres, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).supportActionBar!!.title = viewModel.genreCategory.name

        view.genre_list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = Adapter()
            @Suppress("UNCHECKED_CAST")
            addOnScrollListener(
                RecyclerViewPreloader<String>(
                    this@GenresFragment,
                    adapter as ListPreloader.PreloadModelProvider<String>,
                    ViewPreloadSizeProvider<String>(layoutInflater.inflate(R.layout.station_card, null, false)),
                    10
                )
            )
        }

        GlobalScope.launch {
            try {
                viewModel.loadGenres(ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].user.value!!)
            } catch (e: IOException) {
                (requireActivity() as MainActivity).networkError {
                    it.dismiss()
                    findNavController().navigateUp()
                }
            }
        }

        viewModel.genres.observe(this, Observer {
            view.genre_list.apply {
                view.genres_progress_indicator.visibility = View.GONE
                visibility = View.VISIBLE
                adapter!!.notifyDataSetChanged()
            }
        })
    }

    private inner class ViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card) {
        init {
            card.setOnClickListener {
                showAddStationConfirmationDialog(
                    card.station_name.text,
                    requireContext(),
                    ok = { dialog, _ ->
                        dialog.dismiss()
                        showStationAddingSnackbar(card.station_name.text, view!!)
                        GlobalScope.launch {
                            try {
                                viewModel.genres.value!![adapterPosition].add(
                                    ViewModelProviders.of(
                                        requireActivity()
                                    )[EpimetheusViewModel::class.java].user.value!!
                                )
                                StationListFragment.reloadOnShow = true
                                showStationAddedSnackbar(card.station_name.text, view!!, View.OnClickListener {
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
    }
    private inner class Adapter : RecyclerView.Adapter<ViewHolder>(), ListPreloader.PreloadModelProvider<String> {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.station_card,
                    parent,
                    false
                ) as LinearLayout
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.card.station_name.text = viewModel.genres.value!![position].name

            getPreloadRequestBuilder(viewModel.genres.value!![position].getArtUrl(artSize))
                .transition(DrawableTransitionOptions.withCrossFade())
                .thumbnail(
                    GlideApp
                        .with(this@GenresFragment)
                        .load(GENERIC_ART_URL)
                        .transform(RoundedCorners(8))
                )
                .into(holder.card.station_logo)
        }

        override fun getItemCount() = viewModel.genres.value?.size ?: 0

        override fun getPreloadItems(position: Int) =
            listOf(viewModel.genres.value!![position].getArtUrl(artSize))

        override fun getPreloadRequestBuilder(item: String) =
            GlideApp
                .with(this@GenresFragment)
                .load(item)
                .centerCrop()
                .transform(RoundedCorners(8))
    }
}
