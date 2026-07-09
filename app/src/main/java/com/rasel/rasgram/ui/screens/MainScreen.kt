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

// ==================== MAIN SCREEN (TABS) ====================
@Composable
fun MainScreen(
    currentUser: User,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdate: (User) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    // Request permissions dynamically
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    // Listen for incoming calls when app is open (e.g. from Web version)
    LaunchedEffect(currentUser.mobile) {
        db.collection("calls")
            .whereEqualTo("callee", currentUser.mobile)
            .whereEqualTo("status", "calling")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val intent = Intent(context, MainActivity::class.java).apply {
                            action = "ACTION_INCOMING_CALL"
                            putExtra("callId", change.document.id)
                            putExtra("callerMobile", data["caller"] as? String ?: "")
                            putExtra("callerName", data["callerName"] as? String ?: "")
                            putExtra("callType", data["type"] as? String ?: "audio")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                    }
                }
            }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedContact by remember { mutableStateOf<User?>(null) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var showCallUI by remember { mutableStateOf(false) }
    var selectedStatusUser by remember { mutableStateOf<List<Status>?>(null) }
    var callType by remember { mutableStateOf("audio") }
    var callContact by remember { mutableStateOf<User?>(null) }
    var liveCurrentUser by remember { mutableStateOf(currentUser) }
    val isCompact = isCompactScreen()

    // Keep user online + sync profile
    LaunchedEffect(currentUser.mobile) {
        db.collection("chat_users").document(currentUser.mobile).addSnapshotListener { snap, _ ->
            snap?.data?.let { d ->
                liveCurrentUser = liveCurrentUser.copy(
                    name = d["name"] as? String ?: liveCurrentUser.name,
                    avatarUrl = d["avatarUrl"] as? String ?: liveCurrentUser.avatarUrl,
                    about = d["about"] as? String ?: liveCurrentUser.about
                )
                onUserUpdate(liveCurrentUser)
            }
        }
        while (true) {
            db.collection("chat_users").document(currentUser.mobile).update("lastActive", System.currentTimeMillis())
            delay(30_000)
        }
    }

    val inChat = selectedContact != null || selectedGroup != null

    if (isCompact && inChat) {
        // Full screen chat on mobile
        if (selectedContact != null) {
            ChatArea(
                currentUser = liveCurrentUser,
                contact = selectedContact!!,
                onBack = { selectedContact = null },
                onCallClick = { type ->
                    callType = type
                    callContact = selectedContact
                    showCallUI = true
                }
            )
        } else if (selectedGroup != null) {
            GroupChatArea(
                currentUser = liveCurrentUser,
                group = selectedGroup!!,
                onBack = { selectedGroup = null }
            )
        }
    } else {
        Scaffold(
            containerColor = RasGramTheme.DarkBackground,
            bottomBar = {
                if (isCompact) {
                    BottomNavBar(
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it }
                    )
                }
            }
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Left sidebar on tablet
                if (!isCompact) {
                    Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                        TabLayout(selectedTab = selectedTab, onTabChange = { selectedTab = it })
                        SidebarContent(
                            tab = selectedTab,
                            currentUser = liveCurrentUser,
                            selectedContact = selectedContact,
                            onContactSelect = { selectedContact = it; selectedGroup = null },
                            onGroupSelect = { selectedGroup = it; selectedContact = null },
                            isDarkMode = isDarkMode,
                            onToggleTheme = onToggleTheme,
                            onLogout = onLogout,
                            onUserUpdate = onUserUpdate,
                            onStatusClick = { selectedStatusUser = it }
                        )
                    }
                } else {
                    SidebarContent(
                        tab = selectedTab,
                        currentUser = liveCurrentUser,
                        selectedContact = selectedContact,
                        onContactSelect = { selectedContact = it },
                        onGroupSelect = { selectedGroup = it },
                        isDarkMode = isDarkMode,
                        onToggleTheme = onToggleTheme,
                        onLogout = onLogout,
                        onUserUpdate = onUserUpdate,
                        onStatusClick = { selectedStatusUser = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Chat pane on tablet
                if (!isCompact) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selectedContact != null) {
                            ChatArea(
                                currentUser = liveCurrentUser,
                                contact = selectedContact!!,
                                onBack = { selectedContact = null },
                                onCallClick = { type ->
                                    callType = type
                                    callContact = selectedContact
                                    showCallUI = true
                                }
                            )
                        } else if (selectedGroup != null) {
                            GroupChatArea(
                                currentUser = liveCurrentUser,
                                group = selectedGroup!!,
                                onBack = { selectedGroup = null }
                            )
                        } else {
                            EmptyChatState()
                        }
                    }
                }
            }
        }
    }

    // Incoming call overlay
    if (showCallUI && callContact != null) {
        CallingScreen(
            currentUser = liveCurrentUser,
            contact = callContact!!,
            callType = callType,
            onEndCall = { showCallUI = false }
        )
    }

    // Status Viewer overlay
    if (selectedStatusUser != null && selectedStatusUser!!.isNotEmpty()) {
        StatusViewerScreen(
            currentUserMobile = liveCurrentUser.mobile,
            statuses = selectedStatusUser!!,
            initialIndex = 0,
            onClose = { selectedStatusUser = null }
        )
    }
}

@Composable
fun BottomNavBar(selectedTab: Int, onTabChange: (Int) -> Unit) {
    NavigationBar(containerColor = RasGramTheme.DarkPanel, tonalElevation = 0.dp) {
        val tabs = listOf(
            Triple(Icons.Default.Message, Icons.Default.Message, "Chats"),
            Triple(Icons.Default.RadioButtonChecked, Icons.Default.RadioButtonUnchecked, "Status"),
            Triple(Icons.Default.Call, Icons.Default.Call, "Calls"),
            Triple(Icons.Default.People, Icons.Default.People, "Groups")
        )
        tabs.forEachIndexed { i, (filledIcon, outlinedIcon, label) ->
            NavigationBarItem(
                icon = { Icon(if (selectedTab == i) filledIcon else outlinedIcon, label) },
                label = { Text(label, fontSize = 11.sp) },
                selected = selectedTab == i,
                onClick = { onTabChange(i) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RasGramTheme.Green,
                    selectedTextColor = RasGramTheme.Green,
                    unselectedIconColor = RasGramTheme.TextMuted,
                    unselectedTextColor = RasGramTheme.TextMuted,
                    indicatorColor = RasGramTheme.Green.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
fun TabLayout(selectedTab: Int, onTabChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RasGramTheme.DarkPanel).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf("Chats", "Status", "Calls")
        tabs.forEachIndexed { i, label ->
            Column(
                modifier = Modifier.weight(1f).clickable { onTabChange(i) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (selectedTab == i) RasGramTheme.Green else RasGramTheme.TextMuted,
                    fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
                if (selectedTab == i) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(modifier = Modifier.width(40.dp).height(2.dp).background(RasGramTheme.Green, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
fun SidebarContent(
    tab: Int,
    currentUser: User,
    selectedContact: User?,
    onContactSelect: (User) -> Unit,
    onGroupSelect: (Group) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdate: (User) -> Unit,
    onStatusClick: (List<Status>) -> Unit,
    modifier: Modifier = Modifier
) {
    when (tab) {
        0 -> ChatsTab(
            currentUser = currentUser,
            selectedContact = selectedContact,
            onContactSelect = onContactSelect,
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            onLogout = onLogout,
            onUserUpdate = onUserUpdate,
            modifier = modifier
        )
        1 -> StatusTab(currentUser = currentUser, onStatusClick = onStatusClick, modifier = modifier)
        2 -> CallsTab(currentUser = currentUser, modifier = modifier)
        3 -> GroupsTab(currentUser = currentUser, onGroupSelect = onGroupSelect, modifier = modifier)
    }
}

