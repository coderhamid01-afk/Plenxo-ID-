package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.PlenxoViewModel

@Composable
fun WallpaperItem(
    id: String,
    label: String,
    selectedWallpaper: String,
    accentBlue: Color,
    strokeBorder: Color,
    textWhite: Color,
    textMuted: Color,
    triggerHaptic: () -> Unit,
    weChatViewModel: PlenxoViewModel
) {
    val isSelected = selectedWallpaper == id
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color(0xFF2E3B5E), RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accentBlue else strokeBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    triggerHaptic()
                    weChatViewModel.updateSelectedChatWallpaper(id)
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(accentBlue, RoundedCornerShape(6.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) textWhite else textMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
