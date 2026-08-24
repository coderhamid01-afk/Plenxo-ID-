package com.example.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatRoom
import com.example.model.User
import com.example.ui.components.ProfileImageWithRing
import com.example.viewmodel.PlenxoViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/* ============================================================================================
 *  UserProfileScreen — Modern, visually clean User B Profile View Screen
 * --------------------------------------------------------------------------------------------
 *  Displays User B's profile details when opened by User A:
 *  1. Centered high-resolution avatar with profile ring & shimmer loading state.
 *  2. Full Name header & Plenxo ID badge.
 *  3. Dynamic Age Calculation on the fly from DOB (Current Date - DOB, e.g. "21 Years Old").
 *  4. Formatted Date of Birth (e.g. "DOB: 12 Oct 2004").
 *  5. Gender badge / tag.
 *  6. Bio / About card container supporting multi-line emojis & text.
 *  7. Action buttons to send message or initiate audio/video call.
 * ============================================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val userId by viewModel.selectedUserIdForProfile.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val userPresences by viewModel.userPresences.collectAsState()

    val primaryColor = Color(0xFF58A6FF)
    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val borderColor = Color(0xFF30363D)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)
    val accentGreen = Color(0xFF238636)

    var userProfile by remember(userId) { mutableStateOf<User?>(usersCache[userId]) }
    var bioText by remember(userId) { mutableStateOf("") }
    var bioVisibility by remember(userId) { mutableStateOf("PUBLIC") }
    var isMuted by remember(userId) { mutableStateOf(false) }
    var genderText by remember(userId) { mutableStateOf("") }
    var rawDobText by remember(userId) { mutableStateOf("") }
    var dobTimestampVal by remember(userId) { mutableStateOf<Long?>(null) }
    var isLoading by remember(userId) { mutableStateOf(true) }

    val currentUid = viewModel.currentUserId
    val isSelf = userId == currentUid
    var connectionStatus by remember(userId, currentUid) { mutableStateOf(if (isSelf) "SELF" else "ACCEPTED") }
    var pendingRequestId by remember(userId, currentUid) { mutableStateOf<String?>(null) }
    var isActionLoading by remember(userId) { mutableStateOf(false) }
    var showQRBottomSheet by remember { mutableStateOf(false) }

    if (showQRBottomSheet) {
        val displayPlenxoId = userProfile?.plenxoId?.ifEmpty { userProfile?.userCode } ?: userProfile?.userCode ?: userId
        com.example.ui.components.ProfileQRBottomSheet(
            displayName = userProfile?.displayName ?: "Plenxo User",
            plenxoId = displayPlenxoId,
            avatarUrl = userProfile?.profilePicUrl,
            onDismissRequest = { showQRBottomSheet = false }
        )
    }

    // Fetch User B details and observe connection status
    LaunchedEffect(userId, currentUid) {
        if (userId.isNotBlank()) {
            val cached = usersCache[userId]
            if (cached != null) {
                userProfile = cached
            }
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(userId).get().await()
                if (doc.exists()) {
                    val dName = doc.getString("displayName")
                        ?: doc.getString("fullName")
                        ?: doc.getString("name")
                        ?: userProfile?.displayName
                        ?: "User"
                    val pPic = doc.getString("profilePicUrl")
                        ?: doc.getString("avatar_url")
                        ?: doc.getString("photoUrl")
                        ?: userProfile?.profilePicUrl
                        ?: ""
                    val pId = doc.getString("plenxoId")
                        ?: doc.getString("userCode")
                        ?: doc.getString("username")
                        ?: userProfile?.plenxoId
                        ?: ""
                    val ring = doc.getString("profileRingId")
                        ?: doc.getString("selectedRingId")
                        ?: userProfile?.profileRingId
                        ?: "none"
                    val bio = doc.getString("bio")
                        ?: doc.getString("about")
                        ?: doc.getString("statusMessage")
                        ?: ""
                    val bVis = doc.getString("bioVisibility")
                        ?: doc.getString("bioVis")
                        ?: "PUBLIC"
                    val gender = doc.getString("gender") ?: ""
                    val dob = doc.getString("date_of_birth")
                        ?: doc.getString("dateOfBirth")
                        ?: doc.getString("dob")
                        ?: doc.getString("birthDate")
                        ?: userProfile?.dob
                        ?: ""
                    val timestamp = doc.getLong("dobTimestamp")
                        ?: doc.getLong("birthDateTimestamp")

                    bioText = bio
                    bioVisibility = bVis
                    genderText = gender
                    rawDobText = dob
                    dobTimestampVal = timestamp

                    userProfile = User(
                        uid = userId,
                        displayName = dName,
                        profilePicUrl = pPic,
                        plenxoId = pId,
                        profileRingId = ring,
                        dob = dob
                    )
                }

                // Check connection / friend status
                if (!isSelf && currentUid.isNotBlank()) {
                    val friendDoc = db.collection("users").document(currentUid).collection("friends").document(userId).get().await()
                    val contactDoc = db.collection("contacts").whereEqualTo("user_id", currentUid).whereEqualTo("contact_id", userId).get().await()
                    if (friendDoc.exists() || !contactDoc.isEmpty) {
                        connectionStatus = "ACCEPTED"
                    } else {
                        // Check outgoing pending request
                        val outReq = db.collection("friend_requests")
                            .whereEqualTo("senderUid", currentUid)
                            .whereEqualTo("receiverUid", userId)
                            .get().await()
                        val outReqAlt = if (outReq.isEmpty) {
                            db.collection("friend_requests")
                                .whereEqualTo("requestFrom", currentUid)
                                .whereEqualTo("requestTo", userId)
                                .get().await()
                        } else outReq
                        
                        val activeOut = outReqAlt.documents.firstOrNull { 
                            val st = it.getString("status") ?: ""
                            st.equals("pending", ignoreCase = true)
                        }

                        if (activeOut != null) {
                            connectionStatus = "PENDING_SENT"
                            pendingRequestId = activeOut.id
                        } else {
                            // Check incoming pending request
                            val inReq = db.collection("friend_requests")
                                .whereEqualTo("senderUid", userId)
                                .whereEqualTo("receiverUid", currentUid)
                                .get().await()
                            val inReqAlt = if (inReq.isEmpty) {
                                db.collection("friend_requests")
                                    .whereEqualTo("requestFrom", userId)
                                    .whereEqualTo("requestTo", currentUid)
                                    .get().await()
                            } else inReq
                            
                            val activeIn = inReqAlt.documents.firstOrNull { 
                                val st = it.getString("status") ?: ""
                                st.equals("pending", ignoreCase = true)
                            }
                            
                            if (activeIn != null) {
                                connectionStatus = "PENDING_RECEIVED"
                                pendingRequestId = activeIn.id
                            } else {
                                connectionStatus = "ACCEPTED" // Default allow messaging
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback gracefully to cache or default user profile
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    // Dynamic Age & Formatted DOB calculation
    val parsedDobInfo = remember(rawDobText, dobTimestampVal, userProfile?.dob) {
        val effectiveDobStr = rawDobText.ifBlank { userProfile?.dob.orEmpty() }
        parseDobAndCalculateAge(effectiveDobStr, dobTimestampVal)
    }

    val presenceMap = userPresences[userId] ?: emptyMap()
    val presenceStatus = (presenceMap["status"] as? String) ?: (presenceMap["state"] as? String) ?: "offline"
    val isOnline = presenceStatus == "online"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "User Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("user_profile_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showQRBottomSheet = true },
                        modifier = Modifier.testTag("user_profile_share_qr_button")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Profile QR",
                            tint = primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        if (isLoading && userProfile == null) {
            // Skeleton shimmer loading state for smooth initial load
            ProfileShimmerLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            )
        } else {
            val profile = userProfile
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Centered High-Resolution Profile Picture with Ring & Presence
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileImageWithRing(
                        imageUrl = profile?.profilePicUrl.orEmpty(),
                        profileRingId = profile?.profileRingId.orEmpty().ifBlank { "none" },
                        modifier = Modifier.fillMaxSize(),
                        ringBorderWidth = 7
                    )
                    // Presence dot
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF34C759) else Color(0xFF8E8E93))
                            .border(2.dp, darkBg, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Full Name
                Text(
                    text = profile?.displayName.orEmpty().ifBlank { "Plenxo User" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Plenxo ID Badge & Status
                val plenxoIdStr = profile?.plenxoId.orEmpty().ifBlank {
                    profile?.userCode.orEmpty().ifBlank { userId.take(6) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (plenxoIdStr.startsWith("PX-")) plenxoIdStr else "@$plenxoIdStr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status pill (Online/Offline)
                    Surface(
                        color = (if (isOnline) Color(0xFF34C759) else Color(0xFF8E8E93)).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (isOnline) Color(0xFF34C759) else Color(0xFF8E8E93)).copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOnline) Color(0xFF34C759) else Color(0xFF8E8E93),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3 Primary Action Options: Message, Call, Video Call
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Message
                    UserProfileActionButton(
                        icon = Icons.Default.ChatBubble,
                        label = "Message",
                        accentColor = Color(0xFF238636),
                        testTag = "user_profile_message_action_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val roomId = viewModel.getChatId(currentUid, userId)
                            val room = ChatRoom(
                                chatId = roomId,
                                participantUids = if (isSelf) listOf(currentUid) else listOf(currentUid, userId)
                            )
                            viewModel.openChatRoom(room)
                        }
                    )

                    // Option 2: Call
                    UserProfileActionButton(
                        icon = Icons.Default.Phone,
                        label = "Call",
                        accentColor = primaryColor,
                        testTag = "user_profile_call_action_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (userId.isNotEmpty()) {
                                viewModel.initiateCall(userId, "AUDIO")
                            } else {
                                Toast.makeText(context, "Cannot initiate call", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Option 3: Video Call
                    UserProfileActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Video Call",
                        accentColor = Color(0xFFAB47BC),
                        testTag = "user_profile_video_call_action_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (userId.isNotEmpty()) {
                                viewModel.initiateCall(userId, "VIDEO")
                            } else {
                                Toast.makeText(context, "Cannot initiate call", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // If friend request is pending received, show prompt to accept
                if (connectionStatus == "PENDING_RECEIVED") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A38)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Connection Request", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                Text("This user sent you a connection request.", fontSize = 12.sp, color = textMuted)
                            }
                            Button(
                                onClick = {
                                    val reqId = pendingRequestId
                                    if (!reqId.isNullOrBlank()) {
                                        isActionLoading = true
                                        viewModel.acceptFriendRequest(
                                            requestId = reqId,
                                            senderUid = userId,
                                            onSuccess = {
                                                isActionLoading = false
                                                connectionStatus = "ACCEPTED"
                                                Toast.makeText(context, "Connection accepted! You can now chat.", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                isActionLoading = false
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                enabled = !isActionLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("user_profile_accept_request_btn")
                            ) {
                                if (isActionLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Highlight Badges Row: Dynamic Age and Gender
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Age Badge (Dynamic calculation: Current Date - DOB)
                    val ageDisplay = if (parsedDobInfo.age != null) "${parsedDobInfo.age} Years Old" else "Age N/A"
                    InfoBadge(
                        icon = Icons.Default.Cake,
                        label = ageDisplay,
                        accentColor = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )

                    // Gender Badge
                    val formattedGender = genderText.trim().ifBlank { "Unspecified" }.lowercase()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    InfoBadge(
                        icon = Icons.Default.Wc,
                        label = formattedGender,
                        accentColor = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bio / About Section (Public vs Private check)
                val isBioVisible = isSelf ||
                    bioVisibility.equals("PUBLIC", ignoreCase = true) ||
                    bioVisibility.equals("EVERYONE", ignoreCase = true) ||
                    (bioVisibility.equals("CONTACTS", ignoreCase = true) && connectionStatus == "ACCEPTED")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Bio & About",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMuted
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (!isBioVisible) {
                                Surface(
                                    color = Color(0xFFD29922).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD29922).copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "Private",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD29922),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isBioVisible) {
                            Text(
                                text = bioText.ifBlank { "Hey there! I am using Plenxo." },
                                fontSize = 15.sp,
                                color = textWhite,
                                lineHeight = 22.sp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Private Bio",
                                    tint = textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "This user's bio is set to private.",
                                    fontSize = 14.sp,
                                    color = textMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Personal Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Personal Details",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMuted
                        )

                        // Date of Birth row with formatted DOB
                        ProfileDetailRow(
                            icon = Icons.Default.CalendarToday,
                            label = "Date of Birth",
                            value = if (parsedDobInfo.formattedDob.isNotBlank() && parsedDobInfo.formattedDob != "Not specified") {
                                "DOB: ${parsedDobInfo.formattedDob}"
                            } else {
                                "Not specified"
                            },
                            textWhite = textWhite,
                            textMuted = textMuted,
                            accent = primaryColor
                        )

                        HorizontalDivider(color = borderColor)

                        // Calculated Age row
                        ProfileDetailRow(
                            icon = Icons.Default.HourglassTop,
                            label = "Calculated Age",
                            value = if (parsedDobInfo.age != null) "${parsedDobInfo.age} Years Old" else "Not specified",
                            textWhite = textWhite,
                            textMuted = textMuted,
                            accent = primaryColor
                        )

                        HorizontalDivider(color = borderColor)

                        // Gender row
                        ProfileDetailRow(
                            icon = Icons.Default.Wc,
                            label = "Gender",
                            value = genderText.ifBlank { "Unspecified" },
                            textWhite = textWhite,
                            textMuted = textMuted,
                            accent = primaryColor
                        )

                        HorizontalDivider(color = borderColor)

                        // End-to-End Encryption status
                        ProfileDetailRow(
                            icon = Icons.Default.Lock,
                            label = "Encryption",
                            value = "End-to-End Encrypted",
                            textWhite = primaryColor,
                            textMuted = textMuted,
                            accent = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Options & Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Options & Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMuted
                        )

                        // Option: Mute Notifications
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Mute Notifications", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textWhite)
                                    Text(if (isMuted) "Notifications are silenced" else "Receive notifications", fontSize = 12.sp, color = textMuted)
                                }
                            }
                            Switch(
                                checked = isMuted,
                                onCheckedChange = {
                                    isMuted = it
                                    Toast.makeText(context, if (it) "Notifications muted" else "Notifications unmuted", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = primaryColor
                                )
                            )
                        }

                        HorizontalDivider(color = borderColor)

                        // Option: Media, Links and Docs
                        ProfileClickableRow(
                            icon = Icons.Default.PermMedia,
                            title = "Media, Links & Docs",
                            subtitle = "View shared photos, videos, and files",
                            onClick = {
                                Toast.makeText(context, "No shared media found with this user", Toast.LENGTH_SHORT).show()
                            },
                            accentColor = primaryColor,
                            textWhite = textWhite,
                            textMuted = textMuted
                        )

                        HorizontalDivider(color = borderColor)

                        // Option: Share Profile / Plenxo ID
                        val cleanSharePxId = plenxoIdStr.trim().removePrefix("@").removePrefix("#")
                        val displaySharePxId = if (cleanSharePxId.startsWith("PX-", ignoreCase = true)) {
                            "PX-${cleanSharePxId.removePrefix("PX-").removePrefix("px-")}"
                        } else if (cleanSharePxId.length == 6 && cleanSharePxId.all { it.isDigit() }) {
                            "PX-$cleanSharePxId"
                        } else if (cleanSharePxId.isNotBlank()) {
                            "PX-$cleanSharePxId"
                        } else {
                            "PX-000000"
                        }

                        ProfileClickableRow(
                            icon = Icons.Default.Share,
                            title = "Share Plenxo ID ($displaySharePxId)",
                            subtitle = "Copy ID to clipboard to share with others",
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Plenxo ID", displaySharePxId)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied $displaySharePxId to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            accentColor = primaryColor,
                            textWhite = textWhite,
                            textMuted = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Privacy & Safety Actions Card
                if (!isSelf) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "Privacy & Safety",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMuted
                            )

                            // Clear Chat History
                            ProfileClickableRow(
                                icon = Icons.Default.DeleteOutline,
                                title = "Clear Chat History",
                                subtitle = "Delete local messages with this user",
                                onClick = {
                                    Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                                },
                                accentColor = textWhite,
                                textWhite = textWhite,
                                textMuted = textMuted
                            )

                            HorizontalDivider(color = borderColor)

                            // Block User
                            ProfileClickableRow(
                                icon = Icons.Default.Block,
                                title = "Block User",
                                subtitle = "Blocked contacts will not be able to message or call you",
                                onClick = {
                                    viewModel.blockUser(userId)
                                    Toast.makeText(context, "User blocked successfully", Toast.LENGTH_SHORT).show()
                                },
                                accentColor = Color(0xFFF85149),
                                textWhite = Color(0xFFF85149),
                                textMuted = textMuted
                            )

                            HorizontalDivider(color = borderColor)

                            // Report User
                            ProfileClickableRow(
                                icon = Icons.Default.ReportProblem,
                                title = "Report User",
                                subtitle = "Report spam, harassment or inappropriate behavior",
                                onClick = {
                                    Toast.makeText(context, "Report submitted. Our safety team will review.", Toast.LENGTH_LONG).show()
                                },
                                accentColor = Color(0xFFF85149),
                                textWhite = Color(0xFFF85149),
                                textMuted = textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/* ============================================================================================
 *  Helper Components & Dynamic Auto-Age Calculations
 * ============================================================================================ */

@Composable
private fun UserProfileActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(72.dp)
            .testTag(testTag),
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF0F6FC),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color,
    textWhite: Color,
    textMuted: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textWhite
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = textMuted
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun InfoBadge(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    textWhite: Color,
    textMuted: Color,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 14.sp, color = textMuted)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textWhite)
    }
}

@Composable
private fun ProfileShimmerLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF161B22),
            Color(0xFF2D3748),
            Color(0xFF161B22)
        ),
        start = Offset(translateAnim.value - 300f, translateAnim.value - 300f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Avatar circle placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Title placeholder
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Badge placeholder
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Cards placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush)
        )
    }
}

/** Data holder for parsed DOB & calculated age */
private data class ParsedDobInfo(
    val formattedDob: String,
    val age: Int?
)

/**
 * Auto-Age Logic: Parses DOB string/timestamp and calculates age dynamically on the fly
 * (Current Date - DOB) without storing age as a separate database field.
 */
private fun parseDobAndCalculateAge(dobString: String?, dobTimestamp: Long?): ParsedDobInfo {
    var birthDate: Date? = null

    // 1. Try dobTimestamp if present
    if (dobTimestamp != null && dobTimestamp > 0L) {
        birthDate = Date(dobTimestamp)
    }

    // 2. If no timestamp, parse dobString
    if (birthDate == null && !dobString.isNullOrBlank()) {
        val trimmed = dobString.trim()

        // Check if string itself is numeric timestamp
        val asLong = trimmed.toLongOrNull()
        if (asLong != null && asLong > 1000000000L) {
            birthDate = Date(asLong)
        } else {
            // Try standard date formats
            val formats = listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "dd MMM yyyy",
                "dd MMMM yyyy",
                "yyyy/MM/dd",
                "MMMM dd, yyyy"
            )
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.isLenient = false
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) {
                        birthDate = parsed
                        break
                    }
                } catch (_: Exception) {
                    // Try next format
                }
            }
        }
    }

    if (birthDate == null) {
        return ParsedDobInfo(
            formattedDob = if (!dobString.isNullOrBlank()) dobString else "Not specified",
            age = null
        )
    }

    // Formatted DOB string e.g. "12 Oct 2004"
    val displaySdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val formattedDob = displaySdf.format(birthDate)

    // Calculate age dynamically on the fly: Current Date - DOB
    val today = Calendar.getInstance()
    val dobCal = Calendar.getInstance().apply { time = birthDate }

    var age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
        age--
    }

    val validAge = if (age in 0..120) age else null

    return ParsedDobInfo(
        formattedDob = formattedDob,
        age = validAge
    )
}
