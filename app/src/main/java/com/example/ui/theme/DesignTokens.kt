package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

object PlenxoColors {
    val Primary = PlenxoElectricViolet
    val Secondary = PlenxoNeonCyan
    val Background = PlenxoDeepSpace
    val Surface = PlenxoGlassSurface
    val SurfaceCard = PlenxoCardSurfaceDark
    val TextPrimary = PlenxoTextWhite
    val TextSecondary = PlenxoTextMutedDark
    val Error = PlenxoError
    val Success = PlenxoSuccess
    val Divider = Color(0x33FFFFFF)
}

object PlenxoSpacing {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
}

object PlenxoTypography {
    val Title = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = PlenxoColors.TextPrimary
    )
    val Body = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = PlenxoColors.TextPrimary
    )
    val Label = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = PlenxoColors.TextSecondary
    )
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = PlenxoColors.TextSecondary
    )
}
