package tk.hacker1024.epimetheus.service.state

import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.hacker1024.epimetheus.service.data.EpimetheusMediaProvider

internal class StateManager(private val playbackStateBuilder: PlaybackStateCompat.Builder, private val mediaSession: MediaSessionCompat, private val mediaPlayer: SimpleExoPlayer, private val mediaProvider: EpimetheusMediaProvider) {
    private val eventListener = EventListener()

    private fun MediaSessionCompat.setState(@PlaybackStateCompat.State state: Int = controller.playbackState.state) {
        setPlaybackState(
            playbackStateBuilder.setState(
                state,
                mediaPlayer.currentPosition,
                mediaPlayer.playbackParameters.speed
            ).build()
        )
    }

    init {
        mediaPlayer.addListener(eventListener)
    }

    private inner class EventListener : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> mediaSession.setState(PlaybackStateCompat.STATE_BUFFERING)

                Player.STATE_READY -> {
                    updateMetadata()
                    if (playWhenReady) {
                        mediaSession.setState(PlaybackStateCompat.STATE_PLAYING)
                    } else {
                        mediaSession.setState(PlaybackStateCompat.STATE_PAUSED)
                    }
                }

                Player.STATE_IDLE -> mediaSession.setState(PlaybackStateCompat.STATE_NONE)

                Player.STATE_ENDED -> mediaSession.setState(PlaybackStateCompat.STATE_NONE)
            }
        }

        override fun onSeekProcessed() = mediaSession.setState()
    }

    fun updateMetadata(duration: Long = mediaPlayer.duration) {
        GlobalScope.launch {
            mediaSession.setMetadata(mediaProvider.getCurrentMetadata(duration))
        }
    }

    fun release() = mediaPlayer.removeListener(eventListener)
}