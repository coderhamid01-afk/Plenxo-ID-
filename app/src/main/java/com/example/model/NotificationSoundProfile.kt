package com.example.model

import com.example.R

enum class NotificationSoundProfile(val displayName: String, val resId: Int, val systemKey: String) {
    CYBER_ALERT("Cyber Alert", R.raw.cyber_alert, "sound_cyber_alert"),
    MINIMAL_PING("Minimal Ping", R.raw.minimal_ping, "sound_minimal_ping"),
    RETRO_SYNTH("Retro Synth", R.raw.retro_synth, "sound_retro_synth"),
    AMBIENT_BREEZE("Ambient Breeze", R.raw.ambient_breeze, "sound_ambient_breeze"),
    ECHO_DROP("Echo Drop", R.raw.echo_drop, "sound_echo_drop");

    companion object {
        fun fromSystemKey(key: String?): NotificationSoundProfile {
            return entries.find { it.systemKey == key } ?: MINIMAL_PING
        }
    }
}
