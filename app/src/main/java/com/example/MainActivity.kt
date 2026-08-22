package com.example

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import com.example.data.repository.SecurityRepository
import com.example.model.ChatRoom
import com.example.navigation.PlenxoNavGraph
import com.example.ui.BaseActivity
import com.example.ui.theme.PlenxoTheme
import com.example.util.PermissionManager
import com.example.util.SessionManager
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private val currentIntentState = mutableStateOf<Intent?>(null)
    private var mainViewModel: PlenxoViewModel? = null

    override fun onResume() {
        super.onResume()
        checkGlobalAccountLockout()
    }

    fun checkGlobalAccountLockout() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) return
        val email = currentUser.email ?: uid

        lifecycleScope.launch(Dispatchers.IO) {
            val securityRepository = SecurityRepository(applicationContext)
            val model = securityRepository.checkAccountLockoutStatus(uid) 
                ?: securityRepository.checkAccountLockoutStatus(email)
            if (model != null && model.isLockedOut()) {
                Log.w("MainActivity", "24-Hour Account Lockdown detected for $uid! Revoking active session.")
                withContext(Dispatchers.Main) {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clearLoginState(applicationContext)
                    mainViewModel?.navigateToScreen(
                        PlenxoScreen.LOGIN,
                        addToHistory = false,
                        clearHistory = true
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntentState.value = intent
        Log.d("MainActivity", "onCreate started with Edge-To-Edge and Native OS System Permission Trigger")
        
        // Handle global crash restart recovery notice
        if (intent?.getBooleanExtra("GLOBAL_CRASH_RESTART", false) == true) {
            val crashPrefs = getSharedPreferences("app_crash_logs", MODE_PRIVATE)
            val lastCrash = crashPrefs.getString("last_crash_log", "No crash log recorded")
            android.util.Log.e("PlenxoCrashDebug", "LAST CRASH LOG: $lastCrash")
            Toast.makeText(
                this, 
                "Plenxo recovered from crash: ${lastCrash?.take(100)}", 
                Toast.LENGTH_LONG
            ).show()
        }

        // Enable modern Edge-to-Edge window insets
        enableEdgeToEdge()
        val permissionManager = PermissionManager(this)
        
        try {
            setContentView(R.layout.activity_main)
            
            // Programmatically hide FAB on startup (shown on Home screen)
            findViewById<FloatingActionButton>(R.id.fab_add_user)?.visibility = View.GONE
            
            val container = findViewById<FrameLayout>(R.id.container)
            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@MainActivity)
                setViewTreeViewModelStoreOwner(this@MainActivity)
                setViewTreeSavedStateRegistryOwner(this@MainActivity)
                setViewTreeOnBackPressedDispatcherOwner(this@MainActivity)
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
                    ) {
                    val viewModel: PlenxoViewModel = viewModel()
                    mainViewModel = viewModel
                    val themeMode by viewModel.appThemeMode.collectAsState()
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val selectedThemeName by viewModel.selectedTheme.collectAsState()
                    val activeIntent by currentIntentState
                    
                    // Access LocalConfiguration to ensure the entire Compose tree dynamically reacts to Locale configuration changes instantly
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    @Suppress("UNUSED_VARIABLE")
                    val currentLocaleTag = remember(configuration) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            configuration.locales.get(0)?.toLanguageTag() ?: "en"
                        } else {
                            @Suppress("DEPRECATION")
                            configuration.locale.language
                        }
                    }
                    
                    LaunchedEffect(Unit) {
                        if (intent?.getBooleanExtra("GLOBAL_CRASH_RESTART", false) == true) {
                            viewModel.handleCrashRecovery()
                        }
                    }

                    LaunchedEffect(activeIntent) {
                        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            if (currentScreen != PlenxoScreen.LOGIN && currentScreen != PlenxoScreen.SIGNUP && currentScreen != PlenxoScreen.FORGOT_PASSWORD && currentScreen != PlenxoScreen.OTP_VERIFICATION) {
                                viewModel.navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
                            }
                            return@LaunchedEffect
                        }

                        viewModel.handleDeepLink(activeIntent?.data)

                        if (activeIntent?.hasExtra("type") == true && activeIntent?.getStringExtra("type") == "friend_request") {
                            viewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS)
                            activeIntent?.removeExtra("type")
                        } else if (activeIntent?.hasExtra("chatId") == true) {
                            val chatId = activeIntent?.getStringExtra("chatId")
                            val senderId = activeIntent?.getStringExtra("senderId")
                            if (chatId != null) {
                                if (senderId != null) {
                                    viewModel.openChatRoom(ChatRoom(chatId = chatId, participantUids = listOf(viewModel.currentUserId, senderId)))
                                } else {
                                    viewModel.currentChatId.value = chatId
                                    viewModel.navigateToScreen(PlenxoScreen.CHAT_DETAIL)
                                }
                            }
                            activeIntent?.removeExtra("chatId")
                        }

                        val navigateTo = activeIntent?.getStringExtra("NAVIGATE_TO")
                        if (navigateTo != null) {
                            try {
                                val screen = PlenxoScreen.valueOf(navigateTo)
                                viewModel.navigateToScreen(screen)
                            } catch (e: Exception) {
                                if (navigateTo == "WALLPAPER_GALLERY") {
                                    viewModel.navigateToScreen(PlenxoScreen.WALLPAPER_GALLERY)
                                }
                            }
                            activeIntent?.removeExtra("NAVIGATE_TO")
                        }
                    }

                    // Sync FAB visibility with current screen
                    LaunchedEffect(currentScreen) {
                        val fab = findViewById<FloatingActionButton>(R.id.fab_add_user)
                        fab?.visibility = if (currentScreen == PlenxoScreen.HOME) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                    
                    // Sync FAB tint with active theme
                    LaunchedEffect(selectedThemeName) {
                        val themeColor = when (selectedThemeName) {
                            "Red" -> Color.parseColor("#E53935")
                            "Blue" -> Color.parseColor("#1E88E5")
                            "Purple" -> Color.parseColor("#8E24AA")
                            "Black" -> Color.parseColor("#212121")
                            "Golden" -> Color.parseColor("#FFB300")
                            else -> Color.parseColor("#8A2BE2") // Electric Violet
                        }
                        val fab = findViewById<FloatingActionButton>(R.id.fab_add_user)
                        fab?.backgroundTintList = ColorStateList.valueOf(themeColor)
                        fab?.setRippleColor(ColorStateList.valueOf(Color.parseColor("#00E5FF")))
                    }
                    
                    PlenxoTheme(themeMode = themeMode) {
                        PlenxoNavGraph(
                            viewModel = viewModel, 
                            permissionManager = permissionManager
                        )
                    }
                    }
                }
            }
            container?.addView(composeView)
            
            val fabAddUser = findViewById<FloatingActionButton>(R.id.fab_add_user)
            fabAddUser?.setOnClickListener {
                Log.d("MainActivity", "fab_add_user clicked, navigating to DISCOVERY")
                mainViewModel?.navigateToScreen(com.example.viewmodel.PlenxoScreen.DISCOVERY)
            }
            
        } catch (e: Exception) {
            Log.e("DEBUG_UI", "Error loading Main XML and UI integration", e)
        }
    }
}
