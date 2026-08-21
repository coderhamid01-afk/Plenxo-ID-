package com.example.repository

import android.content.Context

sealed interface AccountDeletionUiState {
    object Idle : AccountDeletionUiState
    object ShowPasswordDialog : AccountDeletionUiState
    object ShowWarningDialog : AccountDeletionUiState
    object Deleting : AccountDeletionUiState
    object Success : AccountDeletionUiState
    data class Error(val message: String) : AccountDeletionUiState
}

interface AccountDeletionRepository {
    suspend fun reauthenticateUser(password: String): Result<Unit>
    suspend fun executeFullAccountDeletion(context: Context): Result<Unit>
}
