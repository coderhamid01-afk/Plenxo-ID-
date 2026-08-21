package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.SecurityRepository
import com.example.ui.theme.PlenxoColors
import java.security.MessageDigest
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

class UnlockActivity : AppCompatActivity() {

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val securityRepo = SecurityRepository(this)
        
        val chatId = intent.getStringExtra("chatId")
        val isChatLock = chatId != null
        
        val savedHash = if (chatId != null) securityRepo.getChatLock(chatId) else securityRepo.getGlobalAppLock()
        val lockType = if (chatId != null) securityRepo.getChatLockType(chatId) ?: "PIN" else securityRepo.getLockType()

        if (savedHash == null) {
            setResult(android.app.Activity.RESULT_OK)
            finish()
            return
        }

        setContent {
            var password by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            
            val isViolation = intent.getBooleanExtra("SECURITY_VIOLATION", false) || com.example.util.AppLockManager.isPermanentlyLocked(this@UnlockActivity)

            LaunchedEffect(lockType) {
                if (lockType == "BIOMETRIC") {
                    com.example.util.SecurityManager.showPrompt(
                        activity = this@UnlockActivity,
                        title = if (isChatLock) "Unlock Chat" else "Unlock Plenxo",
                        subtitle = "Use your fingerprint or face to continue",
                        onSuccess = {
                            if (!isChatLock) {
                                com.example.util.AppLockObserver.isLockScreenShowing = false
                                com.example.util.AppLockManager.setLocked(this@UnlockActivity, false)
                                com.example.util.AppLockManager.setPermanentlyLocked(this@UnlockActivity, false)
                            }
                            setResult(android.app.Activity.RESULT_OK)
                            finish()
                        },
                        onError = { err ->
                            error = "Biometric authentication failed: $err"
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlenxoColors.Background)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isViolation) Color.Red else PlenxoColors.Primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (isViolation) "Security Risk Detected" else if (isChatLock) "Chat Locked" else "App Locked",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isViolation) Color.Red else Color.White
                )
                
                if (isViolation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(id = R.string.str_root_debugger_detected_authenticate_to),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                if (lockType == "BIOMETRIC") {
                    Text(
                        text = "Touch sensor or look at camera to unlock",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            com.example.util.SecurityManager.showPrompt(
                                activity = this@UnlockActivity,
                                title = if (isChatLock) "Unlock Chat" else "Unlock Plenxo",
                                subtitle = "Use your fingerprint or face to continue",
                                onSuccess = {
                                    if (!isChatLock) {
                                        com.example.util.AppLockObserver.isLockScreenShowing = false
                                        com.example.util.AppLockManager.setLocked(this@UnlockActivity, false)
                                        com.example.util.AppLockManager.setPermanentlyLocked(this@UnlockActivity, false)
                                    }
                                    setResult(android.app.Activity.RESULT_OK)
                                    finish()
                                },
                                onError = { err ->
                                    error = "Biometric error: $err"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary)
                    ) {
                        Text("Authenticate with Biometrics", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (lockType == "PATTERN") {
                    Text(stringResource(id = R.string.str_draw_your_pattern_to_unlock),
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    com.example.ui.components.PatternLockView(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        onPatternComplete = { pattern ->
                            if (sha256(pattern) == savedHash) {
                                if (!isChatLock) {
                                    com.example.util.AppLockObserver.isLockScreenShowing = false
                                    com.example.util.AppLockManager.setLocked(this@UnlockActivity, false)
                                    com.example.util.AppLockManager.setPermanentlyLocked(this@UnlockActivity, false)
                                }
                                setResult(android.app.Activity.RESULT_OK)
                                finish()
                            } else {
                                error = "Invalid Pattern"
                            }
                        }
                    )
                } else {
                    val isPin = lockType == "PIN"
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            if (isPin) {
                                if (it.length <= 6) password = it
                            } else {
                                password = it 
                            }
                            error = "" 
                        },
                        label = { Text(if (isPin) "Enter PIN" else "Security Password", color = Color.Gray) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = if (isPin) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error.isNotEmpty(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PlenxoColors.Primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (sha256(password) == savedHash) {
                                if (!isChatLock) {
                                    com.example.util.AppLockObserver.isLockScreenShowing = false
                                    com.example.util.AppLockManager.setLocked(this@UnlockActivity, false)
                                    com.example.util.AppLockManager.setPermanentlyLocked(this@UnlockActivity, false)
                                }
                                setResult(android.app.Activity.RESULT_OK)
                                finish()
                            } else {
                                error = if (isPin) "Invalid PIN" else "Invalid Password"
                                password = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary)
                    ) {
                        Text(stringResource(R.string.str_unlock), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val chatId = intent.getStringExtra("chatId")
        if (chatId != null) {
            setResult(android.app.Activity.RESULT_CANCELED)
            finish()
        } else {
            moveTaskToBack(true)
        }
    }
}
