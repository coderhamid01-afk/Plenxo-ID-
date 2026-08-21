package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UserProfile
import com.example.ui.theme.PlenxoColors
import com.example.ui.components.ProfileRingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDiscoveryScreen(
    currentUser: UserProfile?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    users: List<UserProfile>,
    requestedUserIds: Set<String>,
    contacts: Set<String> = emptySet(),
    onAddFriend: (String) -> Unit,
    onStartChat: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // Deep Slate
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Plenxo ID Lookup input
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Search users by unique Plenxo ID only",
                color = Color(0xFF8B949E),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { input -> onSearchQueryChange(input.filter { it.isDigit() }) },
                    prefix = {
                        Text(
                            text = "PX-",
                            color = Color(0xFF58A6FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Enter numbers",
                            color = Color(0xFF8B949E),
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Icon",
                            tint = Color(0xFF8B949E)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF8B949E)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF010409),
                        unfocusedContainerColor = Color(0xFF010409),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("user_search_input")
                )

                Button(
                    onClick = onSearchClick,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58A6FF))
                ) {
                    Text("Search", color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Results Layout
        if (searchQuery.isNotEmpty() && users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No user found.",
                    color = Color(0xFF8B949E),
                    fontSize = 16.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (users.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("users_search_results_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(users) { user ->
                        val isRequested = requestedUserIds.contains(user.uid)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_discovery_row_${user.uid}"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Catbox Profile Pic with Coil AsyncImage
                                ProfileRingBox(ringId = user.profileRingId, ringPadding = 3.dp, borderWidth = 4.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0D1117)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.profilePicUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(user.profilePicUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                                contentDescription = "User Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Placeholder",
                                                tint = Color(0xFF8B949E),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = user.displayName.ifBlank { "User" },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF0F6FC)
                                    )
                                    Text(
                                        text = "@${user.plenxoId}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF58A6FF),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = { onStartChat(user.uid) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("message_user_button_${user.uid}"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF58A6FF),
                                        contentColor = Color(0xFF0D1117)
                                    )
                                ) {
                                    Text(
                                        text = "Message",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
