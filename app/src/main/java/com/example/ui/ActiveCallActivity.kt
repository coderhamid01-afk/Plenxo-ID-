package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity

/*
 * ActiveCallActivity is temporarily disabled.
 */
open class ActiveCallActivity : ComponentActivity() {
    companion object {
        const val ACTION_END_CALL_BROADCAST = "com.example.ACTION_END_CALL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
