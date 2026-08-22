package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.util.QRCodeUtils
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileQRBottomSheet(
    displayName: String,
    plenxoId: String,
    avatarUrl: String?,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    // Clean formatting for Plenxo ID
    val cleanPlenxoId = plenxoId.trim().removePrefix("@").removePrefix("#")
    val formattedPlenxoId = if (cleanPlenxoId.startsWith("PX-", ignoreCase = true)) cleanPlenxoId else "PX-$cleanPlenxoId"
    val profileUrl = "https://monumental-kangaroo-743f01.netlify.app/user/$cleanPlenxoId"

    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Asynchronously load avatar bitmap for center logo overlay on QR code
    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank()) {
            try {
                val imageLoader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .allowHardware(false) // Software bitmap required for Canvas drawing in QRCodeUtils
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        avatarBitmap = drawable.bitmap
                    }
                }
            } catch (e: Exception) {
                Log.w("ProfileQRBottomSheet", "Error loading avatar bitmap: ${e.message}")
            }
        }
    }

    // Generate Custom High-Res QR Code bitmap
    val qrBitmap = remember(profileUrl, avatarBitmap) {
        QRCodeUtils.generateCustomQRCode(
            data = profileUrl,
            logoBitmap = avatarBitmap,
            size = 512,
            darkColor = 0xFF1E1E2E.toInt(),
            lightColor = 0xFFFFFFFF.toInt()
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF131824),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30363D))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Title & Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Plenxo QR Code",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF0F6FC)
                )

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("close_qr_bottom_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF8B949E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Glassmorphism QR Card Container
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF58A6FF), CircleShape)
                            .background(Color(0xFF21262D)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Avatar Placeholder",
                                modifier = Modifier.size(40.dp),
                                tint = Color(0xFF8B949E)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Display Name
                    Text(
                        text = displayName.ifEmpty { "Plenxo User" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Permanent Plenxo ID Badge
                    Surface(
                        color = Color(0xFF58A6FF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF58A6FF).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "@$formattedPlenxoId",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF58A6FF),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // QR Code Card Container with white background padding for scan contrast
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Plenxo QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(12.dp)
                                .testTag("qr_code_image"),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Scan this QR code with any camera or Plenxo scanner to view profile",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Action Button 1: "Copy Profile Link"
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Plenxo Profile Link", profileUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Profile link copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("copy_profile_link_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF58A6FF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF58A6FF).copy(alpha = 0.1f),
                        contentColor = Color(0xFF58A6FF)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy Link",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action Button 2: "Share QR Image"
                Button(
                    onClick = {
                        try {
                            val qrDir = File(context.cacheDir, "qr_shares").apply { mkdirs() }
                            val imageFile = File(qrDir, "plenxo_qr_${cleanPlenxoId.ifBlank { "code" }}_${System.currentTimeMillis()}.png")
                            if (imageFile.exists()) {
                                imageFile.delete()
                            }
                            imageFile.createNewFile()
                            FileOutputStream(imageFile).use { stream ->
                                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                stream.flush()
                            }

                            val authority = "${context.packageName}.fileprovider"
                            val contentUri: Uri = FileProvider.getUriForFile(
                                context,
                                authority,
                                imageFile
                            )

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Connect with me on Plenxo! Profile Link: $profileUrl"
                                )
                                clipData = ClipData.newRawUri("Plenxo QR Code", contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            val chooserIntent = Intent.createChooser(shareIntent, "Share Plenxo Profile QR").apply {
                                clipData = ClipData.newRawUri("Plenxo QR Code", contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            try {
                                val resInfoList = context.packageManager.queryIntentActivities(
                                    chooserIntent,
                                    PackageManager.MATCH_DEFAULT_ONLY
                                )
                                for (resolveInfo in resInfoList) {
                                    val pkgName = resolveInfo.activityInfo.packageName
                                    context.grantUriPermission(
                                        pkgName,
                                        contentUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }
                            } catch (permEx: Exception) {
                                Log.w("ProfileQRBottomSheet", "Permission grant notice: ${permEx.message}")
                            }

                            context.startActivity(chooserIntent)
                        } catch (e: Exception) {
                            Log.e("ProfileQRBottomSheet", "Failed to share QR image: ${e.message}", e)
                            Toast.makeText(context, "Failed to share QR Code", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("share_qr_image_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF58A6FF),
                        contentColor = Color(0xFF0D1117)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share QR Image",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share QR",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
