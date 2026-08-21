package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.util.PermissionManager
import com.example.viewmodel.PlenxoViewModel

/**
 * Backwards compatibility delegate forwarding to the refactored non-blocking
 * ChatDetailScreen in `com.example.ui.chat.ChatDetailScreen`.
 */
@Composable
fun ChatDetailScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color,
    permissionManager: PermissionManager
) {
    com.example.ui.chat.ChatDetailScreen(
        viewModel = viewModel,
        primaryColor = primaryColor,
        permissionManager = permissionManager
    )
}
