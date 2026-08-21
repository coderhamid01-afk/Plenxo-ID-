@file:Suppress("DEPRECATION")
package com.example.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.example.MainActivity
import com.example.R
import com.example.util.SettingsManager
import com.example.viewmodel.SecurityViewModel
import com.example.util.SessionManager
import com.example.util.UpdateManager
import com.example.util.UpdateInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.EditText
import android.graphics.Color
import android.view.ViewGroup

class SettingsActivity : BaseActivity() {

    private val securityViewModel: SecurityViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[SecurityViewModel::class.java]
    }

    private lateinit var settingsManager: SettingsManager
    private var mediaPlayer: MediaPlayer? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewStopRunnable: Runnable? = null

    // Map of raw audio files to friendly labels
    private val ringtoneList = listOf(
        "minimal_ping" to "Minimal Ping",
        "minimal_pop" to "Minimal Pop",
        "zen_ping" to "Zen Ping",
        "crystal_drop" to "Crystal Drop",
        "echo_drop" to "Echo Drop",
        "cyber_spark" to "Cyber Spark",
        "cyber_alert" to "Cyber Alert",
        "retro_synth" to "Retro Synth",
        "midnight_pulse" to "Midnight Pulse",
        "lunar_chime" to "Chime",
        "ethereal_echo" to "Ethereal Echo",
        "ambient_breeze" to "Ambient Breeze",
        "soft_breeze" to "Soft Breeze",
        "obsidian_strike" to "Obsidian Strike",
        "royal_bell" to "Royal Bell",
        "velvet_tap" to "Velvet Tap",
        "tone_one" to "Tone One",
        "tone_two" to "Tone Two"
    )

    private val languages = listOf("English", "Urdu (اردو)", "Hindi (हिंदी)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager.getInstance(this)

        // Initialize custom toolbar back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            triggerHapticFeedback()
            finish()
        }

        setupChatsAndMediaSection()
        setupNotificationsSection()
        setupAppAndGeneralSection()
        setupPrivacySection()
        setupAccountSection()
    }

    private fun setupChatsAndMediaSection() {
        val optionSaveToGallery = findViewById<LinearLayout>(R.id.option_save_to_gallery)
        val switchSaveToGallery = findViewById<SwitchCompat>(R.id.switch_save_to_gallery)
        val optionEnterIsSend = findViewById<LinearLayout>(R.id.option_enter_is_send)
        val switchEnterIsSend = findViewById<SwitchCompat>(R.id.switch_enter_is_send)
        val btnChatWallpapers = findViewById<LinearLayout>(R.id.btn_chat_wallpapers)
        val optionLinkPreviews = findViewById<LinearLayout>(R.id.option_link_previews)
        val switchLinkPreviews = findViewById<SwitchCompat>(R.id.switch_link_previews)

        // Load persisted values
        switchSaveToGallery.isChecked = settingsManager.isSaveToGalleryEnabled()
        switchEnterIsSend.isChecked = settingsManager.isEnterIsSendEnabled()
        switchLinkPreviews.isChecked = settingsManager.isLinkPreviewsEnabled()

        // Handle Row Clicks (which will toggle the Switch dynamically)
        optionSaveToGallery.setOnClickListener {
            triggerHapticFeedback()
            switchSaveToGallery.toggle()
        }
        switchSaveToGallery.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSaveToGalleryEnabled(isChecked)
        }

        optionEnterIsSend.setOnClickListener {
            triggerHapticFeedback()
            switchEnterIsSend.toggle()
        }
        switchEnterIsSend.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setEnterIsSendEnabled(isChecked)
        }

        optionLinkPreviews.setOnClickListener {
            triggerHapticFeedback()
            switchLinkPreviews.toggle()
        }
        switchLinkPreviews.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setLinkPreviewsEnabled(isChecked)
        }

        // Wallpaper Button -> Navigate to MainActivity and show WALLPAPER_GALLERY
        btnChatWallpapers.setOnClickListener {
            triggerHapticFeedback()
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("NAVIGATE_TO", "WALLPAPER_GALLERY")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    private fun setupNotificationsSection() {
        val optionMuteNotifications = findViewById<LinearLayout>(R.id.option_mute_notifications)
        val switchMuteNotifications = findViewById<SwitchCompat>(R.id.switch_mute_notifications)
        val optionPopupNotification = findViewById<LinearLayout>(R.id.option_popup_notification)
        val switchPopupNotification = findViewById<SwitchCompat>(R.id.switch_popup_notification)
        val btnNotificationSound = findViewById<LinearLayout>(R.id.btn_notification_sound)
        val txtCurrentSound = findViewById<TextView>(R.id.txt_current_sound)

        val optionDnd = findViewById<LinearLayout>(R.id.option_dnd)
        val switchDnd = findViewById<SwitchCompat>(R.id.switch_dnd)
        val layoutDndTimes = findViewById<LinearLayout>(R.id.layout_dnd_times)
        val btnDndStartTime = findViewById<LinearLayout>(R.id.btn_dnd_start_time)
        val txtDndStartTime = findViewById<TextView>(R.id.txt_dnd_start_time)
        val btnDndEndTime = findViewById<LinearLayout>(R.id.btn_dnd_end_time)
        val txtDndEndTime = findViewById<TextView>(R.id.txt_dnd_end_time)
        val txtDndStatus = findViewById<TextView>(R.id.txt_dnd_status)

        // Load persisted values
        switchMuteNotifications.isChecked = settingsManager.isMuteNotificationsEnabled()
        switchPopupNotification.isChecked = settingsManager.isPopupNotificationsEnabled()

        val savedSoundResName = settingsManager.getNotificationSound()
        txtCurrentSound.text = getFriendlySoundName(savedSoundResName)

        val dndEnabled = settingsManager.isDndEnabled()
        switchDnd.isChecked = dndEnabled
        layoutDndTimes.visibility = if (dndEnabled) View.VISIBLE else View.GONE
        txtDndStartTime.text = settingsManager.getDndStartTime()
        txtDndEndTime.text = settingsManager.getDndEndTime()

        val updateDndStatusText = {
            if (settingsManager.isDndEnabled()) {
                if (settingsManager.isCurrentTimeInQuietHours()) {
                    txtDndStatus.text = "Active Now (Quiet Hours)"
                    txtDndStatus.setTextColor(Color.parseColor("#7EE787"))
                } else {
                    txtDndStatus.text = "Scheduled (${settingsManager.getDndStartTime()} - ${settingsManager.getDndEndTime()})"
                    txtDndStatus.setTextColor(Color.parseColor("#58A6FF"))
                }
            } else {
                txtDndStatus.text = "Silence notification alerts during quiet hours"
                txtDndStatus.setTextColor(Color.parseColor("#8E8E93"))
            }
        }
        updateDndStatusText()

        // Row clicks to toggle switches
        optionMuteNotifications.setOnClickListener {
            triggerHapticFeedback()
            switchMuteNotifications.toggle()
        }
        switchMuteNotifications.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setMuteNotificationsEnabled(isChecked)
        }

        optionPopupNotification.setOnClickListener {
            triggerHapticFeedback()
            switchPopupNotification.toggle()
        }
        switchPopupNotification.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setPopupNotificationsEnabled(isChecked)
        }

        optionDnd.setOnClickListener {
            triggerHapticFeedback()
            switchDnd.toggle()
        }
        switchDnd.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDndEnabled(isChecked)
            layoutDndTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateDndStatusText()
        }

        btnDndStartTime.setOnClickListener {
            triggerHapticFeedback()
            val timeParts = settingsManager.getDndStartTime().split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 22
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            
            android.app.TimePickerDialog(this, R.style.CustomDialogTheme, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                settingsManager.setDndStartTime(formattedTime)
                txtDndStartTime.text = formattedTime
                updateDndStatusText()
            }, hour, minute, true).show()
        }

        btnDndEndTime.setOnClickListener {
            triggerHapticFeedback()
            val timeParts = settingsManager.getDndEndTime().split(":")
            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            
            android.app.TimePickerDialog(this, R.style.CustomDialogTheme, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                settingsManager.setDndEndTime(formattedTime)
                txtDndEndTime.text = formattedTime
                updateDndStatusText()
            }, hour, minute, true).show()
        }

        // Notification Sound Dialog Trigger
        btnNotificationSound.setOnClickListener {
            triggerHapticFeedback()
            showNotificationSoundPicker(txtCurrentSound)
        }
    }

    private fun setupAppAndGeneralSection() {
        val btnLanguageSelection = findViewById<LinearLayout>(R.id.btn_language_selection)
        val txtCurrentLanguage = findViewById<TextView>(R.id.txt_current_language)
        val txtAppVersion = findViewById<TextView>(R.id.txt_app_version)
        val btnCheckUpdates = findViewById<Button>(R.id.btn_check_updates)
        val btnReportBug = findViewById<LinearLayout>(R.id.btn_report_bug)

        // Load persisted values
        txtCurrentLanguage.text = settingsManager.getLanguageSelection()
        txtAppVersion.text = "v${com.example.BuildConfig.VERSION_NAME}"

        // Language Picker Dialog Trigger
        btnLanguageSelection.setOnClickListener {
            triggerHapticFeedback()
            showLanguagePicker(txtCurrentLanguage)
        }

        // Updates Check Handler
        btnCheckUpdates.setOnClickListener {
            triggerHapticFeedback()
            btnCheckUpdates.isEnabled = false
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                val updateInfo = UpdateManager.fetchUpdateInfo(this@SettingsActivity)
                val currentVersionCode = com.example.BuildConfig.VERSION_CODE

                withContext(Dispatchers.Main) {
                    btnCheckUpdates.isEnabled = true
                    if (currentVersionCode >= updateInfo.latestVersionCode) {
                        showUpToDateDialog(updateInfo.latestVersionName)
                    } else {
                        showUpdateAvailableDialog(updateInfo)
                    }
                }
            }
        }

        // Bug Report Handler
        btnReportBug?.setOnClickListener {
            triggerHapticFeedback()
            val intent = Intent(this, ReportBugActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showNotificationSoundPicker(txtCurrentSound: TextView) {
        val savedSound = settingsManager.getNotificationSound()
        var tempSelectedIndex = ringtoneList.indexOfFirst { it.first == savedSound }
        if (tempSelectedIndex == -1) tempSelectedIndex = 0

        val labelsArray = ringtoneList.map { it.second }.toTypedArray()

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Notification Sound")
            .setSingleChoiceItems(labelsArray, tempSelectedIndex) { _, which ->
                tempSelectedIndex = which
                val selectedSoundRes = ringtoneList[which].first
                playRingtonePreview(selectedSoundRes)
            }
            .setPositiveButton("Save") { _, _ ->
                triggerHapticFeedback()
                stopRingtonePreview()
                val selectedSoundKey = ringtoneList[tempSelectedIndex].first
                settingsManager.setNotificationSound(this, selectedSoundKey)
                txtCurrentSound.text = ringtoneList[tempSelectedIndex].second
                Toast.makeText(this, "Sound updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                triggerHapticFeedback()
                stopRingtonePreview()
            }
            .setOnDismissListener {
                stopRingtonePreview()
            }
            .create()

        dialog.show()
    }

    private fun showLanguagePicker(txtCurrentLanguage: TextView) {
        val savedLang = settingsManager.getLanguageSelection()
        var tempSelectedIndex = languages.indexOf(savedLang)
        if (tempSelectedIndex == -1) tempSelectedIndex = 0

        val languagesArray = languages.toTypedArray()

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Select Language")
            .setSingleChoiceItems(languagesArray, tempSelectedIndex) { _, which ->
                tempSelectedIndex = which
            }
            .setPositiveButton("Select") { _, _ ->
                triggerHapticFeedback()
                val selectedLanguageName = languages[tempSelectedIndex]
                settingsManager.setLanguageSelection(selectedLanguageName)
                txtCurrentLanguage.text = selectedLanguageName
                val langCode = when (selectedLanguageName) {
                    "Spanish" -> "es"
                    "French" -> "fr"
                    "Urdu" -> "ur"
                    "Hindi" -> "hi"
                    "Arabic" -> "ar"
                    "Chinese" -> "zh"
                    "Japanese" -> "ja"
                    "German" -> "de"
                    "Russian" -> "ru"
                    "Portuguese" -> "pt"
                    "Italian" -> "it"
                    "Bengali" -> "bn"
                    "Punjabi" -> "pa"
                    "Turkish" -> "tr"
                    "Korean" -> "ko"
                    "Vietnamese" -> "vi"
                    "Indonesian" -> "id"
                    "Persian" -> "fa"
                    "Polish" -> "pl"
                    else -> "en"
                }
                com.example.util.LocaleHelper.setLocale(this, langCode)
                Toast.makeText(this, "Language switched to $selectedLanguageName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                triggerHapticFeedback()
            }
            .create()

        dialog.show()
    }

    private fun showUpToDateDialog(versionName: String) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Software Update")
            .setMessage("You are running the latest version of Plenxo (v$versionName).\nNo updates are currently available.")
            .setPositiveButton("OK") { dialog, _ ->
                triggerHapticFeedback()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showUpdateAvailableDialog(updateInfo: UpdateInfo) {
        val message = "New Version Available: v${updateInfo.latestVersionName}\n\nChangelog:\n${updateInfo.changelog}"
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Update via Play Store") { dialog, _ ->
                triggerHapticFeedback()
                UpdateManager.openPlayStore(this, updateInfo.playStoreUrl)
                dialog.dismiss()
            }
            .setNeutralButton("Update via APKPure") { dialog, _ ->
                triggerHapticFeedback()
                UpdateManager.openApkPure(this, updateInfo.apkPureUrl)
                dialog.dismiss()
            }
            .setNegativeButton("Later") { dialog, _ ->
                triggerHapticFeedback()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun playRingtonePreview(soundResName: String) {
        try {
            stopRingtonePreview()
            val resId = resources.getIdentifier(soundResName, "raw", packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId).apply {
                    setOnCompletionListener {
                        stopRingtonePreview()
                    }
                    start()
                }
                
                // Stop after exactly 2 seconds
                previewStopRunnable = Runnable {
                    stopRingtonePreview()
                }
                previewStopRunnable?.let { previewHandler.postDelayed(it, 2000L) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtonePreview() {
        try {
            previewStopRunnable?.let {
                previewHandler.removeCallbacks(it)
                previewStopRunnable = null
            }
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFriendlySoundName(resName: String): String {
        return ringtoneList.find { it.first == resName }?.second ?: "Minimal Ping"
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    it.vibrate(20)
                }
            }
        } catch (e: Exception) {
            // Fallback to view-based haptics
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onStop() {
        super.onStop()
        stopRingtonePreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtonePreview()
    }

    private val visibilityOptions = listOf("EVERYONE" to "Everyone", "CONTACTS" to "My Contacts", "NOBODY" to "Nobody")
    private val disappearingOptions = listOf(0L to "Off", 86400000L to "24 Hours", 604800000L to "7 Days")
    private val lockTimeoutOptions = listOf(0L to "Immediately", 60000L to "After 1 Minute", 300000L to "After 5 Minutes")

    private fun setupPrivacySection() {
        val btnProfilePhotoVis = findViewById<LinearLayout>(R.id.btn_profile_photo_vis)
        val txtCurrentPhotoVis = findViewById<TextView>(R.id.txt_current_photo_vis)
        val btnAboutBioVis = findViewById<LinearLayout>(R.id.btn_about_bio_vis)
        val txtCurrentAboutVis = findViewById<TextView>(R.id.txt_current_about_vis)
        val btnBlockedContacts = findViewById<LinearLayout>(R.id.btn_blocked_contacts)
        val btnDisappearingMessages = findViewById<LinearLayout>(R.id.btn_disappearing_messages)
        val txtCurrentDisappearingMessages = findViewById<TextView>(R.id.txt_current_disappearing_messages)
        val btnScreenLockTimeout = findViewById<LinearLayout>(R.id.btn_screen_lock_timeout)
        val txtCurrentLockTimeout = findViewById<TextView>(R.id.txt_current_lock_timeout)

        // Load persisted values
        val savedPhotoVis = SessionManager.getPhotoVis(this)
        txtCurrentPhotoVis.text = visibilityOptions.find { it.first == savedPhotoVis }?.second ?: "Everyone"

        val savedAboutVis = getSharedPreferences("app_settings", MODE_PRIVATE).getString("about_visibility", "EVERYONE") ?: "EVERYONE"
        txtCurrentAboutVis.text = visibilityOptions.find { it.first == savedAboutVis }?.second ?: "Everyone"

        val savedDisappearing = SessionManager.getDisappearingTimer(this)
        txtCurrentDisappearingMessages.text = disappearingOptions.find { it.first == savedDisappearing }?.second ?: "Off"

        val savedTimeout = settingsManager.getScreenLockTimeout()
        txtCurrentLockTimeout.text = lockTimeoutOptions.find { it.first == savedTimeout }?.second ?: "Immediately"

        // Row clicks
        btnProfilePhotoVis.setOnClickListener {
            triggerHapticFeedback()
            showVisibilityPickerDialog("Profile Photo Visibility", savedPhotoVis) { selected ->
                securityViewModel.updatePhotoVisibility(selected)
                txtCurrentPhotoVis.text = visibilityOptions.find { it.first == selected }?.second
                Toast.makeText(this, "Profile photo visibility updated", Toast.LENGTH_SHORT).show()
            }
        }

        btnAboutBioVis.setOnClickListener {
            triggerHapticFeedback()
            showVisibilityPickerDialog("About / Bio Visibility", savedAboutVis) { selected ->
                securityViewModel.updateAboutVisibility(selected)
                txtCurrentAboutVis.text = visibilityOptions.find { it.first == selected }?.second
                Toast.makeText(this, "About visibility updated", Toast.LENGTH_SHORT).show()
            }
        }

        btnBlockedContacts.setOnClickListener {
            triggerHapticFeedback()
            startActivity(Intent(this, BlockedContactsActivity::class.java))
        }

        btnDisappearingMessages.setOnClickListener {
            triggerHapticFeedback()
            val savedTimer = SessionManager.getDisappearingTimer(this)
            var selectedIndex = disappearingOptions.indexOfFirst { it.first == savedTimer }
            if (selectedIndex == -1) selectedIndex = 0

            val optionsArray = disappearingOptions.map { it.second }.toTypedArray()

            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Disappearing Messages")
                .setSingleChoiceItems(optionsArray, selectedIndex) { dialog, which ->
                    triggerHapticFeedback()
                    val selectedPair = disappearingOptions[which]
                    securityViewModel.updateDisappearingMessages(selectedPair.first)
                    txtCurrentDisappearingMessages.text = selectedPair.second
                    Toast.makeText(this, "Disappearing messages set to: ${selectedPair.second}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnScreenLockTimeout.setOnClickListener {
            triggerHapticFeedback()
            val currentTimeout = settingsManager.getScreenLockTimeout()
            var selectedIndex = lockTimeoutOptions.indexOfFirst { it.first == currentTimeout }
            if (selectedIndex == -1) selectedIndex = 0

            val optionsArray = lockTimeoutOptions.map { it.second }.toTypedArray()

            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Screen Lock Timeout")
                .setSingleChoiceItems(optionsArray, selectedIndex) { dialog, which ->
                    triggerHapticFeedback()
                    val selectedPair = lockTimeoutOptions[which]
                    settingsManager.setScreenLockTimeout(selectedPair.first)
                    txtCurrentLockTimeout.text = selectedPair.second
                    Toast.makeText(this, "Screen lock timeout set to: ${selectedPair.second}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showVisibilityPickerDialog(title: String, currentVal: String, onSelected: (String) -> Unit) {
        var selectedIndex = visibilityOptions.indexOfFirst { it.first == currentVal }
        if (selectedIndex == -1) selectedIndex = 0

        val optionsArray = visibilityOptions.map { it.second }.toTypedArray()

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(title)
            .setSingleChoiceItems(optionsArray, selectedIndex) { dialog, which ->
                triggerHapticFeedback()
                val selectedKey = visibilityOptions[which].first
                onSelected(selectedKey)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAccountSection() {
        val btnChangePassword = findViewById<LinearLayout>(R.id.btn_change_password)
        val btnLoggedInDevices = findViewById<LinearLayout>(R.id.btn_logged_in_devices)
        val btnDeleteAccount = findViewById<LinearLayout>(R.id.btn_delete_account)

        btnChangePassword.setOnClickListener {
            triggerHapticFeedback()
            showChangePasswordDialog()
        }

        btnLoggedInDevices.setOnClickListener {
            triggerHapticFeedback()
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("NAVIGATE_TO", "ACTIVE_SESSIONS")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }

        btnDeleteAccount.setOnClickListener {
            triggerHapticFeedback()
            showDeleteAccountDialog()
        }
    }

    private fun showChangePasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val etCurrentPass = EditText(this).apply {
            hint = "Current Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 30)
            }
        }

        val etNewPass = EditText(this).apply {
            hint = "New Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
        }

        container.addView(etCurrentPass)
        container.addView(etNewPass)

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                triggerHapticFeedback()
                val currentPass = etCurrentPass.text.toString().trim()
                val newPass = etNewPass.text.toString().trim()

                if (currentPass.isEmpty() || newPass.isEmpty()) {
                    Toast.makeText(this, "Passwords cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPass.length < 6) {
                    Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                securityViewModel.changePassword(currentPass, newPass,
                    onSuccess = {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteAccountDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val etConfirmPass = EditText(this).apply {
            hint = "Enter Password to confirm"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
        }
        container.addView(etConfirmPass)

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("WARNING: Irreversible Action")
            .setMessage("All your conversations, profile parameters, and database sync configurations will be permanently deleted. This cannot be undone.")
            .setView(container)
            .setPositiveButton("DELETE PERMANENTLY") { _, _ ->
                triggerHapticFeedback()
                val password = etConfirmPass.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Password is required to delete account.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                securityViewModel.deleteAccount(password,
                    onSuccess = {
                        Toast.makeText(this, "Your account has been deleted permanently.", Toast.LENGTH_LONG).show()
                        // BaseActivity automatically redirects to Login since user is null
                        finish()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, "Failed to delete account: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
