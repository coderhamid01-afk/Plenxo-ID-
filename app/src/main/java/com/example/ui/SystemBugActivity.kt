@file:Suppress("DEPRECATION")
package com.example.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.databinding.ActivityReportBugBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.BuildConfig

class SystemBugActivity : BaseActivity() {

    private lateinit var binding: ActivityReportBugBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up Toolbar Back Button
        binding.btnBack.setOnClickListener {
            triggerHapticFeedback()
            finish()
        }

        setupCategorySpinner()
        loadSystemInfo()

        // Submit Button Click
        binding.btnSubmit.setOnClickListener {
            triggerHapticFeedback()
            validateAndSubmit()
        }
    }

    private fun setupCategorySpinner() {
        val categories = listOf("UI/UX Glitch", "Chat Message Issue", "App Crash", "Other Connection Issues")
        
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (v is TextView) {
                    v.setTextColor(android.graphics.Color.WHITE)
                    v.textSize = 15f
                    v.setPadding(16, 0, 16, 0)
                }
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                if (v is TextView) {
                    v.setTextColor(android.graphics.Color.WHITE)
                    v.setBackgroundColor(android.graphics.Color.parseColor("#16161A"))
                    v.textSize = 15f
                    v.setPadding(24, 24, 24, 24)
                }
                return v
            }
        }
        
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
    }

    private fun loadSystemInfo() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid?.ifEmpty { "Anonymous" } ?: "Anonymous"
        
        binding.txtDeviceModel.text = "Device Model: ${Build.MODEL}"
        binding.txtOsVersion.text = "Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        binding.txtUserUid.text = "User UID: $userId"
    }

    private fun validateAndSubmit() {
        val description = binding.editDescription.text.toString().trim()
        
        if (description.isEmpty()) {
            binding.layoutDescription.error = "Description cannot be empty"
            return
        }
        
        if (description.length < 15) {
            binding.layoutDescription.error = "Please provide more details (minimum 15 characters)"
            return
        }
        
        binding.layoutDescription.error = null // Clear error
        
        val category = binding.spinnerCategory.selectedItem?.toString() ?: "General"
        submitBugReport(category, description)
    }

    private fun submitBugReport(category: String, description: String) {
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                val reportMap = mapOf(
                    "category" to category,
                    "description" to description,
                    "userId" to userId,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("bug_reports")
                    .add(reportMap)
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("SystemBugActivity", "Failed to save bug report to Firebase", e)
            }

            withContext(Dispatchers.Main) {
                binding.loadingOverlay.visibility = View.GONE
                Toast.makeText(this@SystemBugActivity, "Bug report submitted successfully! Thank you.", Toast.LENGTH_LONG).show()
                binding.editDescription.setText("")
                finish()
            }
        }
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
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
