package com.example.media

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoicePlayer(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _duration.value = exoPlayer?.duration ?: 0L
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentPosition.value = 0L
                    exoPlayer?.seekTo(0)
                    exoPlayer?.pause()
                    stopProgressPolling()
                }
                Player.STATE_BUFFERING -> {
                    Log.d("VoicePlayer", "Buffering streaming audio...")
                }
                Player.STATE_IDLE -> {
                    stopProgressPolling()
                }
            }
        }
    }

    private fun initPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(listener)
            }
        }
    }

    fun play(url: String) {
        initPlayer()
        val player = exoPlayer ?: return

        if (_currentUrl.value == url) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } else {
            player.stop()
            _currentUrl.value = url
            _progress.value = 0f
            _currentPosition.value = 0L
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.let { player ->
            try {
                player.setPlaybackSpeed(speed)
                Log.d("VoicePlayer", "Playback speed set to $speed")
            } catch (e: Exception) {
                Log.e("VoicePlayer", "Failed to set playback speed", e)
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
        _isPlaying.value = false
        _currentUrl.value = null
        _progress.value = 0f
        _currentPosition.value = 0L
        stopProgressPolling()
    }

    fun release() {
        stopProgressPolling()
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _currentUrl.value = null
        _progress.value = 0f
        _currentPosition.value = 0L
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        val dur = player.duration
                        _currentPosition.value = pos
                        if (dur > 0) {
                            _progress.value = pos.toFloat() / dur.toFloat()
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }
}
