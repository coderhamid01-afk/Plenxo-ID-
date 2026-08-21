package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Message
import com.example.model.MessageAction
import com.example.model.MessageActionEvaluator
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsMenu(
    message: Message,
    currentUserId: String,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit
) {
    val isEditable = remember(message) { MessageActionEvaluator.canEdit(message, currentUserId) }
    val isCopiable = remember(message) { MessageActionEvaluator.canCopy(message) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("message_actions_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = PlenxoColors.Surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(stringResource(id = R.string.str_message_actions),
                    style = PlenxoTypography.Title.copy(color = PlenxoColors.TextPrimary),
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                // Action Options List
                // 1. Reply
                ActionItemRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    title = "Reply",
                    subtitle = "Quote and reply to this message",
                    tag = "action_reply",
                    onClick = {
                        onAction(MessageAction.REPLY)
                        onDismiss()
                    }
                )

                // 2. Edit (Strictly conditional - under 2 mins & sent by current user)
                if (isEditable) {
                    HorizontalDivider(color = PlenxoColors.Background)
                    ActionItemRow(
                        icon = Icons.Default.Edit,
                        title = "Edit",
                        subtitle = "Modify this message (within 2m limit)",
                        tag = "action_edit",
                        onClick = {
                            onAction(MessageAction.EDIT)
                            onDismiss()
                        }
                    )
                }

                // 3. Copy (Strictly conditional - TEXT messages only)
                if (isCopiable) {
                    HorizontalDivider(color = PlenxoColors.Background)
                    ActionItemRow(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy Text",
                        subtitle = "Copy text to system clipboard",
                        tag = "action_copy",
                        onClick = {
                            onAction(MessageAction.COPY)
                            onDismiss()
                        }
                    )
                }

                // 4. Forward
                HorizontalDivider(color = PlenxoColors.Background)
                ActionItemRow(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    title = "Forward Message",
                    subtitle = "Send this message to other chats",
                    tag = "action_forward",
                    onClick = {
                        onAction(MessageAction.FORWARD)
                        onDismiss()
                    }
                )

                // 5. Delete
                HorizontalDivider(color = PlenxoColors.Background)
                ActionItemRow(
                    icon = Icons.Default.Delete,
                    title = "Delete",
                    subtitle = "Remove this message",
                    tag = "action_delete",
                    iconColor = Color(0xFFE57373),
                    onClick = {
                        onAction(MessageAction.DELETE)
                        onDismiss()
                    }
                )

                // 6. Set Expiry
                HorizontalDivider(color = PlenxoColors.Background)
                ActionItemRow(
                    icon = Icons.Default.Timer,
                    title = "Set Message Expiry",
                    subtitle = "Set self-destruct timer for this message",
                    tag = "action_set_expiry",
                    onClick = {
                        onAction(MessageAction.SET_EXPIRY)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("action_cancel")
                ) {
                    Text(stringResource(R.string.str_close), style = PlenxoTypography.Label.copy(color = PlenxoColors.Primary))
                }
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    iconColor: Color = PlenxoColors.TextSecondary,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            })
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PlenxoTypography.Body.copy(color = if (iconColor == Color(0xFFE57373)) Color(0xFFE57373) else PlenxoColors.TextPrimary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = PlenxoTypography.Caption.copy(color = PlenxoColors.TextSecondary)
            )
        }
    }
}
