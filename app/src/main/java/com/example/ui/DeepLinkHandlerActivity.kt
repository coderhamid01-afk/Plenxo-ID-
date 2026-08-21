package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainActivity
import com.example.ui.theme.PlenxoTheme
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.AuthUiState
import com.example.viewmodel.AuthViewModel

class DeepLinkHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingUri = intent?.data
        Log.d("DeepLinkHandler", "Received deep link uri: $incomingUri")

        setContent {
            PlenxoTheme {
                DeepLinkHandlerScreen(
                    uri = incomingUri,
                    onNavigateToMain = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onNavigateToLogin = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("NAVIGATE_TO", "LOGIN")
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun DeepLinkHandlerScreen(
    uri: Uri?,
    onNavigateToMain: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var statusText by remember { mutableStateOf("Processing security link...") }
    var isVerifying by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) {
            statusText = "Error: Invalid or empty redirection link."
            isVerifying = false
            return@LaunchedEffect
        }

        val uriString = uri.toString()
        Log.d("DeepLinkHandler", "Processing deep link: $uriString")
        
        statusText = "Authenticating secure session..."
        viewModel.verifyDeepLink(uriString)
    }

    // React to ViewModel UI State changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.SuccessDirect -> {
                statusText = "Authenticated Successfully! Redirecting to Plenxo Home Dashboard..."
                isVerifying = false
                kotlinx.coroutines.delay(2000)
                onNavigateToMain()
            }
            is AuthUiState.Error -> {
                statusText = (uiState as AuthUiState.Error).message
                isVerifying = false
            }
            is AuthUiState.Loading -> {
                isVerifying = true
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Matches Plenxo's Deep Space Black Theme
    ) {
        // Subtle background radial gradient for premium feel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x228A2BE2), Color(0x000A0A0A)),
                        radius = 1000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glassmorphism verification card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFF8A2BE2), Color(0xFF00FFFF))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = isVerifying,
                        enter = fadeIn(animationSpec = tween(500)),
                        exit = fadeOut(animationSpec = tween(500))
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00FFFF), // Cyan loading color
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = !isVerifying && uiState is AuthUiState.SuccessDirect,
                        enter = fadeIn(animationSpec = tween(500))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF00FFFF), // Plenxo Cyan Accent
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = !isVerifying && (uiState is AuthUiState.Error || uri == null),
                        enter = fadeIn(animationSpec = tween(500))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFFF3B30), // Warning Red
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isVerifying) {
                            "Securing Session"
                        } else if (uiState is AuthUiState.SuccessDirect) {
                            "Security Cleared"
                        } else {
                            "Verification Failed"
                        },
                        style = PlenxoTypography.Title.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = statusText,
                        style = PlenxoTypography.Body.copy(
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (!isVerifying && uiState is AuthUiState.Error) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onNavigateToLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(stringResource(R.string.str_return_to_login_screen),
                                color = Color.White,
                                style = PlenxoTypography.Body.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
