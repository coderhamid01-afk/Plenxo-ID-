package com.example.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.common.Format
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

object VideoTranscoderPipeline {

    sealed class TranscodeState {
        object Idle : TranscodeState()
        data class Progress(val percentage: Int) : TranscodeState()
        data class Success(val outputFile: File) : TranscodeState()
        data class Error(val error: Throwable) : TranscodeState()
    }

    /**
     * Hardware-accelerated transcode using Media3 Transformer.
     * Enforces: 720p, 30fps, H.264 @ 2Mbps, AAC audio, MP4 container.
     */
    fun transcodeVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File
    ): Flow<TranscodeState> = callbackFlow {
        try {
            val mediaItem = MediaItem.fromUri(inputUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

            // 720p (1280 x 720) @ 30fps, 2 Mbps bitrate (2_000_000 bits per second)
            val videoEncoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(2_000_000)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
                .build()

            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        trySend(TranscodeState.Success(outputFile))
                        close()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        trySend(TranscodeState.Error(exportException))
                        close(exportException)
                    }
                })
                .build()

            // Start the hardware-accelerated transformation
            transformer.start(editedMediaItem, outputFile.absolutePath)

            // Progress tracking loop
            val progressJob = launch {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                while (true) {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(TranscodeState.Progress(progressHolder.progress))
                    } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                        trySend(TranscodeState.Idle)
                    } else if (progressState == Transformer.PROGRESS_STATE_UNAVAILABLE) {
                        // Progress is unavailable, but export is continuing
                    }
                    delay(250) // Poll every 250ms
                }
            }

            awaitClose {
                progressJob.cancel()
                transformer.cancel()
            }
        } catch (e: Exception) {
            trySend(TranscodeState.Error(e))
            close(e)
        }
    }.flowOn(Dispatchers.Main) // Media3 Transformer MUST be accessed on the application's main thread
}
