package tk.hacker1024.epimetheus

import android.content.*
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.dialogs.showNetworkErrorDialog
import tk.hacker1024.epimetheus.dialogs.showPandoraErrorDialog
import tk.hacker1024.epimetheus.service.MusicService
import tk.hacker1024.epimetheus.service.MusicServiceResults
import tk.hacker1024.epimetheus.service.RESULTS_BROADCAST_FILTER
import tk.hacker1024.libepimetheus.Stations
import tk.hacker1024.libepimetheus.User
import tk.hacker1024.libepimetheus.data.Station
import java.io.IOException

// TODO manage audio focus

internal class PandoraViewModel : ViewModel() {
    private lateinit var stationList: MutableLiveData<ArrayList<Station>?>
    internal fun getStationList(user: User): MutableLiveData<ArrayList<Station>?> {
        if (!::stationList.isInitialized) {
            stationList = MutableLiveData()
            loadStations(user)
        }
        return stationList
    }

    internal fun loadStations(user: User) {
        GlobalScope.launch  {
            stationList.postValue(
                try {
                    ArrayList(Stations.getStations(user))
                } catch (e: IOException) {
                    null
                }
            )
        }
    }
}

class MainActivity : AppCompatActivity() {
    internal lateinit var mediaBrowser: MediaBrowserCompat

    override fun onSupportNavigateUp() = findNavController(R.id.nav_host_fragment).navigateUp()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        navigation_view.setupWithNavController(findNavController(R.id.nav_host_fragment))
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        NavigationUI.setupActionBarWithNavController(this, findNavController(R.id.nav_host_fragment))

        LocalBroadcastManager.getInstance(this).registerReceiver(
            appBroadcastReceiver,
            IntentFilter(RESULTS_BROADCAST_FILTER)
        )

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            connectionCallback,
            null
        )
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        mediaBrowser.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(appBroadcastReceiver)
        mediaBrowser.disconnect()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (findNavController(R.id.nav_host_fragment).currentDestination!!.id == R.id.stationListFragment && item.itemId == android.R.id.home) {
            drawer_layout.openDrawer(GravityCompat.START)
            true
        } else super.onOptionsItemSelected(item)
    }

    internal fun connectMediaBrowser(runOnConnect: (() -> Unit)? = null) {
        if (!mediaBrowser.isConnected) {
            connectionCallback.runOnConnect = runOnConnect
            mediaBrowser.connect()
        } else {
            runOnConnect?.invoke()
        }
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        var runOnConnect: (() -> Unit)? = null

        override fun onConnected() {
            MediaControllerCompat.setMediaController(
                this@MainActivity,
                MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
            )
            runOnConnect?.invoke()
            runOnConnect = null
        }

        override fun onConnectionSuspended() {
            println("connection suspended")
        }

        override fun onConnectionFailed() {
            println("failed to connect")
        }
    }

    private val appBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra("disconnect", false)) {
                mediaBrowser.disconnect()
            }

            when (intent.getSerializableExtra("error")) {
                MusicServiceResults.REQUEST_CLOSE_APP -> {
                    MediaControllerCompat.getMediaController(this@MainActivity).transportControls.stop()
                    finishAndRemoveTask()
                }

                MusicServiceResults.REQUEST_STOP_MUSIC -> {
                    if (findNavController(R.id.nav_host_fragment).currentDestination!!.id == R.id.playlistFragment) {
                        findNavController(R.id.nav_host_fragment).navigateUp()
                    }
                }

                MusicServiceResults.ERROR_NETWORK, MusicServiceResults.ERROR_INTERNAL -> networkError()

                MusicServiceResults.ERROR_PANDORA -> pandoraError((intent.getSerializableExtra("error") as MusicServiceResults).message)
            }
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
        findNavController(R.id.nav_host_fragment).apply {
            if (currentDestination!!.id == R.id.playlistFragment) {
                popBackStack(R.id.stationListFragment, false)
            }
        }
    }
}