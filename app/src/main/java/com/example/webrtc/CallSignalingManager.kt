package com.example.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class CallSignalingManager(
    val callId: String = "",
    val callerUid: String = "",
    val receiverUid: String = "",
    val listener: SignalingListener? = null
) {
    interface SignalingListener {
        fun onCallOffer(senderUid: String, sdp: String, callType: String) {}
        fun onCallRinging(senderUid: String) {}
        fun onCallAnswer(senderUid: String, sdp: String) {}
        fun onCallBusy(senderUid: String) {}
        fun onCallReject(senderUid: String) {}
        fun onCallEnd(senderUid: String) {}
        fun onCallAccepted(senderUid: String) {}
    }

    private val db = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    fun startListening() {
        if (callId.isEmpty()) return
        listenerRegistration?.remove()
        listenerRegistration = db.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CallSignalingManager", "Listen failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: ""
                    val caller = snapshot.getString("callerUid") ?: ""
                    val receiver = snapshot.getString("receiverUid") ?: ""
                    val sdpOffer = snapshot.getString("sdpOffer") ?: ""
                    val sdpAnswer = snapshot.getString("sdpAnswer") ?: ""
                    val callType = snapshot.getString("callType") ?: "AUDIO"

                    when (status) {
                        "RINGING" -> {
                            listener?.onCallRinging(caller)
                            if (sdpOffer.isNotEmpty()) {
                                listener?.onCallOffer(caller, sdpOffer, callType)
                            }
                        }
                        "ACCEPTED" -> {
                            listener?.onCallAccepted(receiver)
                            if (sdpAnswer.isNotEmpty()) {
                                listener?.onCallAnswer(receiver, sdpAnswer)
                            }
                        }
                        "REJECTED" -> {
                            listener?.onCallReject(receiver)
                        }
                        "BUSY" -> {
                            listener?.onCallBusy(receiver)
                        }
                        "ENDED" -> {
                            listener?.onCallEnd(caller)
                        }
                    }
                }
            }
    }

    fun sendOffer(sdp: String, callType: String) {
        if (callId.isEmpty()) return
        val callData = mapOf(
            "callId" to callId,
            "callerUid" to callerUid,
            "receiverUid" to receiverUid,
            "callType" to callType,
            "status" to "RINGING",
            "sdpOffer" to sdp,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("calls").document(callId).set(callData, SetOptions.merge())
    }

    fun sendRinging() {
        if (callId.isEmpty()) return
        db.collection("calls").document(callId).update("status", "RINGING")
    }

    fun sendAnswer(sdp: String) {
        if (callId.isEmpty()) return
        val callData = mapOf(
            "status" to "ACCEPTED",
            "sdpAnswer" to sdp
        )
        db.collection("calls").document(callId).update(callData)
    }

    fun sendBusy() {
        if (callId.isEmpty()) return
        db.collection("calls").document(callId).update("status", "BUSY")
    }

    fun sendReject() {
        if (callId.isEmpty()) return
        db.collection("calls").document(callId).update("status", "REJECTED")
    }

    fun sendEnd() {
        if (callId.isEmpty()) return
        db.collection("calls").document(callId).update("status", "ENDED")
    }

    fun cleanup() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
