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

/**
 * Clean Plenxo Navigation Graph:
 * Pure OTP & Plenxo ID flow routed cleanly with safeguard.
 */
@Composable
fun PlenxoNavGraph(
    viewModel: PlenxoViewModel,
    permissionManager: PermissionManager,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val authState by viewModel.authState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(authState, currentScreen) {
        when (authState) {
            AuthState.UNAUTHENTICATED -> {
                if (currentScreen != PlenxoScreen.LOGIN &&
                    currentScreen != PlenxoScreen.SIGN_UP &&
                    currentScreen != PlenxoScreen.PLACEHOLDER_ENTRY
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.VERIFYING_OTP -> {
                if (currentScreen != PlenxoScreen.OTP_VERIFICATION &&
                    currentScreen != PlenxoScreen.PLENXO_ID_REVEAL &&
                    currentScreen != PlenxoScreen.PLACEHOLDER_ENTRY
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.NEEDS_PROFILE_SETUP -> {
                if (currentScreen != PlenxoScreen.PLENXO_ID_REVEAL &&
                    currentScreen != PlenxoScreen.PLACEHOLDER_ENTRY
                ) {
                    viewModel.navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL, addToHistory = false, clearHistory = true)
                }
            }
            AuthState.AUTHENTICATED -> {
                if (currentScreen == PlenxoScreen.OTP_VERIFICATION ||
                    currentScreen == PlenxoScreen.LOGIN ||
                    currentScreen == PlenxoScreen.SIGN_UP ||
                    currentScreen == PlenxoScreen.PLACEHOLDER_ENTRY
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
