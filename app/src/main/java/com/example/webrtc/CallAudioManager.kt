package com.example.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class CallAudioManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneState: Boolean = false

    fun startCall(isVideoCall: Boolean) {
        previousAudioMode = audioManager.mode
        previousSpeakerphoneState = audioManager.isSpeakerphoneOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        if (isVideoCall) {
            setSpeakerphoneOn(true)
        } else {
            setSpeakerphoneOn(false)
        }
    }

    fun setSpeakerphoneOn(isOn: Boolean) {
        audioManager.isSpeakerphoneOn = isOn
    }

    fun isSpeakerphoneOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }

    fun cleanup() {
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerphoneState
    }
}
