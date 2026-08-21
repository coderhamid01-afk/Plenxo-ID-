package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioVoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false

    /**
     * Prepares and starts the MediaRecorder with standard, universally compatible MPEG_4/AAC configuration.
     */
    fun startRecording(outputFile: File) {
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
                setAudioEncodingBitRate(64000) // 64 kbps (perfect for voice, high quality and small size)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("AudioVoiceRecorder", "Recording started successfully: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioVoiceRecorder", "Failed to start recording", e)
            isRecording = false
            releaseRecorder()
            throw e
        }
    }

    /**
     * Safely stops the recording and reclaims the native resources.
     */
    fun stopRecording() {
        if (!isRecording) return

        try {
            recorder?.stop()
            Log.d("AudioVoiceRecorder", "Recording stopped successfully")
        } catch (e: Exception) {
            Log.e("AudioVoiceRecorder", "Stop failed (recording might have been too short)", e)
        } finally {
            releaseRecorder()
        }
    }

    private fun releaseRecorder() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("AudioVoiceRecorder", "Failed to release recorder", e)
        } finally {
            recorder = null
            isRecording = false
        }
    }
}
