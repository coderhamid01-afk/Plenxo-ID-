package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography
import com.example.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun AuthWrapper(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedAuthBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_plenxo_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(PlenxoSpacing.Large))
            
            Text(
                text = title,
                style = PlenxoTypography.Title.copy(color = Color.White),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(PlenxoSpacing.ExtraLarge))
            
            // Glassmorphism Card
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)), // Glass effect
                border = BorderStroke(1.dp, Brush.horizontalGradient(
                    listOf(Color(0x338A2BE2), Color(0x3300FFFF))
                ))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    content()
                }
            }
        }
    }
}
