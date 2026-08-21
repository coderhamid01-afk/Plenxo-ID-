package com.example.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class CallRepository {
    private val db = FirebaseFirestore.getInstance()
    private var callListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null

    interface CallSignalingListener {
        fun onOfferReceived(sdp: SessionDescription)
        fun onAnswerReceived(sdp: SessionDescription)
        fun onIceCandidateReceived(iceCandidate: IceCandidate)
        fun onCallStatusChanged(status: String)
    }

    fun startCall(
        callId: String, 
        callerId: String, 
        callerName: String, 
        callerAvatar: String,
        receiverId: String, 
        receiverName: String, 
        receiverAvatar: String, 
        callType: String
    ) {
        val callData = mapOf(
            "callId" to callId,
            "callerUid" to callerId,
            "callerName" to callerName,
            "callerAvatar" to callerAvatar,
            "receiverUid" to receiverId,
            "receiverName" to receiverName,
            "receiverAvatar" to receiverAvatar,
            "callType" to callType,
            "status" to "RINGING",
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("calls").document(callId).set(callData)
    }

    fun subscribeToCallEvents(callId: String, isCaller: Boolean, listener: CallSignalingListener) {
        callListener = db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CallRepo", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: ""
                    listener.onCallStatusChanged(status)

                    if (!isCaller && status.equals("calling", true) || status.equals("ringing", true)) {
                        val offerStr = snapshot.getString("offer")
                        if (!offerStr.isNullOrEmpty()) {
                            listener.onOfferReceived(SessionDescription(SessionDescription.Type.OFFER, offerStr))
                        }
                    } else if (isCaller && status.equals("accepted", true)) {
                        val answerStr = snapshot.getString("answer")
                        if (!answerStr.isNullOrEmpty()) {
                            listener.onAnswerReceived(SessionDescription(SessionDescription.Type.ANSWER, answerStr))
                        }
                    }
                }
            }

        val collectionName = if (isCaller) "receiverCandidates" else "callerCandidates"
        candidateListener = db.collection("calls").document(callId).collection(collectionName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val candidate = IceCandidate(
                            data["sdpMid"] as String,
                            (data["sdpMLineIndex"] as Long).toInt(),
                            data["sdpCandidate"] as String
                        )
                        listener.onIceCandidateReceived(candidate)
                    }
                }
            }
    }

    fun sendOffer(callId: String, sdp: SessionDescription) {
        db.collection("calls").document(callId).update("offer", sdp.description)
    }

    fun sendAnswer(callId: String, sdp: SessionDescription) {
        db.collection("calls").document(callId).update("answer", sdp.description)
    }

    fun sendIceCandidate(callId: String, isCaller: Boolean, iceCandidate: IceCandidate) {
        val collectionName = if (isCaller) "callerCandidates" else "receiverCandidates"
        val candidateMap = mapOf(
            "sdpMid" to iceCandidate.sdpMid,
            "sdpMLineIndex" to iceCandidate.sdpMLineIndex,
            "sdpCandidate" to iceCandidate.sdp
        )
        db.collection("calls").document(callId).collection(collectionName).add(candidateMap)
    }

    fun updateCallStatus(callId: String, status: String) {
        val updateMap = mutableMapOf<String, Any>("status" to status)
        if (status.equals("accepted", true)) {
            updateMap["startedAt"] = System.currentTimeMillis()
        } else if (status == "ended" || status == "rejected" || status == "timeout" || status == "busy") {
            updateMap["endedAt"] = System.currentTimeMillis()
        }
        db.collection("calls").document(callId).update(updateMap)
    }
    
    fun logCallHistory(
        callerId: String, 
        receiverId: String, 
        callData: Map<String, Any>
    ) {
        val callerLog = callData.toMutableMap()
        callerLog["peerId"] = receiverId
        // The peer details should be mapped properly in the viewModel before passing
        db.collection("users").document(callerId).collection("call_history").add(callerLog)
        
        val receiverLog = callData.toMutableMap()
        receiverLog["peerId"] = callerId
        db.collection("users").document(receiverId).collection("call_history").add(receiverLog)
    }

    fun cleanup() {
        callListener?.remove()
        candidateListener?.remove()
    }
}
