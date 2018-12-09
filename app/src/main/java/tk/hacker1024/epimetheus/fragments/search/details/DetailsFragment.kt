package tk.hacker1024.epimetheus.fragments.search.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.libepimetheus.data.PandoraData

abstract class DetailsFragment<T: PandoraData>(@LayoutRes private val layoutId: Int, @IdRes private val progressId: Int = R.id.progress_indicator, @IdRes private val contentId: Int = R.id.content) : Fragment() {
    protected val user by lazy {
        ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java].user.value!!
    }
    protected val artSize by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(
            "art_size",
            "500"
        )!!.toInt()
    }
    private lateinit var localData: T

    protected abstract val name: String
    protected abstract val onlineData: T
    protected abstract fun bindData(layout: View, data: T)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(layoutId, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as MainActivity).supportActionBar!!.title = name
        GlobalScope.launch {
            localData = onlineData
            activity?.runOnUiThread {
                bindData(view, localData)
                showLoadingIndicator = false
            }
        }
    }

    private inline var showLoadingIndicator
        get() = view?.findViewById<View>(R.id.progress_indicator)?.visibility == View.VISIBLE
        set(value) {
            view?.apply {
                if (value) {
                    findViewById<View>(contentId).visibility = View.GONE
                    findViewById<View>(progressId).visibility = View.VISIBLE
                } else {
                    findViewById<View>(progressId).visibility = View.GONE
                    findViewById<View>(contentId).visibility = View.VISIBLE
                }
            }
        }
}