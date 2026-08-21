package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

object LegalWebUtils {
    const val PRIVACY_POLICY_URL = "https://coderhamid01-afk.github.io/Term/privacy.html"
    const val TERMS_CONDITIONS_URL = "https://coderhamid01-afk.github.io/Term/terms.html"

    fun openUrl(context: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            if (context !is android.app.Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
