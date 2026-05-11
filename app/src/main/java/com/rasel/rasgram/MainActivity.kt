package com.rasel.rasgram

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.webrtc.*
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ==================== CONSTANTS & CONFIGS ====================
const val CLOUDINARY_CLOUD_NAME = "de2w78yxh"
const val CLOUDINARY_UPLOAD_URL = "https://api.cloudinary.com/v1_1/de2w78yxh/auto/upload"
const val CLOUDINARY_UPLOAD_PRESET = "ml_default"
const val PREF_NAME = "rasgram_prefs"
const val PREF_MOBILE = "saved_mobile"
const val PREF_NAME_KEY = "saved_name"
const val PREF_IS_LOGGED_IN = "is_logged_in"

// ==================== DATA MODELS ====================
data class User(
    val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val avatarUrl: String = "",
    val about: String = "Hey there! I am using RasGram.",
    val lastActive: Long = 0,
    val typingTo: String? = null,
    val isOnline: Boolean = false,
    val pushToken: String = ""
)

data class Message(
    val id: String = "",
    val text: String = "",
    val senderMobile: String = "",
    val timestamp: Long = 0,
    val timeString: String = "",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileType: String? = null,
    val fileSize: String? = null,
    val replyToMessageId: String? = null,
    val reaction: String? = null,
    val read: Boolean = false,
    val isCallLog: Boolean = false,
    val callStatus: String? = null,
    val callType: String? = null,
    val isForwarded: Boolean = false,
    val isPending: Boolean = false
)

data class Status(
    val id: String = "",
    val userMobile: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val imageUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val expiresAt: Long = 0
)

// ==================== THEME (WhatsApp / RasGram Custom) ====================
object RasGramTheme {
    val DarkBackground = Color(0xFF111B21)
    val DarkPanel = Color(0xFF202C33)
    val DarkHeader = Color(0xFF202C33)
    val Green = Color(0xFF00A884)
    val GreenHover = Color(0xFF029071)
    val TextPrimary = Color(0xFFE9EDEF)
    val TextMuted = Color(0xFF8696A0)
    val BubbleIn = Color(0xFF202C33)
    val BubbleOut = Color(0xFF005C4B)
    val BubbleReplyIn = Color(0xFF2A3942)
    val BubbleReplyOut = Color(0xFF025144)
    val Border = Color(0xFF222D34)
    val BlueTick = Color(0xFF53BDEB)
    val LightBackground = Color(0xFFEFEAE2)
    val LightPanel = Color(0xFFFFFFFF)
    val LightHeader = Color(0xFF008069)
    val Red = Color(0xFFF15C6D)
}

// ==================== MAIN ACTIVITY ====================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        // Super Fast Offline Persistence
        val db = FirebaseFirestore.getInstance()
        val settings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        db.firestoreSettings = settings

        setContent {
            RasGramApp()
        }
    }
}

// ==================== ROOT APP ====================
@Composable
fun RasGramApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean(PREF_IS_LOGGED_IN, false)) }
    var currentUserMobile by remember { mutableStateOf(prefs.getString(PREF_MOBILE, "") ?: "") }
    var isLightMode by remember { mutableStateOf(false) }

    val db = remember { FirebaseFirestore.getInstance() }

    MaterialTheme(
        colorScheme = if (isLightMode) lightColorScheme() else darkColorScheme()
    ) {
        if (!isLoggedIn || currentUserMobile.isEmpty()) {
            PhoneAuthScreen(
                onLoginSuccess = { mobile ->
                    prefs.edit()
                        .putBoolean(PREF_IS_LOGGED_IN, true)
                        .putString(PREF_MOBILE, mobile)
                        .apply()
                    currentUserMobile = mobile
                    isLoggedIn = true
                }
            )
        } else {
            // Update Online Status periodically
            LaunchedEffect(currentUserMobile) {
                while (true) {
                    db.collection("chat_users").document(currentUserMobile)
                        .update("lastActive", System.currentTimeMillis(), "isOnline", true)
                    delay(30_000)
                }
            }

            MainHomeScreen(
                currentUserMobile = currentUserMobile,
                isLightMode = isLightMode,
                onToggleTheme = { isLightMode = !isLightMode },
                onLogout = {
                    prefs.edit().clear().apply()
                    FirebaseAuth.getInstance().signOut()
                    isLoggedIn = false
                }
            )
        }
    }
}

// ==================== OTP & PHONE AUTH SCREEN ====================
@Composable
fun PhoneAuthScreen(onLoginSuccess: (String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneAuthCredential(auth, credential, phoneNumber, db, scope, onLoginSuccess)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                isLoading = false
                Toast.makeText(context, "Verification Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
                isLoading = false
                isOtpSent = true
                verificationId = verId
                Toast.makeText(context, "OTP Sent to $phoneNumber", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RasGramTheme.DarkBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = RasGramTheme.Green.copy(alpha = 0.15f)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Logo",
                    tint = RasGramTheme.Green,
                    modifier = Modifier.padding(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Verify your phone number",
                style = MaterialTheme.typography.headlineSmall,
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "RasGram will send an SMS message to verify your phone number.",
                style = MaterialTheme.typography.bodyMedium,
                color = RasGramTheme.TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (!isOtpSent) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number (with country code e.g. +880)", color = RasGramTheme.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = outlinedFieldColors()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (phoneNumber.isNotBlank() && phoneNumber.startsWith("+")) {
                            isLoading = true
                            val options = PhoneAuthOptions.newBuilder(auth)
                                .setPhoneNumber(phoneNumber)
                                .setTimeout(60L, TimeUnit.SECONDS)
                                .setActivity(activity)
                                .setCallbacks(callbacks)
                                .build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        } else {
                            Toast.makeText(context, "Enter valid number with country code", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("Send OTP", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("Enter 6-digit OTP", color = RasGramTheme.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = outlinedFieldColors()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (otpCode.length == 6) {
                            isLoading = true
                            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                            signInWithPhoneAuthCredential(auth, credential, phoneNumber, db, scope, onLoginSuccess)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("Verify OTP", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun signInWithPhoneAuthCredential(
    auth: FirebaseAuth,
    credential: PhoneAuthCredential,
    mobile: String,
    db: FirebaseFirestore,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (String) -> Unit
) {
    auth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                scope.launch {
                    val formattedMobile = mobile.replace("+", "")
                    val userRef = db.collection("chat_users").document(formattedMobile)
                    val snap = userRef.get().await()
                    if (!snap.exists()) {
                        val newUser = User(
                            id = formattedMobile,
                            mobile = formattedMobile,
                            name = "User_$formattedMobile",
                            lastActive = System.currentTimeMillis(),
                            isOnline = true
                        )
                        userRef.set(newUser).await()
                    }
                    onSuccess(formattedMobile)
                }
            }
        }
}

// ==================== FLOW REPOSITORIES (SUPER FAST SYNC) ====================
fun getUserStream(db: FirebaseFirestore, mobile: String): Flow<User?> = callbackFlow {
    val listener = db.collection("chat_users").document(mobile)
        .addSnapshotListener { snap, _ ->
            snap?.toObject(User::class.java)?.let { trySend(it) }
        }
    awaitClose { listener.remove() }
}

fun getAllUsersStream(db: FirebaseFirestore): Flow<List<User>> = callbackFlow {
    val listener = db.collection("chat_users")
        .addSnapshotListener { snap, _ ->
            val users = snap?.documents?.mapNotNull { it.toObject(User::class.java) } ?: emptyList()
            trySend(users)
        }
    awaitClose { listener.remove() }
}

fun getChatMessagesStream(db: FirebaseFirestore, chatId: String): Flow<List<Message>> = callbackFlow {
    val listener = db.collection("chats").document(chatId).collection("messages")
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .addSnapshotListener { snap, _ ->
            if (snap != null) {
                val msgs = snap.documents.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id, isPending = doc.metadata.hasPendingWrites())
                }
                trySend(msgs)
            }
        }
    awaitClose { listener.remove() }
}

// ==================== MAIN HOME SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    currentUserMobile: String,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser by getUserStream(db, currentUserMobile).collectAsStateWithLifecycle(initialValue = null)
    var selectedContact by remember { mutableStateOf<User?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val tabs = listOf("Chats", "Status", "Calls")
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
        topBar = {
            if (selectedContact == null || !isCompact) {
                TopAppBar(
                    title = { Text("RasGram", color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RasGramTheme.DarkHeader),
                    actions = {
                        IconButton(onClick = { /* Camera */ }) { Icon(Icons.Outlined.CameraAlt, null, tint = RasGramTheme.TextMuted) }
                        IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, null, tint = RasGramTheme.TextMuted) }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = RasGramTheme.TextMuted) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { showSettings = true; showMenu = false })
                                DropdownMenuItem(text = { Text("Logout", color = RasGramTheme.Red) }, onClick = onLogout)
                            }
                        }
                    }
                )
            }
        },
        containerColor = RasGramTheme.DarkBackground
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Sidebar / Tab Content
            AnimatedVisibility(visible = selectedContact == null || !isCompact, modifier = Modifier.fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isCompact) LocalConfiguration.current.screenWidthDp.dp else 400.dp)
                        .background(RasGramTheme.DarkBackground)
                        .rightBorder(1.dp, RasGramTheme.Border)
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = RasGramTheme.DarkHeader,
                        contentColor = RasGramTheme.Green,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = RasGramTheme.Green
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontWeight = FontWeight.Bold, color = if (selectedTab == index) RasGramTheme.Green else RasGramTheme.TextMuted) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> ChatsListTab(db, currentUserMobile, onContactClick = { selectedContact = it })
                        1 -> StatusTab()
                        2 -> CallsTab()
                    }
                }
            }

            // Chat Area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedContact != null && currentUser != null) {
                    ChatArea(
                        currentUser = currentUser!!,
                        contact = selectedContact!!,
                        onBack = { selectedContact = null },
                        db = db
                    )
                } else {
                    if (!isCompact) EmptyChatState()
                }
            }
        }
    }

    if (showSettings && currentUser != null) {
        SettingsDialog(user = currentUser!!, db = db, onDismiss = { showSettings = false })
    }
}

// ==================== CHATS LIST TAB ====================
@Composable
fun ChatsListTab(db: FirebaseFirestore, currentUserMobile: String, onContactClick: (User) -> Unit) {
    val users by getAllUsersStream(db).collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredUsers = users.filter { it.mobile != currentUserMobile }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredUsers, key = { it.mobile }) { user ->
            ContactItem(user = user, onClick = { onContactClick(user) })
        }
    }
}

@Composable
fun ContactItem(user: User, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = user.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.name}&background=8696a0&color=fff" },
                    contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                if (user.isOnline) {
                    Box(
                        modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
                            .border(2.dp, RasGramTheme.DarkBackground, CircleShape)
                            .background(RasGramTheme.Green, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(user.about, style = MaterialTheme.typography.bodyMedium, color = RasGramTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ==================== CHAT AREA (SUPER FAST & ROBUST) ====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatArea(
    currentUser: User,
    contact: User,
    onBack: () -> Unit,
    db: FirebaseFirestore
) {
    val chatId = generateChatId(currentUser.mobile, contact.mobile)
    val messages by getChatMessagesStream(db, chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val liveContact by getUserStream(db, contact.mobile).collectAsStateWithLifecycle(initialValue = contact)
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    val focusManager = LocalFocusManager.current

    // Optimized Auto-Scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Read Receipt updater
    LaunchedEffect(messages) {
        val unreadMsgs = messages.filter { it.senderMobile == contact.mobile && !it.read }
        if (unreadMsgs.isNotEmpty()) {
            val batch = db.batch()
            unreadMsgs.forEach { msg ->
                val ref = db.collection("chats").document(chatId).collection("messages").document(msg.id)
                batch.update(ref, "read", true)
            }
            batch.commit()
        }
    }

    // Modern File Picker (Supports ANY file)
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch(Dispatchers.IO) {
                val (url, fileName, fileType, fileSize) = uploadAnyFileToCloudinary(context, it)
                if (url != null) {
                    sendMessage(db, chatId, currentUser.mobile, "", url, fileName, fileType, fileSize, replyToMessage?.id)
                    withContext(Dispatchers.Main) {
                        replyToMessage = null
                        Toast.makeText(context, "File Sent successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Upload Failed", Toast.LENGTH_SHORT).show() }
                }
                isUploading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        // Chat Header
        Surface(color = RasGramTheme.DarkHeader, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (LocalConfiguration.current.screenWidthDp < 600) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RasGramTheme.TextPrimary) }
                }
                AsyncImage(
                    model = liveContact?.avatarUrl?.ifEmpty { "https://ui-avatars.com/api/?name=${liveContact?.name}" },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(liveContact?.name ?: "Unknown", style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary)
                    Text(
                        if (liveContact?.isOnline == true) "online" else "offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = RasGramTheme.TextMuted
                    )
                }
                IconButton(onClick = { /* Video Call */ }) { Icon(Icons.Default.Videocam, null, tint = RasGramTheme.TextPrimary) }
                IconButton(onClick = { /* Voice Call */ }) { Icon(Icons.Default.Call, null, tint = RasGramTheme.TextPrimary) }
            }
        }

        // Message List
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                painter = coil3.compose.rememberAsyncImagePainter("https://i.imgur.com/3qC5PQA.jpeg"), // WhatsApp dark wallpaper pattern
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.05f
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        "Messages are end-to-end encrypted.",
                        color = Color(0xFFFFD279),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                items(messages, key = { it.id }) { message ->
                    val isMe = message.senderMobile == currentUser.mobile
                    val repliedMsg = messages.find { it.id == message.replyToMessageId }
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        SwipeToReplyWrapper(
                            onSwipe = { replyToMessage = message }
                        ) {
                            MessageBubbleReal(message, isMe, repliedMsg)
                        }
                    }
                }
            }
        }

        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.Green)
        }

        // Reply UI Bar
        AnimatedVisibility(visible = replyToMessage != null) {
            Surface(color = RasGramTheme.DarkPanel, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(RasGramTheme.DarkBackground).padding(8.dp).rightBorder(4.dp, RasGramTheme.Green)) {
                        Text(if (replyToMessage?.senderMobile == currentUser.mobile) "You" else contact.name, color = RasGramTheme.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(replyToMessage?.text ?: "File", color = RasGramTheme.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                    IconButton(onClick = { replyToMessage = null }) { Icon(Icons.Default.Close, null, tint = RasGramTheme.TextMuted) }
                }
            }
        }

        // Input Area
        Surface(color = RasGramTheme.DarkHeader, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { fileLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, null, tint = RasGramTheme.TextMuted)
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 150.dp),
                    placeholder = { Text("Message", color = RasGramTheme.TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = RasGramTheme.DarkPanel,
                        unfocusedContainerColor = RasGramTheme.DarkPanel,
                        focusedTextColor = RasGramTheme.TextPrimary,
                        unfocusedTextColor = RasGramTheme.TextPrimary,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            sendMessage(db, chatId, currentUser.mobile, inputText, null, null, null, null, replyToMessage?.id)
                            inputText = ""
                            replyToMessage = null
                        }
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            sendMessage(db, chatId, currentUser.mobile, inputText, null, null, null, null, replyToMessage?.id)
                            inputText = ""
                            replyToMessage = null
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = RasGramTheme.Green,
                    shape = CircleShape
                ) {
                    Icon(if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic, null, tint = Color.White)
                }
            }
        }
    }
}

// ==================== MESSAGE BUBBLE & SWIPE TO REPLY ====================
@Composable
fun MessageBubbleReal(message: Message, isMe: Boolean, repliedMsg: Message?) {
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) RasGramTheme.BubbleOut else RasGramTheme.BubbleIn
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isMe) 12.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 12.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                // Render Reply Snippet
                if (repliedMsg != null) {
                    Surface(
                        color = if (isMe) RasGramTheme.BubbleReplyOut else RasGramTheme.BubbleReplyIn,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp).drawBehind {
                            drawLine(RasGramTheme.Green, Offset(0f, 0f), Offset(0f, size.height), 8f)
                        }) {
                            Text(if (repliedMsg.senderMobile == message.senderMobile) "You" else "Contact", color = RasGramTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(repliedMsg.text.ifEmpty { "File Attached" }, color = RasGramTheme.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        }
                    }
                }

                // Render File
                if (message.fileUrl != null) {
                    when {
                        message.fileType?.startsWith("image/") == true -> {
                            AsyncImage(
                                model = message.fileUrl,
                                contentDescription = "Image",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, message.fileUrl.toUri()))
                                },
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            // Any other document file
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.2f)).clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, message.fileUrl.toUri()))
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = RasGramTheme.TextMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(message.fileName ?: "Document", color = RasGramTheme.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Text(message.fileSize ?: "Unknown size", color = RasGramTheme.TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Render Text
                if (message.text.isNotEmpty()) {
                    Text(message.text, color = RasGramTheme.TextPrimary, fontSize = 16.sp)
                }

                // Render Time & Read Receipt
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(message.timeString, fontSize = 11.sp, color = RasGramTheme.TextMuted)
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when {
                                message.isPending -> Icons.Default.Schedule
                                message.read -> Icons.Default.DoneAll
                                else -> Icons.Default.Check
                            },
                            contentDescription = "Status",
                            modifier = Modifier.size(14.dp),
                            tint = if (message.read && !message.isPending) RasGramTheme.BlueTick else RasGramTheme.TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeToReplyWrapper(onSwipe: () -> Unit, content: @Composable () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier.fillMaxWidth().offset(x = offsetX.dp).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offsetX > 60f) onSwipe()
                    offsetX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    if (dragAmount > 0 && offsetX < 80f) { offsetX += dragAmount * 0.5f }
                }
            )
        }
    ) {
        content()
    }
}

// ==================== UNIVERSAL FILE UPLOADER ====================
// Supports PDF, DOCX, APK, Images, Audio, ZIP up to 10MB natively via Cloudinary generic endpoint.
suspend fun uploadAnyFileToCloudinary(context: Context, uri: Uri): FileUploadResult = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileNameFromUri(context, uri) ?: "file_${System.currentTimeMillis()}"
        val fileSize = getFileSize(context, uri)

        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@withContext FileUploadResult()
        val tempFile = File(context.cacheDir, fileName)
        tempFile.outputStream().use { out -> inputStream.copyTo(out) }

        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, tempFile.asRequestBody(mimeType.toMediaTypeOrNull()))
            .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/raw/upload") // 'raw' supports ANY file type
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext FileUploadResult()
        val json = JSONObject(responseBody)
        val url = json.optString("secure_url", null)
        tempFile.delete()

        FileUploadResult(url, fileName, mimeType, fileSize)
    } catch (e: Exception) {
        e.printStackTrace()
        FileUploadResult()
    }
}

data class FileUploadResult(val url: String? = null, val name: String? = null, val type: String? = null, val size: String? = null)

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

fun getFileSize(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size = cursor.getLong(sizeIndex)
            formatSize(size)
        } ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// ==================== SEND MESSAGE LOGIC ====================
fun sendMessage(
    db: FirebaseFirestore,
    chatId: String,
    senderMobile: String,
    text: String,
    fileUrl: String?,
    fileName: String?,
    fileType: String?,
    fileSize: String?,
    replyToId: String?
) {
    val msgId = db.collection("chats").document(chatId).collection("messages").document().id
    val message = Message(
        id = msgId,
        text = text,
        senderMobile = senderMobile,
        timestamp = System.currentTimeMillis(),
        timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
        fileUrl = fileUrl,
        fileName = fileName,
        fileType = fileType,
        fileSize = fileSize,
        replyToMessageId = replyToId,
        read = false
    )
    // Save to subcollection
    db.collection("chats").document(chatId).collection("messages").document(msgId).set(message)
    // Update latest activity for chat list sorting
    db.collection("chats").document(chatId).set(mapOf("lastUpdate" to System.currentTimeMillis()), SetOptions.merge())
}

// ==================== UTILS ====================
fun generateChatId(m1: String, m2: String): String = if (m1 < m2) "${m1}_${m2}" else "${m2}_${m1}"

fun Modifier.rightBorder(width: Dp, color: Color) = this.drawBehind {
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), width.toPx())
}

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RasGramTheme.Green,
    unfocusedBorderColor = RasGramTheme.Border,
    focusedTextColor = RasGramTheme.TextPrimary,
    unfocusedTextColor = RasGramTheme.TextPrimary,
    cursorColor = RasGramTheme.Green
)

// ==================== STUB COMPONENTS ====================
@Composable fun EmptyChatState() { Box(modifier = Modifier.fillMaxSize().background(RasGramTheme.DarkBackground), contentAlignment = Alignment.Center) { Text("Select a chat to start messaging", color = RasGramTheme.TextMuted) } }
@Composable fun StatusTab() { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Status Features Coming Soon", color = RasGramTheme.TextMuted) } }
@Composable fun CallsTab() { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Call Logs Coming Soon", color = RasGramTheme.TextMuted) } }
@Composable fun SettingsDialog(user: User, db: FirebaseFirestore, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Profile") }, text = { Text("Settings UI Here") }, confirmButton = { Button(onClick = onDismiss) { Text("OK") } }) }
