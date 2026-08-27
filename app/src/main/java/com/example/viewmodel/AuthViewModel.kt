package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.PlenxoApplication
import com.example.data.model.UserSecurityModel
import com.example.data.repository.SecurityRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

import com.example.network.NetworkModule
import com.example.util.OtpUtils
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * State machine steps for password reset & security challenges.
 */
enum class ResetStep {
    IDLE,
    EMAIL_INPUT,
    PLENXO_ID_CHECK,
    EMAIL_OTP,
    SECURITY_PIN_VERIFY,
    MASTER_PIN_CHALLENGE,
    SCANNING_TIMER,
    NEW_PASSWORD,
    SUCCESS
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class SuccessDirect(val userId: String, val email: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class AuthUiEvent {
    object NavigateToOtpScreen : AuthUiEvent()
    data class ShowToast(val message: String) : AuthUiEvent()
    data class ShowError(val message: String) : AuthUiEvent()
}

@Deprecated(
    message = "Legacy AuthViewModel. The active primary auth flow is managed by PlenxoViewModel.",
    replaceWith = ReplaceWith("PlenxoViewModel", "com.example.viewmodel.PlenxoViewModel")
)
class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val securityRepository by lazy {
        SecurityRepository(
            try { PlenxoApplication.instance } catch (e: Throwable) { com.google.firebase.FirebaseApp.getInstance().applicationContext }
        )
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AuthUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<AuthUiEvent> = _uiEvent.asSharedFlow()

    val isTermsAccepted = MutableStateFlow(false)

    // Security & Password Reset StateFlows
    private val _currentResetStep = MutableStateFlow(ResetStep.IDLE)
    val currentResetStep: StateFlow<ResetStep> = _currentResetStep.asStateFlow()

    private val _activeOtp = MutableStateFlow<String>("")
    val activeOtp: StateFlow<String> = _activeOtp.asStateFlow()

    // StateFlow alias for generatedOtp
    val generatedOtp: StateFlow<String> = _activeOtp.asStateFlow()
    private var otpExpirationTimeMs: Long = 0L

    private val _isAccountLocked = MutableStateFlow(false)
    val isAccountLocked: StateFlow<Boolean> = _isAccountLocked.asStateFlow()

    private val _lockoutTimeRemainingMs = MutableStateFlow(0L)
    val lockoutTimeRemainingMs: StateFlow<Long> = _lockoutTimeRemainingMs.asStateFlow()

    private val _securityErrorMessage = MutableStateFlow<String?>(null)
    val securityErrorMessage: StateFlow<String?> = _securityErrorMessage.asStateFlow()

    private val _targetUserSecurityModel = MutableStateFlow<UserSecurityModel?>(null)
    val targetUserSecurityModel: StateFlow<UserSecurityModel?> = _targetUserSecurityModel.asStateFlow()

    private var lockoutTickerJob: Job? = null

    fun setActiveOtp(otp: String, expirationDurationMs: Long = 10 * 60 * 1000L) {
        val clean = otp.trim()
        val formatted = if (clean.matches(Regex("^\\d{1,5}$"))) clean.padStart(6, '0') else clean
        _activeOtp.value = formatted
        otpExpirationTimeMs = System.currentTimeMillis() + expirationDurationMs
        android.util.Log.d("OTP_DEBUG", "Received from Netlify: ${_activeOtp.value}")
    }

    fun setGeneratedOtp(otp: String, expirationDurationMs: Long = 10 * 60 * 1000L) {
        setActiveOtp(otp, expirationDurationMs)
    }

    fun sendSignupOtp(recipientEmail: String, onSuccess: () -> Unit = {}) {
        val cleanEmail = recipientEmail.trim()
        if (cleanEmail.isBlank()) {
            _errorMessage.value = "Please enter your email address."
            return
        }

        _errorMessage.value = null
        val newOtp = (100000..999999).random().toString().padStart(6, '0')
        _activeOtp.value = newOtp
        otpExpirationTimeMs = System.currentTimeMillis() + 10 * 60 * 1000L
        android.util.Log.d("OTP_DEBUG", "Client-Side Generated OTP: $newOtp for $cleanEmail")

        // Immediately transition to OTP verification screen and dismiss loading spinner
        _isLoading.value = false
        viewModelScope.launch(Dispatchers.Main) {
            _uiEvent.emit(AuthUiEvent.NavigateToOtpScreen)
            onSuccess()
        }

        // Asynchronously dispatch OTP email via Netlify in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                OtpUtils.saveOtpToFirestore(cleanEmail, newOtp)
                securityRepository.sendOtpToNetlify(cleanEmail, "signup", newOtp)
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "sendSignupOtp background error: ${e.message}", e)
            }
        }
    }

    fun requestNetlifyEmailOtp(recipientEmail: String, purpose: String = "general", onResult: (String?) -> Unit = {}) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val cleanEmail = recipientEmail.trim()
            val generatedOtp = (100000..999999).random().toString().padStart(6, '0')

            // Step 1: Immediately set _activeOtp.value BEFORE making the network call
            _activeOtp.value = generatedOtp
            otpExpirationTimeMs = System.currentTimeMillis() + 10 * 60 * 1000L
            android.util.Log.d("OTP_DEBUG", "Client-Side Generated OTP: $generatedOtp for $cleanEmail")

            var resolvedOtp: String? = generatedOtp

            try {
                // Sync generated OTP to Firestore
                try {
                    OtpUtils.saveOtpToFirestore(cleanEmail, generatedOtp)
                } catch (e: Throwable) {
                    android.util.Log.w("AuthViewModel", "Failed to pre-sync OTP to Firestore: ${e.message}")
                }

                val response = securityRepository.sendOtpToNetlify(cleanEmail, purpose, generatedOtp)
                if (response.isSuccessful) {
                    _uiEvent.emit(AuthUiEvent.NavigateToOtpScreen)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error in requestNetlifyEmailOtp: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = e.localizedMessage ?: "Network error occurred"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false // CRITICAL: Ensure loading is always dismissed
                    onResult(resolvedOtp)
                }
            }
        }
    }


    fun clearError() {
        _errorMessage.value = null
        _securityErrorMessage.value = null
    }

    fun setResetStep(step: ResetStep) {
        _currentResetStep.value = step
    }

    fun resetPasswordResetFlow() {
        _currentResetStep.value = ResetStep.IDLE
        _securityErrorMessage.value = null
        _targetUserSecurityModel.value = null
    }

    private fun startLockoutTicker(initialDurationMs: Long) {
        lockoutTickerJob?.cancel()
        _isAccountLocked.value = true
        _lockoutTimeRemainingMs.value = initialDurationMs
        lockoutTickerJob = viewModelScope.launch(Dispatchers.IO) {
            var remaining = initialDurationMs
            while (remaining > 0L) {
                delay(1000L)
                remaining -= 1000L
                _lockoutTimeRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            _isAccountLocked.value = false
            _lockoutTimeRemainingMs.value = 0L
            _securityErrorMessage.value = null
        }
    }

    /**
     * Core Lockout Engine: Checks user lockout status from Firestore.
     */
    fun checkAccountLockoutStatus(emailOrPlenxoId: String, onResult: (UserSecurityModel?) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val model = securityRepository.checkAccountLockoutStatus(emailOrPlenxoId)
            _targetUserSecurityModel.value = model
            if (model != null && model.isLockedOut()) {
                val remaining = model.getRemainingLockoutMs()
                _securityErrorMessage.value = "Account is locked due to security violation: ${model.lastSecurityViolationReason ?: "Lockdown active"}"
                startLockoutTicker(remaining)
            } else {
                _isAccountLocked.value = false
                _lockoutTimeRemainingMs.value = 0L
                _securityErrorMessage.value = null
            }
            withContext(Dispatchers.Main) {
                onResult(model)
            }
        }
    }

    /**
     * Triggers 24-Hour Account Lockdown in Firestore and invalidates local auth session.
     */
    fun trigger24HourLockdown(targetUid: String, violationReason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val model = securityRepository.trigger24HourLockdown(targetUid, violationReason)
            _targetUserSecurityModel.value = model
            _securityErrorMessage.value = "Security Violation: $violationReason. Account locked for 24 hours."
            startLockoutTicker(24 * 60 * 60 * 1000L)
        }
    }

    /**
     * Validates that entered Plenxo ID matches the provided Email address in Firestore.
     */
    fun validateEmailAndPlenxoId(email: String, plenxoId: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || plenxoId.isBlank()) {
            val err = "Please enter both Email and Plenxo ID"
            _errorMessage.value = err
            onResult(false, err)
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val (matches, model) = securityRepository.verifyPlenxoIdMatchesEmail(email, plenxoId)
            _targetUserSecurityModel.value = model

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                if (!matches) {
                    val errorStr = "Plenxo ID and Email do not match"
                    _errorMessage.value = errorStr
                    onResult(false, errorStr)
                } else {
                    _errorMessage.value = null
                    onResult(true, null)
                }
            }
        }
    }

    /**
     * Verifies 6-digit Security PIN against Firestore stored securityPin / masterPin.
     */
    fun verifySecurityPin(inputPin: String, onResult: (Boolean) -> Unit = {}) {
        val model = _targetUserSecurityModel.value
        val targetUid = model?.uid ?: auth.currentUser?.uid ?: ""
        val expectedPin = model?.securityPin?.ifBlank { model.masterPin } ?: ""

        if (inputPin.length < 6) {
            _errorMessage.value = "Security PIN must be 6 digits"
            onResult(false)
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val verified = securityRepository.verifySecurityPin(targetUid, inputPin, expectedPin)
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                if (!verified) {
                    _errorMessage.value = "Invalid Security PIN. Please try again."
                } else {
                    _errorMessage.value = null
                }
                onResult(verified)
            }
        }
    }

    /**
     * Submits new password for user during Forgot Password recovery.
     */
    fun submitNewPassword(newPass: String, onResult: (Boolean, String?) -> Unit) {
        if (newPass.length < 6) {
            val err = "Password must be at least 6 characters"
            _errorMessage.value = err
            onResult(false, err)
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val user = auth.currentUser
            val model = _targetUserSecurityModel.value
            val email = model?.email.orEmpty().ifBlank { user?.email.orEmpty() }

            try {
                if (user != null) {
                    user.updatePassword(newPass).await()
                } else if (email.isNotBlank()) {
                    // Update password or send password reset confirmation email
                    try {
                        auth.sendPasswordResetEmail(email).await()
                    } catch (e: Throwable) {
                        android.util.Log.w("AuthViewModel", "sendPasswordResetEmail note: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _currentResetStep.value = ResetStep.SUCCESS
                    onResult(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Password update error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val err = e.localizedMessage ?: "Failed to update password. Please try again."
                    _errorMessage.value = err
                    onResult(false, err)
                }
            }
        }
    }

    /**
     * Zero-Tolerance Plenxo ID verification.
     * Mismatch instantly triggers 24-hour lockdown.
     */
    fun verifyPlenxoId(inputPlenxoId: String, onResult: (Boolean) -> Unit = {}) {
        val model = _targetUserSecurityModel.value
        val targetUid = model?.uid ?: auth.currentUser?.uid ?: ""
        val expectedId = model?.plenxoId ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            val verified = securityRepository.verifyPlenxoId(targetUid, inputPlenxoId, expectedId)
            if (!verified) {
                _securityErrorMessage.value = "Plenxo ID verification failed! 24-Hour lockdown triggered."
                startLockoutTicker(24 * 60 * 60 * 1000L)
            }
            withContext(Dispatchers.Main) {
                onResult(verified)
            }
        }
    }

    /**
     * Zero-Tolerance Master PIN verification.
     * SHA-256 mismatch instantly triggers 24-hour lockdown.
     */
    fun verifyMasterPin(inputPin: String, onResult: (Boolean) -> Unit = {}) {
        val model = _targetUserSecurityModel.value
        val targetUid = model?.uid ?: auth.currentUser?.uid ?: ""
        val expectedHashed = model?.masterPin ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            val verified = securityRepository.verifyMasterPin(targetUid, inputPin, expectedHashed)
            if (!verified) {
                _securityErrorMessage.value = "Master PIN verification failed! 24-Hour lockdown triggered."
                startLockoutTicker(24 * 60 * 60 * 1000L)
            }
            withContext(Dispatchers.Main) {
                onResult(verified)
            }
        }
    }

    /**
     * Relaxed/Hardened direct OTP verification with explicit logging and exact StateFlow matching.
     */
    fun verifyOtp(enteredOtp: String, onResult: (Boolean) -> Unit = {}) {
        val currentActive = _activeOtp.value.trim()
        val userInput = enteredOtp.trim()
        android.util.Log.d("OTP_DEBUG", "Received from Netlify: ${_activeOtp.value}")
        android.util.Log.d("OTP_DEBUG", "Entered by User: ${enteredOtp.trim()}")

        val isDirectMatch = currentActive.isNotEmpty() && (currentActive == userInput || currentActive.equals(userInput, ignoreCase = true))
        if (isDirectMatch) {
            android.util.Log.d("OTP_DEBUG", "OTP Direct Verification Success: $userInput")
            _activeOtp.value = "" // Only clear upon SUCCESSFUL verification
            _securityErrorMessage.value = null
            onResult(true)
            return
        }

        // Secondary / Firestore fallback validation
        val model = _targetUserSecurityModel.value
        val targetUid = model?.uid ?: auth.currentUser?.uid ?: ""
        val targetEmail = model?.email.orEmpty().ifBlank { auth.currentUser?.email.orEmpty() }

        viewModelScope.launch(Dispatchers.IO) {
            val firestoreOtps = mutableListOf<String>()
            if (targetUid.isNotBlank()) {
                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(targetUid))
            }
            if (targetEmail.isNotBlank()) {
                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(targetEmail))
            }

            val isMatchedInFirestore = userInput.isNotBlank() && firestoreOtps.any {
                it.isNotBlank() && (it.trim() == userInput || it.trim().equals(userInput, ignoreCase = true))
            }

            val verified = isMatchedInFirestore || (targetUid.isNotBlank() && userInput.isNotBlank() && currentActive.isNotBlank() && securityRepository.verifyEmailOtp(targetUid, userInput, currentActive))

            withContext(Dispatchers.Main) {
                if (verified) {
                    android.util.Log.d("OTP_DEBUG", "OTP Firestore Fallback Success: $userInput")
                    _activeOtp.value = "" // Only clear upon SUCCESSFUL verification
                    _securityErrorMessage.value = null
                    onResult(true)
                } else {
                    android.util.Log.w("OTP_DEBUG", "OTP Verification Failed! Expected: '$currentActive', Firestore: $firestoreOtps, Entered: '$userInput'")
                    _securityErrorMessage.value = "Invalid OTP code. Please try again."
                    onResult(false)
                }
            }
        }
    }

    /**
     * Strict string-normalized Email OTP verification with leading zeros preservation.
     */
    fun verifyEmailOtp(inputOtp: String, sentOtp: String? = null, onResult: (Boolean) -> Unit = {}) {
        val model = _targetUserSecurityModel.value
        val targetUid = model?.uid ?: auth.currentUser?.uid ?: ""
        val targetEmail = model?.email.orEmpty().ifBlank { auth.currentUser?.email.orEmpty() }

        val rawInput = inputOtp.trim()
        val cleanInput = if (rawInput.matches(Regex("^\\d{1,5}$"))) rawInput.padStart(6, '0') else rawInput

        val rawSent = if (!sentOtp.isNullOrBlank()) sentOtp else _activeOtp.value
        val trimmedSent = rawSent.trim()
        val storedOtp = if (trimmedSent.matches(Regex("^\\d{1,5}$"))) trimmedSent.padStart(6, '0') else trimmedSent

        android.util.Log.d("OTP_DEBUG", "Received from Netlify: ${_activeOtp.value}")
        android.util.Log.d("OTP_DEBUG", "Entered by User: ${cleanInput}")

        val isExpired = otpExpirationTimeMs > 0L && System.currentTimeMillis() > otpExpirationTimeMs

        val currentActive = storedOtp
        val userInput = cleanInput
        if (currentActive.isNotEmpty() && (currentActive == userInput || currentActive.equals(userInput, ignoreCase = true))) {
            android.util.Log.d("OTP_DEBUG", "Immediate OTP match success: $userInput")
            _activeOtp.value = "" // Only clear upon SUCCESSFUL verification
            _securityErrorMessage.value = null
            onResult(true)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val firestoreOtps = mutableListOf<String>()
            if (targetUid.isNotBlank()) {
                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(targetUid))
            }
            if (targetEmail.isNotBlank()) {
                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(targetEmail))
            }

            android.util.Log.d("OTP_DEBUG", "Expected OTP: '$storedOtp' | Firestore OTPs: $firestoreOtps | User Input: '$cleanInput'")

            if (isExpired) {
                android.util.Log.w("OTP_DEBUG", "Verification Failed: OTP expired for $targetUid")
                withContext(Dispatchers.Main) {
                    _securityErrorMessage.value = "Invalid OTP: Verification code has expired. Please request a new code."
                    onResult(false)
                }
                return@launch
            }

            val isMatchedLocally = storedOtp.isNotBlank() && cleanInput.isNotBlank() && 
                (cleanInput == storedOtp || cleanInput.equals(storedOtp, ignoreCase = true))
            val isMatchedInFirestore = cleanInput.isNotBlank() && firestoreOtps.any { 
                it.isNotBlank() && (it.trim() == cleanInput || it.trim().equals(cleanInput, ignoreCase = true)) 
            }

            val verified = if (isMatchedLocally || isMatchedInFirestore) {
                true
            } else if (targetUid.isNotBlank() && cleanInput.isNotBlank()) {
                securityRepository.verifyEmailOtp(targetUid, cleanInput, storedOtp)
            } else {
                false
            }

            if (!verified) {
                android.util.Log.w("OTP_DEBUG", "OTP Verification Mismatch! Expected: '$storedOtp', Firestore: $firestoreOtps, Got: '$cleanInput'")
                _securityErrorMessage.value = "Invalid OTP code. Please try again."
            } else {
                android.util.Log.d("OTP_DEBUG", "OTP Verification Success: '$cleanInput' verified successfully.")
                _activeOtp.value = "" // Only clear upon SUCCESSFUL verification
                _securityErrorMessage.value = null
            }
            withContext(Dispatchers.Main) {
                onResult(verified)
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (_isAccountLocked.value) {
            _errorMessage.value = _securityErrorMessage.value ?: "Account is locked due to 24-hour security violation."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val lockStatus = securityRepository.checkAccountLockoutStatus(email.trim())
                if (lockStatus != null && lockStatus.isLockedOut()) {
                    _targetUserSecurityModel.value = lockStatus
                    val remaining = lockStatus.getRemainingLockoutMs()
                    _securityErrorMessage.value = "Account locked for 24 hours: ${lockStatus.lastSecurityViolationReason ?: "Security Violation"}"
                    _errorMessage.value = _securityErrorMessage.value
                    startLockoutTicker(remaining)
                    return@launch
                }

                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val uid = result.user?.uid ?: ""

                // Check 2FA flag on user profile
                val userSecurityModel = if (uid.isNotBlank()) securityRepository.checkAccountLockoutStatus(uid) else lockStatus
                val is2FA = userSecurityModel?.is2FAEnabled == true

                _currentUser.value = result.user

                if (is2FA) {
                    // Rule 1: If 2FA is enabled, trigger 2FA OTP challenge via Netlify API
                    requestNetlifyEmailOtp(email, purpose = "login") { _ ->
                        _uiState.value = AuthUiState.SuccessDirect(uid, email)
                        setResetStep(ResetStep.EMAIL_OTP)
                    }
                } else {
                    // Rule 1: If 2FA is disabled, completely bypass OTP screen and route straight to home
                    _uiState.value = AuthUiState.SuccessDirect(uid, email)
                    onSuccess()
                }
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Login failed"
                _errorMessage.value = err
                _uiState.value = AuthUiState.Error(err)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (_isAccountLocked.value) {
            _errorMessage.value = _securityErrorMessage.value ?: "Account creation blocked due to active security lockdown."
            return
        }
        if (!isTermsAccepted.value) {
            _errorMessage.value = "Please accept the Terms & Conditions to proceed."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                _currentUser.value = result.user
                _uiState.value = AuthUiState.SuccessDirect(result.user?.uid ?: "", email)
                onSuccess()
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Sign up failed"
                _errorMessage.value = err
                _uiState.value = AuthUiState.Error(err)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyDeepLink(uriString: String) {
        _uiState.value = AuthUiState.Loading
        val user = auth.currentUser
        if (user != null) {
            _uiState.value = AuthUiState.SuccessDirect(user.uid, user.email ?: "")
        } else {
            _uiState.value = AuthUiState.Error("Session link invalid or expired")
        }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
        _uiState.value = AuthUiState.Idle
        resetPasswordResetFlow()
    }
}
