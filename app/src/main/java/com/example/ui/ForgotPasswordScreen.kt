package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.PlenxoViewModel

@Composable
fun ForgotPasswordScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val authViewModel: AuthViewModel = viewModel()
    com.example.ui.screens.auth.ForgotPasswordScreen(
        authViewModel = authViewModel,
        onBackToLogin = { viewModel.navigateToLogin() },
        onCompleteReset = { email ->
            viewModel.completePasswordResetAndNavigateHome(email)
        }
    )
}


