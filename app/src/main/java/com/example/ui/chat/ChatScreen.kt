package com.example.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.util.PermissionManager
import com.example.viewmodel.PlenxoViewModel

/**
 * UNUSED / DEAD CODE NOTICE:
 * This file is currently unused/dead code and is not referenced from NavGraph.kt or PlenxoAppContent.kt.
 * The active implementation lives in ui/chat/ChatDetailScreen.kt driven directly by PlenxoViewModel.
 */
@Composable
fun ChatScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color = Color(0xFF58A6FF),
    permissionManager: PermissionManager
) {
    ChatDetailScreen(
        viewModel = viewModel,
        primaryColor = primaryColor,
        permissionManager = permissionManager
    )
}
