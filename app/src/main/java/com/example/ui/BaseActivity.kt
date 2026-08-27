package com.example.ui

import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.util.SessionManager
import com.example.util.AppLockManager
import com.example.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecurityFlags()
        checkAndRestoreAuthSession()
    }

    private fun checkAndRestoreAuthSession() {
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val loginState = SessionManager.getLoginState(this)
        if (fbUser == null && loginState.isLoggedIn) {
            android.util.Log.w("BaseActivity", "Session marked logged in, but Firebase Auth user is null. User needs to log in.")
        }
    }

    override fun onResume() {
        super.onResume()
        applySecurityFlags()
        checkLockAndSecurity()
    }

    private fun applySecurityFlags() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isEmpty()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            return
        }

        val isAppLockEnabled = AppLockManager.isAppLockEnabled(this)
        val isLocked = AppLockManager.isLocked(this)
        
        // FLAG_SECURE must ONLY be applied if app lock is explicitly enabled AND the app is locked
        if (isAppLockEnabled && isLocked) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            // Explicitly clear the secure flag if the app lock is not enabled or app is unlocked
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun checkLockAndSecurity() {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val isLoggedIn = SessionManager.getLoginState(this).isLoggedIn || currentUid.isNotEmpty()
        val isAppLockEnabled = AppLockManager.isAppLockEnabled(this)

        // If user is not logged in, they should go to Login screen (hosted in MainActivity)
        if (!isLoggedIn) {
            if (this !is com.example.MainActivity) {
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                finish()
            }
            return
        }

        // Prevent navigation loop: if app lock is disabled globally, skip all lock checks
        if (!isAppLockEnabled) {
            return
        }

        // Task 4: Root & Debug detection. If a security risk is active, set permanent lock status.
        val hasRisk = AppLockManager.checkSecurityRisk(this)
        
        if (hasRisk) {
            val intent = android.content.Intent(this, UnlockActivity::class.java).apply {
                putExtra("SECURITY_VIOLATION", true)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
            return
        }

        if (AppLockManager.isLocked(this)) {
            val intent = android.content.Intent(this, UnlockActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }
}
