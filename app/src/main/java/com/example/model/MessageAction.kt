package com.example.model

enum class MessageAction {
    REPLY,
    EDIT,
    COPY,
    FORWARD,
    DELETE,
    SET_EXPIRY
}

object MessageActionEvaluator {
    fun canEdit(message: Message, currentUserId: String): Boolean {
        if (message.senderId != currentUserId) return false
        val timestamp = message.timestamp ?: return false
        // 2-minute limit: 120,000 ms
        val elapsed = System.currentTimeMillis() - timestamp
        return elapsed <= 120000L
    }

    fun canCopy(message: Message): Boolean {
        return message.messageType == "TEXT"
    }
}
