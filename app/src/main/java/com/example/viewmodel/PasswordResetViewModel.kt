package com.example.viewmodel

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface PasswordResetState {
    object Idle : PasswordResetState
    object Loading : PasswordResetState
    data class Success(val message: String) : PasswordResetState
    data class Error(val message: String) : PasswordResetState
}

class PasswordResetViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val state: StateFlow<PasswordResetState> = _state.asStateFlow()

    fun sendPasswordResetEmail(email: String) {
        val trimmedEmail = email.trim()

        if (trimmedEmail.isEmpty()) {
            _state.value = PasswordResetState.Error("Please enter your email address.")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _state.value = PasswordResetState.Error("Please enter a valid email address.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = PasswordResetState.Loading
            try {
                auth.sendPasswordResetEmail(trimmedEmail).await()
                Log.d("PasswordResetVM", "Password reset email sent to $trimmedEmail")
                withContext(Dispatchers.Main) {
                    _state.value = PasswordResetState.Success(
                        "Password reset email sent! Check your inbox for instructions."
                    )
                }
            } catch (e: Exception) {
                Log.e("PasswordResetVM", "Failed to send password reset email: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("no user record", ignoreCase = true) == true ->
                        "No account found with this email address."
                    e.message?.contains("invalid email", ignoreCase = true) == true ->
                        "Invalid email format."
                    e.message?.contains("network error", ignoreCase = true) == true ->
                        "Network error. Please check your internet connection."
                    else -> e.localizedMessage ?: "Failed to send password reset email. Please try again."
                }
                withContext(Dispatchers.Main) {
                    _state.value = PasswordResetState.Error(errorMessage)
                }
            }
        }
    }

    fun resetState() {
        _state.value = PasswordResetState.Idle
    }
}
