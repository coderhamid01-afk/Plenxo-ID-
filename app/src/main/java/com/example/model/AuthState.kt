package com.example.model

enum class AuthState {
    UNAUTHENTICATED,
    VERIFYING_OTP,
    NEEDS_PROFILE_SETUP,
    AUTHENTICATED
}
