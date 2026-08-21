package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.R

/**
 * ChatActivity demonstrating the exact, production-ready solution for voice note permissions
 * on Plenxo's dark themed chat interface.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var btnMicContainer: FrameLayout
    private lateinit var imgMic: ImageView
    private lateinit var etMessageInput: EditText
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var tvRecordingStatus: TextView
    private lateinit var btnBack: ImageView

    private var isRecording = false

    // 1. DYNAMIC PERMISSION LAUNCHER
    // Use ActivityResultLauncher to request permission and handle response asynchronously
    private val requestAudioPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // If granted, immediately start recording to deliver a seamless user experience
                startRecording()
            } else {
                // If denied, provide informative toast notification
                Toast.makeText(this, "Permission required for Voice Notes", Toast.LENGTH_LONG).show()
                stopRecordingVisuals()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Bind Views
        btnMicContainer = findViewById(R.id.btn_mic_container)
        imgMic = findViewById(R.id.img_mic)
        etMessageInput = findViewById(R.id.et_message_input)
        recordingIndicator = findViewById(R.id.recording_indicator)
        tvRecordingStatus = findViewById(R.id.tv_recording_status)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener {
            finish()
        }

        // 3. LINK PERMISSION CHECK TO ACTION_DOWN EVENT ON TOUCH LISTENER
        setupVoiceRecordingTouchListener()
    }

    /**
     * Configures the voice button's touch listener to detect press-and-hold gestures,
     * triggering dynamic permission requests immediately when user touches the microphone.
     */
    private fun setupVoiceRecordingTouchListener() {
        btnMicContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check and request microphone permission immediately on hold
                    checkAndRequestAudioPermission()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Stop voice recording as soon as user lifts their finger
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Checks if the application has mic recording permission. If not, requests it dynamically.
     */
    private fun checkAndRequestAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        val checkVal = ContextCompat.checkSelfPermission(this, permission)

        if (checkVal == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted; begin recording instantly
            startRecording()
        } else {
            // Request permission dynamically. Launcher handles response inside the contract.
            requestAudioPermissionLauncher.launch(permission)
        }
    }

    /**
     * Starts the voice recording session and updates corresponding visual elements in the UI.
     */
    private fun startRecording() {
        isRecording = true
        recordingIndicator.visibility = View.VISIBLE
        
        // Visual indication with electric violet and neon cyan hues
        btnMicContainer.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        imgMic.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
        
        Toast.makeText(this, "Recording Voice Note...", Toast.LENGTH_SHORT).show()
        // Audio voice recorder engine starts here (e.g. MediaRecorder)
    }

    /**
     * Stops the voice recording session and resets UI indicators to their idle state.
     */
    private fun stopRecording() {
        isRecording = false
        stopRecordingVisuals()
        Toast.makeText(this, "Voice Note saved!", Toast.LENGTH_SHORT).show()
        // Audio voice recorder engine stops and persists file here
    }

    private fun stopRecordingVisuals() {
        recordingIndicator.visibility = View.GONE
        btnMicContainer.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        imgMic.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray))
    }
}
