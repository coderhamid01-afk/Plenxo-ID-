package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentOutputFile: File? = null
    private var sizeMonitoringJob: Job? = null

    var onAutoSendVoiceNote: ((File) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    companion object {
        const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB limit
        private const val TAG = "VoiceRecorderManager"
    }

    /**
     * Starts recording audio to the specified outputFile.
     * Continuously monitors file size on IO thread. If size reaches 100 MB,
     * automatically stops recording and triggers [onAutoSendVoiceNote].
     */
    fun startRecording(outputFile: File) {
        if (isRecording) return

        currentOutputFile = outputFile

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
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Voice recording started: ${outputFile.absolutePath}")

            // Launch real-time file size monitoring
            startSizeMonitoring(outputFile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            isRecording = false
            releaseRecorder()
            onError?.invoke("Failed to start voice recorder: ${e.localizedMessage}")
        }
    }

    private fun startSizeMonitoring(file: File) {
        sizeMonitoringJob?.cancel()
        sizeMonitoringJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                if (file.exists()) {
                    val currentSize = file.length()
                    if (currentSize >= MAX_FILE_SIZE_BYTES) {
                        Log.w(TAG, "Voice recording reached 100 MB limit ($currentSize bytes). Auto-stopping and sending...")
                        withContext(Dispatchers.Main) {
                            stopAndAutoSend()
                        }
                        break
                    }
                }
                delay(200) // Poll file size every 200 ms
            }
        }
    }

    private fun stopAndAutoSend() {
        if (!isRecording) return
        val recordedFile = stopRecording()
        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            Log.d(TAG, "Triggering auto-send for recorded file: ${recordedFile.absolutePath}")
            onAutoSendVoiceNote?.invoke(recordedFile)
        }
    }

    /**
     * Stops the active recording and returns the recorded File.
     */
    fun stopRecording(): File? {
        sizeMonitoringJob?.cancel()
        sizeMonitoringJob = null

        if (!isRecording) return currentOutputFile

        try {
            recorder?.stop()
            Log.d(TAG, "Voice recording stopped successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder (audio might be too short): ${e.message}", e)
        } finally {
            releaseRecorder()
        }

        return currentOutputFile
    }

    /**
     * Cancels active recording and deletes the temporary file.
     */
    fun cancelRecording() {
        sizeMonitoringJob?.cancel()
        sizeMonitoringJob = null

        if (isRecording) {
            try {
                recorder?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder during cancel: ${e.message}")
            } finally {
                releaseRecorder()
            }
        }

        currentOutputFile?.let { file ->
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cancelled recording and deleted temp file: ${file.absolutePath}")
            }
        }
        currentOutputFile = null
    }

    fun isRecording(): Boolean = isRecording

    private fun releaseRecorder() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release MediaRecorder", e)
        } finally {
            recorder = null
            isRecording = false
        }
    }
}
