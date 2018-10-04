package tk.hacker1024.epimetheus.fragments

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_media_control.view.*
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R
import tk.hacker1024.epimetheus.service.MusicService

class MediaControlFragment : Fragment() {
    private var mediaController: MediaControllerCompat? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_media_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.stop.setOnClickListener {
            mediaController!!.transportControls.stop()
            view.visibility = View.GONE
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
    }

    override fun onStart() {
        super.onStart()

        fun onConnect() {
            mediaController = MediaControllerCompat.getMediaController(requireActivity())
            mediaController!!.registerCallback(controllerCallback)
            updatePlaybackState(mediaController!!.playbackState.state)
        }

        if (findNavController().currentDestination!!.id == R.id.playlistFragment) {
            onConnect()
        } else {
            (requireActivity() as MainActivity).connectMediaBrowser {
                onConnect()
            }
        }

        view?.apply {
            visibility = View.GONE
            @Suppress("DEPRECATION")
            for (service in (requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Int.MAX_VALUE)) {
                if ((service.foreground && service.service.className == MusicService::class.java.name) || findNavController().currentDestination!!.id == R.id.playlistFragment) {
                    show()
                    break
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
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

    private fun show() {
        view!!.visibility = View.VISIBLE
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) = updatePlaybackState(state.state)
    }
}
