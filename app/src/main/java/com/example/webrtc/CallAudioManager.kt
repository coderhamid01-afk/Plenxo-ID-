package com.example.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class CallAudioManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneState: Boolean = false
    private var isSpeakerEnabled: Boolean = false

    fun startCall(isVideoCall: Boolean) {
        previousAudioMode = audioManager.mode
        previousSpeakerphoneState = audioManager.isSpeakerphoneOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        setSpeakerphoneOn(isVideoCall)
    }

    fun setSpeakerphoneOn(isOn: Boolean) {
        isSpeakerEnabled = isOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (isOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val targetDevice = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
            if (targetDevice != null) {
                audioManager.setCommunicationDevice(targetDevice)
            } else {
                audioManager.isSpeakerphoneOn = isOn
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = isOn
        }
    }

    fun isSpeakerphoneOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || isSpeakerEnabled
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn || isSpeakerEnabled
        }
    }

    fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = previousAudioMode
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = previousSpeakerphoneState
    }
}

