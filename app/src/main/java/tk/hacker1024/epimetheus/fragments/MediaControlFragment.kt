package tk.hacker1024.epimetheus.fragments

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_media_control.*
import kotlinx.android.synthetic.main.fragment_media_control.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.EpimetheusViewModel
import tk.hacker1024.epimetheus.GlideApp
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.MusicService

class MediaControlFragment : Fragment() {
    internal lateinit var viewModel: EpimetheusViewModel
    private var mediaController: MediaControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity())[EpimetheusViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_media_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.darkVibrant.observe(this, Observer {
            if (it != 0) view.setBackgroundColor(it)
        })
        viewModel.lightVibrant.observe(this, Observer {
            if (it != 0) view.playing_title.setTextColor(it)
        })
        viewModel.lightMuted.observe(this, Observer {
            if (it != 0) view.playing_subtitle.setTextColor(it)
        })

        view.stop.setOnClickListener {
            mediaController!!.transportControls.stop()
            view.visibility = View.GONE

            viewModel.darkMuted.postValue(0)
            viewModel.darkVibrant.postValue(0)
        }

        view.rewind.setOnClickListener {
            MediaControllerCompat.getMediaController(requireActivity()).transportControls.rewind()
        }

        view.play_pause.setOnClickListener {
            when (mediaController!!.playbackState.state) {
                PlaybackStateCompat.STATE_PAUSED -> {
                    mediaController!!.transportControls.play()
                }
                PlaybackStateCompat.STATE_PLAYING -> {
                    mediaController!!.transportControls.pause()
                }
            }
        }

        view.fast_forward.setOnClickListener {
            mediaController!!.transportControls.fastForward()
        }

        view.skip.setOnClickListener {
            mediaController!!.transportControls.skipToNext()
        }

        view.setOnClickListener {
            findNavController().apply {
                if (currentDestination!!.id != R.id.playlistFragment) {
                    navigate(R.id.openPlaylist)
                }
            }
        }

        view.playing_subtitle.isSelected = true
    }

    override fun onStart() {
        super.onStart()

        showIfServiceRunning()

        fun onConnect() {
            mediaController = MediaControllerCompat.getMediaController(requireActivity())
            mediaController!!.registerCallback(controllerCallback)
            updatePlaybackState(mediaController!!.playbackState.state)
            mediaController!!.metadata?.let {
                controllerCallback.onMetadataChanged(it)
            }
        }

        if (findNavController().currentDestination!!.id == R.id.playlistFragment) {
            onConnect()
        } else {
            (requireActivity() as MainActivity).connectMediaBrowser {
                onConnect()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        oldUri = null
        mediaController?.unregisterCallback(controllerCallback)
    }

    private fun updatePlaybackState(state: Int) {
        fun changeClickableState(clickable: Boolean) {
            view!!.rewind?.isClickable = clickable
            view!!.play_pause?.isClickable = clickable
            view!!.fast_forward?.isClickable = clickable
        }

        when (state) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING ->
                changeClickableState(false)

            PlaybackStateCompat.STATE_PLAYING -> {
                view?.play_pause?.setImageResource(R.drawable.ic_pause_black_24dp)
                changeClickableState(true)
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                view?.play_pause?.setImageResource(R.drawable.ic_play_arrow_black_24dp)
                changeClickableState(true)
            }
        }
    }

    internal fun show() {
        view!!.visibility = View.VISIBLE
    }

    internal fun hide() {
        view!!.visibility = View.GONE
    }

    internal fun showIfServiceRunning() {
        if (isServiceRunning(requireContext())) show()
    }

    private var oldUri: String? = null
    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) = updatePlaybackState(state.state)

        @SuppressLint("SetTextI18n")
        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            if (oldUri != metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)) {
                view?.let { view ->
                    view.playing_title?.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                    view.playing_subtitle?.text = "${metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)} (${metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)})"

                    GlideApp
                        .with(this@MediaControlFragment)
                        .load(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                        .placeholder(playing_art.drawable)
                        .transform(RoundedCorners(32))
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(view.playing_art)
                    oldUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)

                    GlobalScope.launch {
                        Palette.Builder(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                            .generate().apply {
                                viewModel.apply {
                                    darkVibrant.postValue(getDarkVibrantColor(Color.BLACK))
                                    darkMuted.postValue(getDarkMutedColor(Color.DKGRAY))
                                    lightVibrant.postValue(getLightVibrantColor(Color.WHITE))
                                    lightMuted.postValue(getLightMutedColor(Color.WHITE))
                                }
                            }
                    }
                }
            }
        }
    }

    companion object {
        fun isServiceRunning(context: Context): Boolean {
            @Suppress("DEPRECATION")
            for (service in (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Int.MAX_VALUE)) {
                if ((service.foreground && service.service.className == MusicService::class.java.name)) {
                    return true
                }
            }
            return false
        }
    }
}
