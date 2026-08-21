package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRtcClient(
    private val context: Context,
    private val eglBase: EglBase,
    private val observer: PeerConnection.Observer
) {
    private val tag = "WebRtcClient"
    
    private val peerConnectionFactory: PeerConnectionFactory
    var peerConnection: PeerConnection? = null
    
    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    init {
        initPeerConnectionFactory(context)
        peerConnectionFactory = createPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun initializePeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    fun startLocalVideo(surfaceViewRenderer: SurfaceViewRenderer, isVideoCall: Boolean) {
        if (!isVideoCall) {
            startLocalAudioOnly()
            return
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer = createVideoCapturer(context)
        
        localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast == true)
        videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("local_video_track", localVideoSource)
        localVideoTrack?.addSink(surfaceViewRenderer)

        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource)
        localAudioTrack?.setEnabled(true)

        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(localAudioTrack)
    }

    private fun startLocalAudioOnly() {
        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack)
    }

    private fun createVideoCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try front facing first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // Fallback to back facing
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleMic(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleVideo(isEnabled: Boolean) {
        localVideoTrack?.setEnabled(isEnabled)
    }

    fun call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(sdpObserver, constraints)
    }

    fun answer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createAnswer(sdpObserver, constraints)
    }

    fun setLocalDescription(observer: SdpObserver, sessionDescription: SessionDescription) {
        peerConnection?.setLocalDescription(observer, sessionDescription)
    }

    fun setRemoteDescription(observer: SdpObserver, sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(observer, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            localVideoSource?.dispose()
            localAudioSource?.dispose()
            surfaceTextureHelper?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnectionFactory.dispose()
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebRTC Client", e)
        }
    }
}
