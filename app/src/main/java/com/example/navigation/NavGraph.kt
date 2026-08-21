package com.example.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.model.AuthState
import com.example.ui.PlenxoAppContent
import com.example.util.PermissionManager
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.google.firebase.auth.FirebaseAuth

/**
 * Plenxo Navigation Graph checking Firebase currentUser:
 * If null -> Start on LOGIN.
 * If non-null -> Start on HOME.
 */
@Composable
fun PlenxoNavGraph(
    viewModel: PlenxoViewModel,
    permissionManager: PermissionManager,
    modifier: Modifier = Modifier
) {
    val authenticatedUserId by viewModel.authenticatedUserId.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val authState by viewModel.authState.collectAsState()

    // Synchronize screen navigation with AuthState lifecycle
    androidx.compose.runtime.LaunchedEffect(authState) {
        when (authState) {
            AuthState.UNAUTHENTICATED -> {
                if (currentScreen != PlenxoScreen.LOGIN &&
                    currentScreen != PlenxoScreen.SIGNUP &&
                    currentScreen != PlenxoScreen.FORGOT_PASSWORD
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.VERIFYING_OTP -> {
                if (currentScreen != PlenxoScreen.OTP_VERIFICATION) {
                    viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.NEEDS_PROFILE_SETUP -> {
                if (currentScreen != PlenxoScreen.PROFILE_SETUP &&
                    currentScreen != PlenxoScreen.AVATAR_SETUP &&
                    currentScreen != PlenxoScreen.FINAL_DETAILS
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.AUTHENTICATED -> {
                if (currentScreen == PlenxoScreen.LOGIN ||
                    currentScreen == PlenxoScreen.SIGNUP ||
                    currentScreen == PlenxoScreen.OTP_VERIFICATION ||
                    currentScreen == PlenxoScreen.PROFILE_SETUP ||
                    currentScreen == PlenxoScreen.AVATAR_SETUP ||
                    currentScreen == PlenxoScreen.FINAL_DETAILS ||
                    currentScreen == PlenxoScreen.WELCOME
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                }
            }
        }
    }

    Crossfade(
        targetState = currentScreen,
        animationSpec = tween(durationMillis = 300),
        modifier = modifier,
        label = "nav_graph_transition"
    ) { screen ->
        when (screen) {
            PlenxoScreen.PERMISSION_GATEWAY -> {
                viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
            }
            else -> {
                PlenxoAppContent(
                    viewModel = viewModel,
                    permissionManager = permissionManager
                )
            }
        }
    }
}
