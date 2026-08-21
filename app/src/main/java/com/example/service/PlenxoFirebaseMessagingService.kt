package com.example.service

import com.google.firebase.auth.FirebaseAuth

class PlenxoFirebaseMessagingService : PlenxoFCMService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        updateFcmTokenInDatabase(uid, token)
    }
}
