package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography

val DefaultSpringSpec = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 400f
)

@Composable
fun PlenxoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = PlenxoColors.Primary,
            contentColor = PlenxoColors.Surface,
            disabledContainerColor = PlenxoColors.Primary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = PlenxoColors.Surface,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(text, style = PlenxoTypography.Body.copy(color = PlenxoColors.Surface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlenxoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    isDark: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    singleLine: Boolean = true
) {
    val textColor = if (isDark) androidx.compose.ui.graphics.Color.White else PlenxoColors.TextPrimary
    val labelColor = if (isDark) androidx.compose.ui.graphics.Color.LightGray else PlenxoColors.TextSecondary
    val borderColor = if (isDark) androidx.compose.ui.graphics.Color(0x4DFFFFFF) else PlenxoColors.Divider

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = PlenxoTypography.Label.copy(color = labelColor)) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    keyboardController?.show()
                }
            },
        enabled = enabled,
        isError = isError,
        readOnly = readOnly,
        singleLine = singleLine,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedBorderColor = PlenxoColors.Primary,
            unfocusedBorderColor = borderColor,
            focusedLabelColor = PlenxoColors.Primary,
            unfocusedLabelColor = labelColor,
            focusedContainerColor = if (isDark) androidx.compose.ui.graphics.Color(0x1A000000) else androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = if (isDark) androidx.compose.ui.graphics.Color(0x1A000000) else androidx.compose.ui.graphics.Color.Transparent,
            errorBorderColor = PlenxoColors.Error
        ),
        textStyle = PlenxoTypography.Body.copy(color = textColor)
    )
}

@Composable
fun PlenxoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PlenxoColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(PlenxoSpacing.Medium),
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = { 
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = PlenxoColors.TextSecondary) 
    }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PlenxoSpacing.Medium, vertical = PlenxoSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PlenxoColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(PlenxoSpacing.Medium))
        Text(
            text = title,
            style = PlenxoTypography.Body,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}
