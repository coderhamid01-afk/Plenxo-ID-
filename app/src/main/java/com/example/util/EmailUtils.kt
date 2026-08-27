package com.example.util

object EmailUtils {
    val ALLOWED_EMAIL_DOMAINS = listOf(
        "@gmail.com",
        "@outlook.com",
        "@hotmail.com",
        "@icloud.com",
        "@me.com",
        "@protonmail.com",
        "@zoho.com",
        "@mail.com"
    )

    const val INVALID_DOMAIN_ERROR_MESSAGE =
        "Only standard email providers like Gmail, Outlook, Hotmail, iCloud, me.com, ProtonMail, Zoho, and Mail.com are allowed."

    fun isAllowedEmailDomain(email: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) return false
        return ALLOWED_EMAIL_DOMAINS.any { cleanEmail.endsWith(it) }
    }
}
