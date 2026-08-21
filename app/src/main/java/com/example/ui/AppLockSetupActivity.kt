package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

class AppLockSetupActivity : com.example.ui.BaseActivity() {

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val securityRepo = SecurityRepository(this)
        
        val chatId = intent.getStringExtra("chatId")
        val isChatLock = chatId != null

        setContent {
            var selectedOption by remember { mutableStateOf("PIN") }
            var passwordValue by remember { mutableStateOf("") }
            var pinValue by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (isChatLock) "Setup Chat Lock" else "Setup App Lock", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PlenxoColors.Primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = PlenxoColors.Background)
                    )
                },
                containerColor = PlenxoColors.Background
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    Text(stringResource(R.string.str_select_lock_type), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val options = listOf("PIN", "Pattern", "Password", "Biometric")
                    
                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption == option,
                                onClick = { selectedOption = option },
                                colors = RadioButtonDefaults.colors(selectedColor = PlenxoColors.Primary, unselectedColor = Color.Gray)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(if (option == "Biometric") "Biometric (Fingerprint / Face)" else option, color = Color.White)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (selectedOption == "Biometric") {
                        Text(
                            text = "Secure your app instantly using your device's biometric sensor (Fingerprint or Face Unlock).",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (com.example.util.SecurityManager.isBiometricAvailable(this@AppLockSetupActivity)) {
                                    com.example.util.SecurityManager.showPrompt(
                                        activity = this@AppLockSetupActivity,
                                        title = if (isChatLock) "Setup Chat Biometric" else "Setup App Biometric",
                                        subtitle = "Confirm your fingerprint or face to enable biometric lock",
                                        onSuccess = {
                                            if (isChatLock) {
                                                chatId?.let { id ->
                                                    securityRepo.setChatLockType(id, "BIOMETRIC")
                                                    securityRepo.setChatLock(id, "BIOMETRIC_ENABLED")
                                                }
                                            } else {
                                                securityRepo.setGlobalAppLock("BIOMETRIC_ENABLED")
                                                securityRepo.setLockType("BIOMETRIC")
                                                com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                            }
                                            finish()
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(this@AppLockSetupActivity, "Biometric error: $err", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    android.widget.Toast.makeText(this@AppLockSetupActivity, "Biometric authentication is not available or not enrolled on this device.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary)
                        ) {
                            Text("Verify & Enable Biometric Lock", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedOption == "PIN") {
                        OutlinedTextField(
                            value = pinValue,
                            onValueChange = { if (it.length <= 6) pinValue = it },
                            label = { Text(stringResource(R.string.str_enter_4_6_digit_pin), color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (pinValue.length in 4..6) {
                                    if (isChatLock) {
                                        chatId?.let { id ->
                                            securityRepo.setChatLockType(id, "PIN")
                                            securityRepo.setChatLock(id, sha256(pinValue))
                                        }
                                    } else {
                                        securityRepo.setGlobalAppLock(sha256(pinValue))
                                        securityRepo.setLockType("PIN")
                                        com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                    }
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary),
                            enabled = pinValue.length in 4..6
                        ) {
                            Text(stringResource(R.string.str_save_enable_lock), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedOption == "Password") {
                        OutlinedTextField(
                            value = passwordValue,
                            onValueChange = { passwordValue = it },
                            label = { Text(stringResource(R.string.str_enter_alphanumeric_password), color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (passwordValue.isNotEmpty()) {
                                    if (isChatLock) {
                                        chatId?.let { id ->
                                            securityRepo.setChatLockType(id, "PASSWORD")
                                            securityRepo.setChatLock(id, sha256(passwordValue))
                                        }
                                    } else {
                                        securityRepo.setGlobalAppLock(sha256(passwordValue))
                                        securityRepo.setLockType("PASSWORD")
                                        com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                    }
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary),
                            enabled = passwordValue.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.str_save_enable_lock), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedOption == "Pattern") {
                        Text(stringResource(R.string.str_draw_your_pattern), color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(16.dp))
                        com.example.ui.components.PatternLockView(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            onPatternComplete = { pattern ->
                                if (isChatLock) {
                                    chatId?.let { id ->
                                        securityRepo.setChatLockType(id, "PATTERN")
                                        securityRepo.setChatLock(id, sha256(pattern))
                                    }
                                } else {
                                    securityRepo.setGlobalAppLock(sha256(pattern))
                                    securityRepo.setLockType("PATTERN")
                                    com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                }
                                finish()
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
