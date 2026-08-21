package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AndroidAudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    /**
     * Helper to create a uniquely named output file in context.cacheDir.
     */
    fun createAudioFile(): File {
        return File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
    }

    /**
     * Prepares and starts recording using MediaRecorder with MPEG_4 container and AAC codec.
     */
    fun start(outputFile: File) {
        if (isRecording) return

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000) // 64 kbps (optimal quality & compression)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("AndroidAudioRecorder", "Recording started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AndroidAudioRecorder", "Failed to start MediaRecorder", e)
            isRecording = false
            releaseRecorder()
            throw e
        }
    }

    /**
     * Retrieves current maximum amplitude for live waveform visuals.
     */
    fun getAmplitude(): Int {
        return if (isRecording) {
            try {
                val maxAmp = recorder?.maxAmplitude ?: 0
                _amplitude.value = maxAmp
                maxAmp
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    /**
     * Stops current recording session and frees hardware resources.
     */
    fun stop() {
        if (!isRecording) return

        try {
            recorder?.stop()
            Log.d("AndroidAudioRecorder", "Recording stopped successfully.")
        } catch (e: Exception) {
            Log.e("AndroidAudioRecorder", "Stop failed (recording might have been too short)", e)
        } finally {
            releaseRecorder()
        }
    }

    private fun releaseRecorder() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("AndroidAudioRecorder", "Failed to release recorder", e)
        } finally {
            recorder = null
            isRecording = false
            _amplitude.value = 0
        }
    }
}
