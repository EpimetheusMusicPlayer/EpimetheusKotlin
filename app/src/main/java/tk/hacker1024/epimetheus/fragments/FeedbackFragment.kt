package tk.hacker1024.epimetheus.fragments


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.paging.PositionalDataSource
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.feedback_card.view.*
import kotlinx.android.synthetic.main.feedback_tab.view.*
import kotlinx.android.synthetic.main.fragment_tabs.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.data.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.data.Station
import tk.hacker1024.libepimetheus.data.feedback.FeedbackItem
import tk.hacker1024.libepimetheus.data.search.toTrack
import tk.hacker1024.libepimetheus.deleteFeedback
import tk.hacker1024.libepimetheus.getFeedback
import java.io.IOException

private const val DUPLICATE_NAME_TAG = "DUPLICATE"

class FeedbackFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tabs, container, false).apply {
            pager.adapter = PagerAdapter(childFragmentManager)

            ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].appBarColor.observe(
                this@FeedbackFragment,
                Observer {
                    tabs.setBackgroundColor(it)
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.pager.apply {
            tabs.getTabAt(0)!!.setIcon(R.drawable.ic_thumb_up_white_24dp)
            tabs.getTabAt(1)!!.setIcon(R.drawable.ic_thumb_down_white_24dp)
        }
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as MainActivity).toolbar_layout.elevation = 0f
    }

    private inner class PagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getCount() = 2

        override fun getItem(position: Int) =
            FeedbackFragmentArgs.fromBundle(arguments).stationIndex.let {
                when (position) {
                    0 -> FeedbackTab.newInstance(true, it)
                    1 -> FeedbackTab.newInstance(false, it)
                    else -> null
                }
            }
    }
}

class FeedbackTab : Fragment() {
    private lateinit var appViewModel: EpimetheusViewModel
    private lateinit var station: Station

    private val artSize by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()
    }

    private val pagedListBuilder: PagedList.Builder<Int, FeedbackItem>
        get() = PagedList.Builder<Int, FeedbackItem>(
            DataSource(),
            PagedList.Config.Builder()
                .setEnablePlaceholders(true)
                .setPageSize(10)
                .build()
        )
            .setNotifyExecutor {
                activity?.runOnUiThread {
                    it.run()
                }
            }
            .setFetchExecutor {
                GlobalScope.launch {
                    try {
                        it.run()
                    } catch (e: IOException) {
                        (activity as? MainActivity)?.networkError {
                            findNavController().navigateUp()
                        }
                    }
                }
            }

    private lateinit var sizeProvider: ViewPreloadSizeProvider<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appViewModel = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]
        station = appViewModel.getStationList().value!![arguments!!.getInt("stationIndex")]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.feedback_tab, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sizeProvider = ViewPreloadSizeProvider()
        view.feedback_list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = Adapter()

            @Suppress("UNCHECKED_CAST")
            addOnScrollListener(
                RecyclerViewPreloader(
                    this@FeedbackTab,
                    adapter as ListPreloader.PreloadModelProvider<String>,
                    sizeProvider,
                    15
                )
            )

            GlobalScope.launch {
                try {
                    pagedListBuilder.build().apply {
                        activity?.runOnUiThread {
                            @Suppress("UNCHECKED_CAST")
                            (adapter as PagedListAdapter<FeedbackItem, Adapter.ViewHolder>)
                                .submitList(this)
                            view.feedback_progress_indicator?.visibility = View.GONE
                            view.feedback_list?.visibility = View.VISIBLE
                        }
                    }
                } catch (e: IOException) {
                    (activity as? MainActivity)?.networkError {
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private inner class Adapter : PagedListAdapter<FeedbackItem, Adapter.ViewHolder>(FeedbackItem.DiffUtilItemCallback()), ListPreloader.PreloadModelProvider<String> {
        internal inner class ViewHolder(internal val card: LinearLayout) : RecyclerView.ViewHolder(card) {
            init {
                sizeProvider.setView(card)

                card.setOnClickListener {
                    findNavController().navigate(
                        FeedbackFragmentDirections.songDetails(getItem(adapterPosition)!!.toTrack())
                    )
                }

                card.delete.setOnClickListener {
                    card.delete.visibility = View.GONE
                    card.delete_progress.visibility = View.VISIBLE
                    GlobalScope.launch {
                        try {
                            currentList!![adapterPosition]!!.deleteFeedback(appViewModel.user.value!!)
                            currentList!!.dataSource.invalidate()
                            submitList(pagedListBuilder.build())
                        } catch (e: IOException) {
                            (activity as? MainActivity)?.networkError {
                                findNavController().navigateUp()
                            }
                        }
                    }
                }
            }

            internal fun setVisible(visible: Boolean) {
                card.layoutParams = card.layoutParams.apply {
                    if (visible) {
                        height = LinearLayout.LayoutParams.WRAP_CONTENT
                        width = LinearLayout.LayoutParams.MATCH_PARENT
                    } else {
                        height = 0
                        width = 0
                    }
                }
            }

            internal fun clear() {
                card.apply {
                    delete.visibility = View.GONE
                    delete_progress.visibility = View.VISIBLE
                    song_title.text = getString(R.string.loading)
                    song_artist.text = getString(R.string.loading)
                    song_album.text = getString(R.string.loading)

                    GlideApp
                        .with(parentFragment!!)
                        .load(GENERIC_ART_URL)
                        .transform(RoundedCorners(8))
                        .into(card.song_album_art)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.feedback_card,
                parent,
                false
            ) as LinearLayout
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            getItem(position)?.apply {
                if (name == DUPLICATE_NAME_TAG) {
                    holder.setVisible(false)
                    holder.clear()
                } else {
                    holder.card.apply {
                        holder.setVisible(true)
                        delete_progress.visibility = View.GONE
                        delete.visibility = View.VISIBLE
                        song_title.text = name
                        song_artist.text = artist
                        song_album.text = album

                        getPreloadRequestBuilder(getArtUrl(artSize))
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .thumbnail(
                                GlideApp
                                    .with(parentFragment!!)
                                    .load(GENERIC_ART_URL)
                                    .transform(RoundedCorners(8))
                            )
                            .into(holder.card.song_album_art)
                    }
                }
            } ?: holder.clear()
        }

        override fun getPreloadItems(position: Int) =
            currentList?.get(position)?.getArtUrl(artSize)?.run { listOf(this) } ?: listOf(GENERIC_ART_URL)

        override fun getPreloadRequestBuilder(item: String) =
            GlideApp
                .with(parentFragment!!)
                .load(item)
                .centerCrop()
                .transform(RoundedCorners(8))
    }

    private inner class DataSource : PositionalDataSource<FeedbackItem>() {
        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<FeedbackItem>) {
            station.getFeedback(
                appViewModel.user.value!!,
                arguments!!.getBoolean("thumbsUp"),
                params.requestedLoadSize,
                params.requestedStartPosition
            ).run {
                callback.onResult(
                    this,
                    params.requestedStartPosition,
                    totalSize
                )
            }
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<FeedbackItem>) {
            callback.onResult(
                station.getFeedback(
                    appViewModel.user.value!!,
                    arguments!!.getBoolean("thumbsUp"),
                    params.loadSize,
                    params.startPosition
                ).apply {
                    if (size < params.loadSize) {
                        last().copy(name = DUPLICATE_NAME_TAG).also { item ->
                            repeat(params.loadSize - size) {
                                this += item
                            }
                        }
                    }
                }
            )
        }
    }

    companion object {
        fun newInstance(thumbsUp: Boolean, stationIndex: Int): FeedbackTab {
            return FeedbackTab().apply {
                arguments = bundleOf(
                    "thumbsUp" to thumbsUp,
                    "stationIndex" to stationIndex
                )
            }
        }
    }
}
