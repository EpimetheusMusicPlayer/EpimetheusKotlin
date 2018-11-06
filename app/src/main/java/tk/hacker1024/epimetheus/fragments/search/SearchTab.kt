package tk.hacker1024.epimetheus.fragments.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.paging.PositionalDataSource
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.util.ViewPreloadSizeProvider
import kotlinx.android.synthetic.main.search_tab.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.Search
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.PandoraData
import tk.hacker1024.libepimetheus.data.search.SearchResults
import tk.hacker1024.libepimetheus.data.search.SearchType
import java.io.IOException

private const val PAGE_SIZE = 20

private class SearchTabViewModel : ViewModel() {
    var pagedList: PagedList<PandoraData>? = null
}

internal abstract class SearchTab<T : PandoraData>(private val searchType: SearchType) : androidx.fragment.app.Fragment() {
    internal companion object {
        inline fun <reified F : SearchTab<*>> newInstance(query: String): F {
            return (F::class.java.newInstance()).apply {
                arguments = androidx.core.os.bundleOf(
                    "query" to query
                )
            }
        }
    }

    protected lateinit var user: User
    private lateinit var viewModel: SearchTabViewModel

    private val artSize by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()
    }

    private val pagedListBuilder
        get() = PagedList.Builder<Int, T>(
            DataSource(),
            PagedList.Config.Builder()
                .setEnablePlaceholders(false)
                .setPageSize(PAGE_SIZE)
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as MainActivity).supportActionBar!!.title = arguments!!.getString("query")!!.capitalize()
        user = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].user.value!!
        viewModel = ViewModelProviders.of(parentFragment!!).get(searchType.toString(), SearchTabViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.search_tab, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.list.apply {
            addOnScrollListener(
                RecyclerViewPreloader<String>(
                    this@SearchTab,
                    preloadModelProvider,
                    preloadSizeProvider,
                    PAGE_SIZE
                )
            )

            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchTab.adapter

            GlobalScope.launch {
                if (viewModel.pagedList == null) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        viewModel.pagedList = pagedListBuilder.build() as PagedList<PandoraData>
                    } catch (e: IOException) {
                        (activity as? MainActivity)?.networkError {
                            findNavController().navigateUp()
                        }
                    }
                }
                activity?.runOnUiThread {
                    @Suppress("UNCHECKED_CAST")
                    this@SearchTab.adapter.submitList(viewModel.pagedList as PagedList<T>)
                    view.progress_indicator?.visibility = View.GONE
                    view.list?.visibility = View.VISIBLE
                }
            }
        }
    }

    private inner class DataSource : PositionalDataSource<T>() {
        fun getSearchList(results: SearchResults): List<T> {
            @Suppress("UNCHECKED_CAST")
            return when (searchType) {
                SearchType.TRACK -> results.tracks
                SearchType.ARTIST -> results.artists
                SearchType.ALBUM -> results.albums
                SearchType.PLAYLIST -> results.playlists
                SearchType.STATION -> results.stations
                else -> results.tracks
            } as List<T>
        }

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            Search.search(
                user,
                arguments!!.getString("query")!!,
                params.requestedStartPosition,
                params.requestedLoadSize,
                artSize,
                searchType
            ).apply {
                getSearchList(this).apply {
                    callback.onResult(
                        this,
                        params.requestedStartPosition,
                        size
                    )
                }
            }
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(
                getSearchList(
                    Search.search(
                        user,
                        arguments!!.getString("query")!!,
                        params.startPosition,
                        params.loadSize,
                        artSize,
                        searchType
                    )
                )
            )
        }
    }

    protected abstract val adapter: PagedListAdapter<T, *>
    protected open val preloadSizeProvider: ListPreloader.PreloadSizeProvider<String> = ViewPreloadSizeProvider<String>()

    protected val preloadModelProvider = PreloadModelProvider()
    protected open inner class PreloadModelProvider : ListPreloader.PreloadModelProvider<String> {
        final override fun getPreloadItems(position: Int) = listOf(adapter.currentList?.get(position)?.getArtUrl(artSize) ?: GENERIC_ART_URL)

        override fun getPreloadRequestBuilder(item: String) =
            GlideApp
                .with(this@SearchTab)
                .load(item)
                .centerCrop()
                .transform(RoundedCorners(8))

        internal open fun getRequestForDisplay(position: Int) =
            getPreloadRequestBuilder(preloadModelProvider.getPreloadItems(position)[0])
                .transition(DrawableTransitionOptions.withCrossFade())
                .thumbnail(
                    GlideApp
                        .with(this@SearchTab)
                        .load(GENERIC_ART_URL)
                        .transform(RoundedCorners(8))
                )
    }
}

internal fun Songs.Companion.newInstance(query: String) = SearchTab.newInstance<Songs>(query)
internal fun Artists.Companion.newInstance(query: String) = SearchTab.newInstance<Artists>(query)
internal fun Albums.Companion.newInstance(query: String) = SearchTab.newInstance<Albums>(query)
internal fun Playlists.Companion.newInstance(query: String) = SearchTab.newInstance<Playlists>(query)
internal fun Stations.Companion.newInstance(query: String) = SearchTab.newInstance<Stations>(query)