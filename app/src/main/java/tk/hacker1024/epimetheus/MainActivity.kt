package tk.hacker1024.epimetheus

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.nav_header.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import tk.hacker1024.epimetheus.dialogs.showNetworkErrorDialog
import tk.hacker1024.epimetheus.dialogs.showPandoraErrorDialog
import tk.hacker1024.epimetheus.fragments.AUTH_SHARED_PREFS_NAME
import tk.hacker1024.epimetheus.service.EpimetheusMusicService
import tk.hacker1024.epimetheus.service.data.GENERIC_ART_URL
import tk.hacker1024.libepimetheus.Stations
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.Station
import java.io.IOException

// TODO manage audio focus

internal inline val @receiver:ColorInt Int.darken: Int
    @ColorInt get() {
        return Color.HSVToColor(
            FloatArray(3).apply {
                Color.colorToHSV(this@darken, this)
            }.apply {
                this[2] *= 0.8f
            }
        )
    }

internal inline val Context.artSize
    get() = PreferenceManager.getDefaultSharedPreferences(this).getString(
        "art_size",
        "500"
    )!!.toInt()

internal class EpimetheusViewModel : ViewModel() {
    internal var user = MutableLiveData<User>()

    private lateinit var stationList: MutableLiveData<ArrayList<Station>?>
    internal fun getStationList(): MutableLiveData<ArrayList<Station>?> {
        if (!::stationList.isInitialized) {
            stationList = MutableLiveData()
            loadStations()
        }
        return stationList
    }

    internal fun loadStations() {
        GlobalScope.launch  {
            stationList.postValue(
                try {
                    ArrayList(Stations.getStations(user.value!!))
                } catch (e: IOException) {
                    null
                }
            )
        }
    }

    val appBarColor = MutableLiveData<Int>()
    val statusBarColor = MutableLiveData<Int>()
    val titleColor = MutableLiveData<Int>()
    val subtitleColor = MutableLiveData<Int>()
}

class MainActivity : AppCompatActivity() {
    private val navController by lazy { findNavController(R.id.nav_host_fragment) }
    internal lateinit var mediaBrowser: MediaBrowserCompat
    private val viewModel by lazy { ViewModelProviders.of(this)[EpimetheusViewModel::class.java] }

    override fun onSupportNavigateUp() = navController.navigateUp(drawer_layout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        navigation_view.setupWithNavController(navController)
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        NavigationUI.setupActionBarWithNavController(this, navController)

        ViewModelProviders.of(this)[EpimetheusViewModel::class.java].user.observe(this, Observer {
            navigation_view.getHeaderView(0).also { view ->
                view.userName.text = it.username
                view.userEmail.text = it.email
                GlideApp
                    .with(this)
                    .load(it.profilePicUri)
                    .circleCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .thumbnail(
                        GlideApp
                            .with(this)
                            .load(GENERIC_ART_URL)
                            .circleCrop()
                    )
                    .into(view.userPicture)
            }
        })

        drawer_layout.navigation_view.getHeaderView(0).setOnClickListener {
            PopupMenu(this, it).apply {
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.logout -> {
                            logout()
                            true
                        }
                        else -> false
                    }
                }
                inflate(R.menu.account_menu)
                show()
            }
        }

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, EpimetheusMusicService::class.java),
            connectionCallback,
            null
        )

        ViewModelProviders.of(this)[EpimetheusViewModel::class.java].apply {
            appBarColor.observe(this@MainActivity, Observer {
                if (it == 0) {
                    @Suppress("DEPRECATION")
                    appBarColor.value =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimary, null)
                        else resources.getColor(R.color.colorPrimary)
                } else {
                    toolbar.setBackgroundColor(it)
                }
            })

            statusBarColor.observe(this@MainActivity, Observer {
                if (it == 0) {
                    @Suppress("DEPRECATION")
                    statusBarColor.value =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            resources.getColor(R.color.colorPrimaryDark, null)
                        else resources.getColor(R.color.colorPrimaryDark)
                } else {
                    window.statusBarColor = it
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        eventBus.register(this)
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        eventBus.unregister(this)
        mediaBrowser.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        eventBus.unregister(this)
        mediaBrowser.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEARCH) {
            navController.navigate(R.id.searchFragment, intent.extras)
        }
    }

    internal fun connectMediaBrowser(runOnConnect: (() -> Unit)? = null) {
        if (!mediaBrowser.isConnected) {
            connectionCallback.runOnConnect.add(runOnConnect)
            try {
                mediaBrowser.connect()
            } catch (e: IllegalStateException) {
                runOnConnect?.invoke()
            }
        } else {
            runOnConnect?.invoke()
        }
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        var runOnConnect: MutableList<(() -> Unit)?> = mutableListOf()

        override fun onConnected() {
            MediaControllerCompat.setMediaController(
                this@MainActivity,
                MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
            )
            runOnConnect.forEach { it?.invoke() }
            runOnConnect.clear()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceEvent(event: MusicServiceEvent) {
        if (event.disconnect) {
            mediaBrowser.disconnect()
            viewModel.statusBarColor.postValue(0)
            viewModel.appBarColor.postValue(0)
        }
    }

    internal fun networkError(
        onDialogClose: (dialog: DialogInterface) -> Unit = ::navigateToStationListScreenFromPlaylistScreen
    ) {
        runOnUiThread {
            showNetworkErrorDialog(
                this,
                exit = ::closeAppOnDialogPress,
                onClose = onDialogClose
            )
        }
    }

    internal fun pandoraError(
        message: String,
        onDialogClose: (dialog: DialogInterface) -> Unit = ::navigateToStationListScreenFromPlaylistScreen
    ) {
        runOnUiThread {
            showPandoraErrorDialog(
                message,
                this,
                exit = ::closeAppOnDialogPress,
                onClose = onDialogClose
            )
        }
    }

    internal fun closeAppOnDialogPress(dialog: DialogInterface, @Suppress("UNUSED_PARAMETER") which: Int) {
        dialog.dismiss()
        finishAndRemoveTask()
    }

    private fun navigateToStationListScreenFromPlaylistScreen(@Suppress("UNUSED_PARAMETER") dialog: DialogInterface) {
        navController.apply {
            if (currentDestination!!.id == R.id.playlistFragment) {
                popBackStack(R.id.stationListFragment, false)
            }
        }
    }

    internal fun logout() {
        stopService(Intent(this, EpimetheusMusicService::class.java))
        getSharedPreferences(AUTH_SHARED_PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
        finish()
        startActivity(
            Intent(this@MainActivity, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
    }
}