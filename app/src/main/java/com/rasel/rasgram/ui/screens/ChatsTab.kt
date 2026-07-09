package com.rasel.rasgram

import android.Manifest
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import com.google.firebase.firestore.PersistentCacheSettings
import android.content.ClipData
import androidx.fragment.app.FragmentActivity
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.roundToInt
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.source
import org.json.JSONObject
import org.webrtc.*
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ==================== CHATS TAB ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab(
    currentUser: User,
    selectedContact: User?,
    onContactSelect: (User) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdate: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var latestMessages by remember { mutableStateOf<Map<String, Message>>(emptyMap()) }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showNewBroadcast by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Contact Sync (WhatsApp style) ───────────────────────────────────────
    var deviceContactNumbers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var contactsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
        if (granted) {
            deviceContactNumbers = getDeviceContactNumbers(context)
        }
    }

    // Permission check + load contacts on first open
    LaunchedEffect(contactsPermissionGranted) {
        if (contactsPermissionGranted) {
            deviceContactNumbers = getDeviceContactNumbers(context)
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    // Real-time users
    LaunchedEffect(Unit) {
        db.collection("chat_users").addSnapshotListener { snapshot, _ ->
            snapshot?.documents?.mapNotNull { doc ->
                doc.data?.let { d ->
                    User(
                        uid = d["uid"] as? String ?: "",
                        name = d["name"] as? String ?: "",
                        mobile = doc.id,
                        avatarUrl = d["avatarUrl"] as? String ?: "",
                        lastActive = d["lastActive"] as? Long ?: 0,
                        typingTo = d["typingTo"] as? String,
                        statusVisible = d["statusVisible"] as? Boolean ?: true,
                        about = d["about"] as? String ?: ""
                    )
                }
            }?.filter { it.mobile != currentUser.mobile }?.also { users = it }
        }
    }

    // Fast batch latest messages
    LaunchedEffect(users) {
        users.forEach { user ->
            val chatId = generateChatId(currentUser.mobile, user.mobile)
            db.collection("pvt_msg_$chatId")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snap, _ ->
                    snap?.documents?.firstOrNull()?.let { doc ->
                        doc.data?.let { d ->
                            val msg = Message(
                                id = doc.id,
                                text = AESCrypto.decrypt(chatId, d["text"] as? String ?: "") ?: "",
                                senderMobile = d["senderMobile"] as? String ?: "",
                                timestamp = d["timestamp"] as? Long ?: 0,
                                timeString = d["timeString"] as? String ?: "",
                                fileUrl = d["fileUrl"] as? String,
                                fileType = d["fileType"] as? String,
                                read = d["read"] as? Boolean ?: false,
                                isCallLog = d["isCallLog"] as? Boolean ?: false,
                                isDeleted = d["isDeleted"] as? Boolean ?: false
                            )
                            latestMessages = latestMessages + (user.mobile to msg)
                        }
                    }

                    // Unread count
                    db.collection("pvt_msg_$chatId")
                        .whereEqualTo("senderMobile", user.mobile)
                        .whereEqualTo("read", false)
                        .get().addOnSuccessListener { qs ->
                            unreadCounts = unreadCounts + (user.mobile to qs.size())
                        }
                }
        }
    }

    // WhatsApp style: শুধু phonebook contacts যারা RasGram-এ আছে
    val filteredUsers = users.filter { user ->
        val isInPhonebook = if (deviceContactNumbers.isEmpty()) {
            // Permission না পেলে সব users দেখাও (fallback)
            true
        } else {
            deviceContactNumbers.contains(user.mobile)
        }
        isInPhonebook && (
            user.name.contains(searchQuery, ignoreCase = true) ||
            user.mobile.contains(searchQuery)
        )
    }.sortedWith(compareByDescending<User> { latestMessages[it.mobile]?.timestamp ?: 0L })

    Column(modifier = modifier.fillMaxHeight().background(RasGramTheme.DarkBackground)) {
        // Header
        if (showSearch) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = { searchQuery = ""; showSearch = false }
            )
        } else {
            ChatsHeader(
                currentUser = currentUser,
                onSearchClick = { showSearch = true },
                onSettingsClick = { showSettings = true },
                onNewGroupClick = { showNewGroup = true },
                onNewBroadcastClick = { showNewBroadcast = true },
                onAddContactClick = { showAddContact = true },
                onToggleTheme = onToggleTheme,
                onLogout = onLogout
            )
        }

        HorizontalDivider(color = RasGramTheme.DividerColor, thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (filteredUsers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonAddAlt, null, modifier = Modifier.size(64.dp), tint = RasGramTheme.TextMuted.copy(0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No contacts on RasGram", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (deviceContactNumbers.isEmpty())
                                "Allow contacts permission to see your phonebook contacts who use RasGram."
                            else
                                "None of your phonebook contacts are on RasGram yet. Invite them!",
                            color = RasGramTheme.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            items(filteredUsers, key = { it.mobile }) { user ->
                ContactItem(
                    user = user,
                    latestMessage = latestMessages[user.mobile],
                    unreadCount = unreadCounts[user.mobile] ?: 0,
                    isSelected = selectedContact?.mobile == user.mobile,
                    currentUserMobile = currentUser.mobile,
                    onClick = { onContactSelect(user) }
                )
                HorizontalDivider(color = RasGramTheme.DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 80.dp))
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentUser = currentUser,
            onDismiss = { showSettings = false },
            onSave = { updated ->
                showSettings = false
                onUserUpdate(updated)
            }
        )
    }
    if (showNewGroup) NewGroupDialog(onDismiss = { showNewGroup = false }, currentUser = currentUser)
}

@Composable
fun ChatsHeader(
    currentUser: User,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onNewBroadcastClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(RasGramTheme.DarkPanel).padding(horizontal = 16.dp).height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Ras", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.ExtraBold)
            Text("Gram", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.Green, fontWeight = FontWeight.ExtraBold)
        }
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Default.Search, null, tint = RasGramTheme.TextMuted)
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, null, tint = RasGramTheme.TextMuted)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(RasGramTheme.DarkPanel)
            ) {
                DropdownMenuItem(
                    text = { Text("New Group", color = RasGramTheme.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.People, null, tint = RasGramTheme.TextMuted) },
                    onClick = { onNewGroupClick(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("New Broadcast", color = RasGramTheme.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Campaign, null, tint = RasGramTheme.TextMuted) },
                    onClick = { onNewBroadcastClick(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Settings", color = RasGramTheme.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = RasGramTheme.TextMuted) },
                    onClick = { onSettingsClick(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (true) "Light Mode" else "Dark Mode", color = RasGramTheme.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.WbSunny, null, tint = RasGramTheme.TextMuted) },
                    onClick = { onToggleTheme(); showMenu = false }
                )
                HorizontalDivider(color = RasGramTheme.Border)
                DropdownMenuItem(
                    text = { Text("Logout", color = RasGramTheme.Red) },
                    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = RasGramTheme.Red) },
                    onClick = { onLogout(); showMenu = false }
                )
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RasGramTheme.DarkPanel).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.ArrowBack, null, tint = RasGramTheme.TextMuted)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search...", color = RasGramTheme.TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = RasGramTheme.TextPrimary,
                unfocusedTextColor = RasGramTheme.TextPrimary,
                cursorColor = RasGramTheme.Green
            )
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Clear, null, tint = RasGramTheme.TextMuted)
            }
        }
    }
}

// ==================== CONTACT ITEM ====================
@Composable
fun ContactItem(
    user: User,
    latestMessage: Message?,
    unreadCount: Int,
    isSelected: Boolean,
    currentUserMobile: String,
    onClick: () -> Unit
) {
    val isOnline = user.lastActive > System.currentTimeMillis() - ONLINE_THRESHOLD_MS
    val isTyping = user.typingTo != null

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = when {
            isSelected -> RasGramTheme.DarkPanel
            else -> Color.Transparent
        }
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar + online dot
            Box(modifier = Modifier.size(52.dp)) {
                AsyncImage(
                    model = user.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.name.replace(" ", "+")}&background=008069&color=fff&bold=true&size=128" },
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .border(2.dp, RasGramTheme.DarkBackground, CircleShape)
                            .background(RasGramTheme.OnlineGreen, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = RasGramTheme.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        latestMessage?.timeString ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unreadCount > 0) RasGramTheme.Green else RasGramTheme.TextMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (isTyping) {
                        Text("typing...", style = MaterialTheme.typography.bodySmall, color = RasGramTheme.Green, fontWeight = FontWeight.Medium)
                    } else {
                        // Tick icon for sent messages
                        if (latestMessage?.senderMobile == currentUserMobile && latestMessage != null) {
                            Icon(
                                imageVector = when {
                                    latestMessage.isPending -> Icons.Default.AccessTime
                                    latestMessage.read -> Icons.Default.Done
                                    latestMessage.delivered -> Icons.Default.Done
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(end = 2.dp),
                                tint = when {
                                    latestMessage.read -> RasGramTheme.BlueTick
                                    latestMessage.isPending -> RasGramTheme.TextMuted
                                    else -> RasGramTheme.TextMuted
                                }
                            )
                        }
                        val previewText = when {
                            latestMessage?.isDeleted == true -> "🚫 This message was deleted"
                            latestMessage?.text?.isNotEmpty() == true -> latestMessage.text
                            latestMessage != null -> getFileTypePreview(latestMessage)
                            else -> "Tap to start chatting"
                        }
                        Text(
                            previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unreadCount > 0 && latestMessage?.senderMobile != currentUserMobile) RasGramTheme.TextPrimary else RasGramTheme.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (unreadCount > 0 && latestMessage?.senderMobile != currentUserMobile) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = RasGramTheme.Green) {
                                Text(
                                    if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

