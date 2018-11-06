package tk.hacker1024.epimetheus.fragments

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_tabs.view.*
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.fragments.search.*
import tk.hacker1024.libepimetheus.data.PandoraData
import tk.hacker1024.libepimetheus.data.search.Track
import kotlin.math.atan

class SearchFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_tabs, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.pager.adapter = PagerAdapter(childFragmentManager)
        view.pager.currentItem = SearchFragmentArgs.fromBundle(arguments).tab

        ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].appBarColor.observe(
            this,
            Observer {
                view.tabs.setBackgroundColor(it)
            }
        )
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as MainActivity).toolbar_layout.elevation = 0f
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_menu, menu)
        (menu[0].actionView as SearchView).apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(SearchQueryListener())
        }
    }

    private inner class SearchQueryListener : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            findNavController().navigate(
                SearchFragmentDirections.searchAgain(query).setTab(view!!.pager.currentItem)
            )
            return true
        }

        override fun onQueryTextChange(newText: String) = false
    }

    private inner class PagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getItem(position: Int) =
            SearchFragmentArgs.fromBundle(arguments).query!!.let {
                when (position) {
                    0 -> Songs.newInstance(it)
                    1 -> Artists.newInstance(it)
                    2 -> Albums.newInstance(it)
                    3 -> Playlists.newInstance(it)
                    4 -> Stations.newInstance(it)
                    else -> null
                }
            }


        override fun getPageTitle(position: Int) =
            when (position) {
                0 -> Songs.LABEL
                1 -> Artists.LABEL
                2 -> Albums.LABEL
                3 -> Playlists.LABEL
                4 -> Stations.LABEL
                else -> "Miscellaneous"
            }

        override fun getCount() = 5
    }
}
