package com.example.webrtc

import android.app.Application
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.util.UUID

class CallViewModel(application: Application) : AndroidViewModel(application), CallRepository.CallSignalingListener {

    private val eglBase = EglBase.create()
    private val webRtcClient = WebRtcClient(application, eglBase, object : PeerConnectionObserver() {
        override fun onIceCandidate(candidate: IceCandidate?) {
            super.onIceCandidate(candidate)
            candidate?.let {
                repository.sendIceCandidate(callId, isCaller, it)
            }
        }
    })
    
    private val repository = CallRepository()
    private val callAudioManager = CallAudioManager(application)
    
    var callId: String = ""
    var isCaller: Boolean = false
    var isVideoCall: Boolean = false
    var peerId: String = ""
    var peerName: String = ""
    var peerAvatar: String = ""
    var currentUserId: String = ""
    
    private val _callStatus = MutableStateFlow("idle")
    val callStatus: StateFlow<String> = _callStatus
    
    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled
    
    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn
    
    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration
    
    private var durationJob: Job? = null
    private var timeoutJob: Job? = null
    private var ringbackPlayer: MediaPlayer? = null
    private var ringtonePlayer: MediaPlayer? = null
    
    private var startedAt: Long = 0L

    fun initEglBaseContext(): EglBase.Context {
        return eglBase.eglBaseContext
    }

    fun startOutgoingCall(
        callerId: String, callerName: String, callerAvatar: String,
        receiverId: String, receiverName: String, receiverAvatar: String,
        isVideo: Boolean
    ) {
        currentUserId = callerId
        peerId = receiverId
        peerName = receiverName
        peerAvatar = receiverAvatar
        isVideoCall = isVideo
        isCaller = true
        callId = UUID.randomUUID().toString()
        
        _callStatus.value = "calling"
        
        repository.startCall(
            callId, callerId, callerName, callerAvatar,
            receiverId, receiverName, receiverAvatar,
            if (isVideo) "video" else "audio"
        )
        repository.subscribeToCallEvents(callId, true, this)
        
        playRingback()
        startTimeoutTimer()
        
        setupWebRTC()
    }
    
    fun answerIncomingCall(
        existingCallId: String,
        myId: String,
        callerId: String,
        callerName: String,
        callerAvatar: String,
        isVideo: Boolean
    ) {
        currentUserId = myId
        peerId = callerId
        peerName = callerName
        peerAvatar = callerAvatar
        isVideoCall = isVideo
        isCaller = false
        callId = existingCallId
        
        stopRingtone()
        _callStatus.value = "accepted"
        repository.updateCallStatus(callId, "accepted")
        repository.subscribeToCallEvents(callId, false, this)
        
        setupWebRTC()
    }
    
    fun rejectIncomingCall(existingCallId: String) {
        stopRingtone()
        repository.updateCallStatus(existingCallId, "rejected")
        _callStatus.value = "rejected"
    }
    
    fun handleIncomingCallRinging(existingCallId: String, isVideo: Boolean) {
        callId = existingCallId
        isVideoCall = isVideo
        _callStatus.value = "ringing"
        repository.updateCallStatus(callId, "ringing")
        playRingtone()
    }
    
    private fun setupWebRTC() {
        webRtcClient.initializePeerConnection()
        callAudioManager.startCall(isVideoCall)
        _isSpeakerphoneOn.value = callAudioManager.isSpeakerphoneOn()
        _isVideoEnabled.value = isVideoCall
        
        if (isCaller) {
            createOffer()
        }
    }
    
    fun attachLocalVideo(surfaceViewRenderer: SurfaceViewRenderer) {
        webRtcClient.startLocalVideo(surfaceViewRenderer, isVideoCall)
    }

    fun attachRemoteVideo(surfaceViewRenderer: SurfaceViewRenderer) {
        // Find the remote video track when it's added.
        // We need to implement onAddTrack in PeerConnectionObserver to attach to the surface
        webRtcClient.peerConnection?.transceivers?.forEach { transceiver: org.webrtc.RtpTransceiver ->
            val receiverId = transceiver.receiver
            if (receiverId.track()?.kind() == "video") {
                val track = receiverId.track() as? org.webrtc.VideoTrack
                track?.addSink(surfaceViewRenderer)
            }
        }
    }

    private fun createOffer() {
        webRtcClient.call(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    webRtcClient.setLocalDescription(this, it)
                    repository.sendOffer(callId, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        })
    }

    override fun onOfferReceived(sdp: SessionDescription) {
        webRtcClient.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAnswer()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    private fun createAnswer() {
        webRtcClient.answer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    webRtcClient.setLocalDescription(this, it)
                    repository.sendAnswer(callId, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        })
    }

    override fun onAnswerReceived(sdp: SessionDescription) {
        webRtcClient.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        webRtcClient.addIceCandidate(iceCandidate)
    }

    override fun onCallStatusChanged(status: String) {
        _callStatus.value = status.lowercase()
        when (status.lowercase()) {
            "accepted" -> {
                stopRingback()
                timeoutJob?.cancel()
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
                startDurationTimer()
                com.example.service.VoiceCallService.startService(
                    getApplication(),
                    callId,
                    peerId,
                    peerName,
                    peerAvatar,
                    if (isVideoCall) "Video" else "Audio"
                )
            }
            "rejected", "ended", "timeout", "busy" -> {
                stopRingback()
                stopRingtone()
                timeoutJob?.cancel()
                durationJob?.cancel()
                saveCallLog(status)
                cleanup()
            }
        }
    }
    
    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
        webRtcClient.toggleMic(_isMicMuted.value)
    }
    
    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
        webRtcClient.toggleVideo(_isVideoEnabled.value)
    }
    
    fun switchCamera() {
        webRtcClient.switchCamera()
    }
    
    fun toggleSpeakerphone() {
        _isSpeakerphoneOn.value = !_isSpeakerphoneOn.value
        callAudioManager.setSpeakerphoneOn(_isSpeakerphoneOn.value)
    }

    fun resetCall() { _callStatus.value = "idle"; cleanup() }
    fun endCall() {
        repository.updateCallStatus(callId, "ended")
        _callStatus.value = "ended"
        stopRingback()
        stopRingtone()
        timeoutJob?.cancel()
        durationJob?.cancel()
        saveCallLog("ended")
        cleanup()
    }

    private fun playRingback() {
        // Use default ringtone for now or raw resource
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringbackPlayer = MediaPlayer.create(getApplication(), uri)
            ringbackPlayer?.isLooping = true
            ringbackPlayer?.start()
        } catch (e: Exception) {
            Log.e("CallViewModel", "Ringback error", e)
        }
    }
    
    private fun stopRingback() {
        ringbackPlayer?.stop()
        ringbackPlayer?.release()
        ringbackPlayer = null
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer.create(getApplication(), uri)
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
        } catch (e: Exception) {
            Log.e("CallViewModel", "Ringtone error", e)
        }
    }

    private fun stopRingtone() {
        ringtonePlayer?.stop()
        ringtonePlayer?.release()
        ringtonePlayer = null
    }

    private fun startTimeoutTimer() {
        timeoutJob = viewModelScope.launch {
            delay(30000) // 30 seconds
            if (_callStatus.value == "calling" || _callStatus.value == "ringing") {
                repository.updateCallStatus(callId, "timeout")
                _callStatus.value = "timeout"
            }
        }
    }

    private fun startDurationTimer() {
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDuration.value = (System.currentTimeMillis() - startedAt) / 1000
            }
        }
    }

    private fun saveCallLog(status: String) {
        if (callId.isEmpty() || currentUserId.isEmpty() || peerId.isEmpty()) return
        
        val endTime = System.currentTimeMillis()
        val duration = if (startedAt > 0) (endTime - startedAt) / 1000 else 0
        
        val callLog = mapOf(
            "callId" to callId,
            "peerName" to peerName,
            "peerAvatar" to peerAvatar,
            "callType" to if (isVideoCall) "video" else "audio",
            "status" to status,
            "startTime" to startedAt,
            "endTime" to endTime,
            "durationSeconds" to duration,
            "timestamp" to endTime
        )
        
        repository.logCallHistory(currentUserId, peerId, callLog)
    }

    private fun cleanup() {
        try {
            com.example.service.VoiceCallService.stopService(getApplication())
        } catch (_: Exception) {}
        webRtcClient.close()
        repository.cleanup()
        callAudioManager.cleanup()
        eglBase.release()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
