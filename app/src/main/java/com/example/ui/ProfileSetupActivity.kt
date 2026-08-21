package com.example.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.example.BuildConfig
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var btnEditAvatar: View
    private lateinit var etDisplayName: EditText
    private lateinit var etAbout: EditText
    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var progressOverlay: RelativeLayout
    private lateinit var tvProgressStatus: TextView
    private lateinit var btnBack: View

    private var currentProfilePicUrl: String = ""

    // Image selector contract
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadImageToCatbox(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        // Bind Views
        imgProfile = findViewById(R.id.img_profile)
        btnEditAvatar = findViewById(R.id.btn_edit_avatar)
        etDisplayName = findViewById(R.id.et_display_name)
        etAbout = findViewById(R.id.et_about)
        btnSaveProfile = findViewById(R.id.btn_save_profile)
        progressOverlay = findViewById(R.id.progress_overlay)
        tvProgressStatus = findViewById(R.id.tv_progress_status)
        btnBack = findViewById(R.id.btn_back)

        // Check login status
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please sign in to access this screen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set listeners
        btnBack.setOnClickListener {
            finish()
        }

        btnEditAvatar.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnSaveProfile.setOnClickListener {
            saveProfileInfo()
        }

        // Load existing user profile
        loadUserProfile()
    }

    private fun getResolvedUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    private fun loadUserProfile() {
        val uid = getResolvedUserId()
        if (uid.isEmpty()) return
        showLoading("Loading your profile...")

        lifecycleScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users_data").document(uid).get().await()
                val user = doc.toObject(com.example.model.UserProfile::class.java)

                if (user != null) {
                    val dName = user.displayName ?: ""
                    val about = user.statusMessage ?: ""
                    val picUrl = user.profilePicUrl ?: ""

                    etDisplayName.setText(dName)
                    etAbout.setText(about)
                    
                    if (picUrl.isNotEmpty()) {
                        currentProfilePicUrl = picUrl
                        refreshProfileImageWithCoil(picUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileSetupActivity", "Error loading user profile", e)
                Toast.makeText(this@ProfileSetupActivity, "Error loading profile data", Toast.LENGTH_SHORT).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun uploadImageToCatbox(uri: Uri) {
        showLoading("Uploading avatar to Catbox...")

        lifecycleScope.launch {
            try {
                val uploadedUrl = com.example.network.CatboxStorageManager.uploadImage(applicationContext, uri)

                currentProfilePicUrl = uploadedUrl
                refreshProfileImageWithCoil(uploadedUrl)
                Toast.makeText(this@ProfileSetupActivity, "Image uploaded and saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileSetupActivity", "Error uploading avatar", e)
                Toast.makeText(this@ProfileSetupActivity, e.localizedMessage ?: "Upload failed", Toast.LENGTH_LONG).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun saveProfileInfo() {
        val uid = getResolvedUserId()
        if (uid.isEmpty()) {
            Toast.makeText(this, "User session not found.", Toast.LENGTH_SHORT).show()
            return
        }
        val displayNameStr = etDisplayName.text.toString().trim()
        val aboutStr = etAbout.text.toString().trim()

        if (displayNameStr.isEmpty()) {
            etDisplayName.error = "Name cannot be empty"
            return
        }

        showLoading("Saving your profile changes...")

        lifecycleScope.launch {
            try {
                val userMap = mapOf(
                    "id" to uid,
                    "uid" to uid,
                    "displayName" to displayNameStr,
                    "display_name" to displayNameStr,
                    "email" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
                    "statusMessage" to aboutStr,
                    "profilePicUrl" to currentProfilePicUrl,
                    "photoUrl" to currentProfilePicUrl,
                    "avatar_url" to currentProfilePicUrl,
                    "is_profile_completed" to true,
                    "isProfileCompleted" to true
                )

                FirebaseFirestore.getInstance().collection("users_data").document(uid).set(userMap, SetOptions.merge()).await()

                Log.d("ProfileSetupActivity", "Saved profile to Firestore")

                Toast.makeText(this@ProfileSetupActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                
                // Navigate seamlessly to MainActivity / Chat Dashboard
                val mainIntent = android.content.Intent(this@ProfileSetupActivity, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(mainIntent)
                finish()

            } catch (e: Exception) {
                Log.e("ProfileSetupActivity", "Error saving profile", e)
                Toast.makeText(this@ProfileSetupActivity, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun refreshProfileImageWithCoil(url: String) {
        if (isDestroyed || isFinishing) return
        imgProfile.load(url) {
            diskCachePolicy(CachePolicy.ENABLED)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_avatar_upload.jpg")
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showLoading(message: String) {
        tvProgressStatus.text = message
        progressOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressOverlay.visibility = View.GONE
    }
}
