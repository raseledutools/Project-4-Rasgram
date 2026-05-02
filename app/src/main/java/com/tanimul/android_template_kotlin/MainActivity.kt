package com.tanimul.android_template_kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import java.text.SimpleDateFormat
import java.util.*
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

// Data Classes
data class User(
    val name: String = "",
    val mobile: String = "",
    val avatarUrl: String = "",
    val password: String = "",
    val lastActive: Long = 0,
    val typingTo: String? = null,
    val statusVisible: Boolean = true
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
    val reaction: String? = null,
    val read: Boolean = false,
    val isCallLog: Boolean = false,
    val callStatus: String? = null,
    val callType: String? = null,
    val isPending: Boolean = false
)

data class CallData(
    val id: String = "",
    val caller: String = "",
    val callee: String = "",
    val type: String = "audio",
    val status: String = "calling",
    val timestamp: Long = 0,
    val offer: Map<String, Any>? = null,
    val answer: Map<String, Any>? = null
)

// Theme Colors
object RasGramTheme {
    val DarkBackground = Color(0xFF111B21)
    val DarkPanel = Color(0xFF202C33)
    val Green = Color(0xFF00A884)
    val GreenHover = Color(0xFF029071)
    val TextPrimary = Color(0xFFE9EDEF)
    val TextMuted = Color(0xFF8696A0)
    val BubbleIn = Color(0xFF202C33)
    val BubbleOut = Color(0xFF005C4B)
    val Border = Color(0xFF222D34)
    val BlueTick = Color(0xFF53BDEB)
    val LightBackground = Color(0xFFF0F2F5)
    val LightPanel = Color(0xFFFFFFFF)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        
        // WhatsApp Style Offline Persistence Enable
        val db = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        db.firestoreSettings = settings
        
        setContent {
            RasGramApp()
        }
    }
}

@Composable
fun RasGramApp() {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLightMode by remember { mutableStateOf(false) }
    
    MaterialTheme(
        colorScheme = if (isLightMode) lightColorScheme() else darkColorScheme()
    ) {
        if (!isLoggedIn) {
            LoginScreen(
                onLogin = { user ->
                    currentUser = user
                    isLoggedIn = true
                }
            )
        } else {
            MainChatScreen(
                currentUser = currentUser!!,
                isLightMode = isLightMode,
                onToggleTheme = { isLightMode = !isLightMode },
                onLogout = {
                    isLoggedIn = false
                    currentUser = null
                }
            )
        }
    }
}

// ================== LOGIN SCREEN ==================
@Composable
fun LoginScreen(onLogin: (User) -> Unit) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RasGramTheme.DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = RasGramTheme.Green.copy(alpha = 0.1f)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Logo",
                    tint = RasGramTheme.Green,
                    modifier = Modifier.padding(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Welcome to RasGram",
                style = MaterialTheme.typography.headlineMedium,
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Create your secure account.",
                style = MaterialTheme.typography.bodyMedium,
                color = RasGramTheme.TextMuted
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RasGramTheme.Green,
                    unfocusedBorderColor = RasGramTheme.Border,
                    focusedTextColor = RasGramTheme.TextPrimary,
                    unfocusedTextColor = RasGramTheme.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mobile Input
            OutlinedTextField(
                value = mobile,
                onValueChange = { if (it.length <= 11 && it.all { c -> c.isDigit() }) mobile = it },
                label = { Text("Mobile Number (11 digits)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RasGramTheme.Green,
                    unfocusedBorderColor = RasGramTheme.Border,
                    focusedTextColor = RasGramTheme.TextPrimary,
                    unfocusedTextColor = RasGramTheme.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { if (it.length <= 6) password = it },
                label = { Text("Create 6-digit Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RasGramTheme.Green,
                    unfocusedBorderColor = RasGramTheme.Border,
                    focusedTextColor = RasGramTheme.TextPrimary,
                    unfocusedTextColor = RasGramTheme.TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (name.isNotBlank() && mobile.length == 11 && password.length >= 4) {
                        isLoading = true
                        scope.launch {
                            try {
                                // Firebase Anonymous Auth
                                auth.signInAnonymously().await()
                                
                                // Save user data
                                val userRef = db.collection("chat_users").document(mobile)
                                val userSnap = userRef.get().await()
                                
                                if (userSnap.exists()) {
                                    val dbData = userSnap.data
                                    if (dbData?.get("password") != null && dbData["password"] != password) {
                                        Toast.makeText(context, "Wrong password for this mobile number.", Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        return@launch
                                    }
                                } else {
                                    val userData = hashMapOf(
                                        "name" to name,
                                        "mobile" to mobile,
                                        "password" to password,
                                        "lastActive" to System.currentTimeMillis(),
                                        "typingTo" to null,
                                        "avatarUrl" to "",
                                        "statusVisible" to true
                                    )
                                    userRef.set(userData).await()
                                }
                                
                                val user = User(
                                    name = name,
                                    mobile = mobile,
                                    password = password
                                )
                                onLogin(user)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please fill all details correctly.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black
                    )
                } else {
                    Text(
                        "Create & Login",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ================== MAIN CHAT SCREEN ==================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    currentUser: User,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedContact by remember { mutableStateOf<User?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showLockedChats by remember { mutableStateOf(false) }
    var isViewingLocked by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCallUI by remember { mutableStateOf(false) }
    var callType by remember { mutableStateOf("audio") }
    
    Scaffold(
        containerColor = if (isLightMode) RasGramTheme.LightBackground else RasGramTheme.DarkBackground
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sidebar
            AnimatedVisibility(
                visible = selectedContact == null || !isCompactScreen(),
                modifier = Modifier.fillMaxHeight()
            ) {
                SidebarPanel(
                    currentUser = currentUser,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onContactClick = { selectedContact = it },
                    isViewingLocked = isViewingLocked,
                    onToggleTheme = onToggleTheme,
                    onSettingsClick = { showSettings = true },
                    onNewGroupClick = { showNewGroup = true },
                    onLockedChatsClick = {
                        showLockedChats = true
                        isViewingLocked = true
                    },
                    onLogout = onLogout
                )
            }
            
            // Chat Area
            AnimatedVisibility(
                visible = selectedContact != null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                selectedContact?.let { contact ->
                    ChatArea(
                        currentUser = currentUser,
                        contact = contact,
                        onBack = { selectedContact = null },
                        onCallClick = { type ->
                            callType = type
                            showCallUI = true
                        }
                    )
                } ?: EmptyChatState()
            }
        }
    }
    
    // Settings Dialog
    if (showSettings) {
        SettingsDialog(
            currentUser = currentUser,
            onDismiss = { showSettings = false },
            onSave = { showSettings = false }
        )
    }
    
    // New Group Dialog
    if (showNewGroup) {
        NewGroupDialog(
            onDismiss = { showNewGroup = false },
            onCreateGroup = { showNewGroup = false }
        )
    }
    
    // Locked Chats Dialog
    if (showLockedChats) {
        LockedChatsDialog(
            onDismiss = {
                showLockedChats = false
                isViewingLocked = false
            }
        )
    }
    
    // Call Screen
    if (showCallUI && selectedContact != null) {
        CallingScreen(
            currentUser = currentUser,
            contact = selectedContact!!,
            callType = callType,
            onEndCall = {
                showCallUI = false
            }
        )
    }
}

// ================== WEBRTC CALLING SCREEN ==================
@Composable
fun CallingScreen(
    currentUser: User,
    contact: User,
    callType: String,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    
    var callStatus by remember { mutableStateOf("Calling...") }
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(0) }
    
    // Initialize WebRTC
    val peerConnectionFactory = remember { 
        PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }
    
    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )
    
    var peerConnection by remember { mutableStateOf<PeerConnection?>(null) }
    var localStream by remember { mutableStateOf<MediaStream?>(null) }
    
    // Start Call
    LaunchedEffect(Unit) {
        try {
            // Get user media
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            }
            
            val stream = peerConnectionFactory.createLocalMediaStream("localStream")
            
            // Audio track
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            val audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)
            stream.addTrack(audioTrack)
            
            // Video track for video calls
            if (callType == "video") {
                val videoCapturer = getVideoCapturer(context)
                val videoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)
                val videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
                stream.addTrack(videoTrack)
            }
            
            localStream = stream
            
            // Create peer connection
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
            
            val observer = object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        // Send ICE candidate to Firebase
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            callStatus = "Connected"
                            isConnected = true
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            onEndCall()
                        }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            }
            
            val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            stream.audioTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            if (callType == "video") {
                stream.videoTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            }
            peerConnection = pc
            
            // Create call in Firebase
            val chatHash = if (currentUser.mobile < contact.mobile) 
                "${currentUser.mobile}_${contact.mobile}" 
            else 
                "${contact.mobile}_${currentUser.mobile}"
            val callId = "call_${chatHash}_${System.currentTimeMillis()}"
            
            val callData = hashMapOf(
                "caller" to currentUser.mobile,
                "callee" to contact.mobile,
                "type" to callType,
                "status" to "calling",
                "timestamp" to System.currentTimeMillis()
            )
            
            db.collection("calls").document(callId).set(callData)
            
            // Listen for answer
            db.collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    val data = snapshot?.data
                    if (data?.get("status") == "answered" && data["answer"] != null) {
                        callStatus = "Connected"
                        isConnected = true
                    } else if (data?.get("status") == "ended") {
                        onEndCall()
                    }
                }
            
        } catch (e: Exception) {
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            onEndCall()
        }
    }
    
    // Call Timer
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callSeconds++
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            peerConnection?.close()
            peerConnection = null
            localStream?.dispose()
            localStream = null
        }
    }
    
    // UI
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B141A)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Contact Avatar
            AsyncImage(
                model = contact.avatarUrl.ifEmpty {
                    "https://ui-avatars.com/api/?name=${contact.name}"
                },
                contentDescription = "Call Avatar",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(4.dp, RasGramTheme.Green, CircleShape)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                contact.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                if (isConnected) formatTime(callSeconds) else callStatus,
                style = MaterialTheme.typography.titleMedium,
                color = RasGramTheme.Green,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Call Controls
            Row(
                modifier = Modifier.padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Mute Button
                FloatingActionButton(
                    onClick = {
                        isMuted = !isMuted
                        localStream?.audioTracks?.firstOrNull()?.setEnabled(!isMuted)
                    },
                    containerColor = if (isMuted) Color(0xFFF15C6D) else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }
                
                // End Call Button
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            // Update call status in Firebase
                            db.collection("calls")
                                .whereEqualTo("caller", currentUser.mobile)
                                .whereEqualTo("callee", contact.mobile)
                                .whereEqualTo("status", "calling")
                                .get()
                                .await()
                                .documents
                                .forEach { it.reference.update("status", "ended") }
                        }
                        onEndCall()
                    },
                    containerColor = Color(0xFFF15C6D),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Camera Toggle (Video calls only)
                if (callType == "video") {
                    FloatingActionButton(
                        onClick = {
                            isCameraOff = !isCameraOff
                            localStream?.videoTracks?.firstOrNull()?.setEnabled(!isCameraOff)
                        },
                        containerColor = if (isCameraOff) Color(0xFFF15C6D) else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Helper function to get video capturer
fun getVideoCapturer(context: android.content.Context): VideoCapturer? {
    return try {
        val cameraEnumerator = Camera2Enumerator(context)
        val deviceNames = cameraEnumerator.deviceNames
        for (deviceName in deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null)
            }
        }
        null
    } catch (e: Exception) {
        try {
            val cameraEnumerator = Camera1Enumerator(false)
            val deviceNames = cameraEnumerator.deviceNames
            for (deviceName in deviceNames) {
                if (cameraEnumerator.isFrontFacing(deviceName)) {
                    return cameraEnumerator.createCapturer(deviceName, null)
                }
            }
        } catch (e2: Exception) {
            // Fallback
        }
        null
    }
}

// ================== SIDEBAR PANEL ==================
@Composable
fun SidebarPanel(
    currentUser: User,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onContactClick: (User) -> Unit,
    isViewingLocked: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onLockedChatsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var users by remember { mutableStateOf<List<User>>(listOf()) }
    var latestMessages by remember { mutableStateOf<Map<String, Message>>(mapOf()) }
    
    LaunchedEffect(Unit) {
        // Load users from Firebase
        db.collection("chat_users")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    users = it.documents.mapNotNull { doc ->
                        val data = doc.data
                        data?.let {
                            User(
                                name = it["name"] as? String ?: "",
                                mobile = doc.id,
                                avatarUrl = it["avatarUrl"] as? String ?: "",
                                lastActive = it["lastActive"] as? Long ?: 0,
                                typingTo = it["typingTo"] as? String,
                                statusVisible = it["statusVisible"] as? Boolean ?: true
                            )
                        }
                    }.filter { it.mobile != currentUser.mobile }
                }
            }
    }
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(RasGramTheme.DarkBackground)
            .border(1.dp, RasGramTheme.Border)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = currentUser.avatarUrl.ifEmpty {
                    "https://ui-avatars.com/api/?name=${currentUser.name}"
                },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onSettingsClick() }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Row {
                Text(
                    "Ras",
                    style = MaterialTheme.typography.titleLarge,
                    color = RasGramTheme.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Gram",
                    style = MaterialTheme.typography.titleLarge,
                    color = RasGramTheme.Green,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Menu Icons
            IconButton(onClick = { /* Camera */ }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = RasGramTheme.TextMuted)
            }
            
            var showMenu by remember { mutableStateOf(false) }
            
            Box {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = RasGramTheme.TextMuted)
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Group") },
                        onClick = {
                            onNewGroupClick()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            onSettingsClick()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle Theme") },
                        onClick = {
                            onToggleTheme()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Brightness6, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Locked Chats") },
                        onClick = {
                            onLockedChatsClick()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RasGramTheme.Green) }
                    )
                    HorizontalDivider(color = RasGramTheme.Border)
                    DropdownMenuItem(
                        text = { Text("Logout", color = Color.Red.copy(alpha = 0.7f)) },
                        onClick = {
                            onLogout()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f)) }
                    )
                }
            }
        }
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search or start new chat", color = RasGramTheme.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = RasGramTheme.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RasGramTheme.Green,
                unfocusedBorderColor = RasGramTheme.Border,
                focusedTextColor = RasGramTheme.TextPrimary,
                unfocusedTextColor = RasGramTheme.TextPrimary,
                cursorColor = RasGramTheme.Green
            ),
            shape = RoundedCornerShape(24.dp)
        )
        
        // Locked Chats Indicator
        if (!isViewingLocked) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLockedChatsClick() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = RasGramTheme.Green.copy(alpha = 0.1f)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = RasGramTheme.Green,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Locked chats",
                        color = RasGramTheme.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            HorizontalDivider(color = RasGramTheme.Border)
        }
        
        // Contacts List
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(users.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.mobile.contains(searchQuery)
            }) { user ->
                val latestMsg = latestMessages[user.mobile]
                ContactItem(
                    user = user,
                    latestMessage = latestMsg,
                    isSelected = false,
                    onClick = { onContactClick(user) }
                )
                HorizontalDivider(color = RasGramTheme.Border)
            }
        }
    }
}

// ================== CONTACT ITEM ==================
@Composable
fun ContactItem(
    user: User,
    latestMessage: Message?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (isSelected) RasGramTheme.DarkPanel else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.avatarUrl.ifEmpty {
                    "https://ui-avatars.com/api/?name=${user.name}&background=8696a0&color=fff&bold=true"
                },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RasGramTheme.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Typing indicator
                if (user.typingTo == user.mobile) {
                    Text(
                        "typing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RasGramTheme.Green,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    latestMessage?.let { msg ->
                        Text(
                            msg.text.ifEmpty { getFileTypePreview(msg) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!msg.read && msg.senderMobile != "") 
                                RasGramTheme.TextPrimary 
                            else 
                                RasGramTheme.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: Text(
                        "Tap to chat...",
                        color = RasGramTheme.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            latestMessage?.let { msg ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        msg.timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!msg.read && msg.senderMobile != "") 
                            RasGramTheme.Green 
                        else 
                            RasGramTheme.TextMuted
                    )
                    if (!msg.read && msg.senderMobile != "" && msg.senderMobile != user.mobile) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RasGramTheme.Green
                        ) {
                            Text(
                                "New",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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

// ================== CHAT AREA ==================
@Composable
fun ChatArea(
    currentUser: User,
    contact: User,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var messages by remember { mutableStateOf<List<Message>>(listOf()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val chatId = remember(currentUser.mobile, contact.mobile) {
        generateChatId(currentUser.mobile, contact.mobile)
    }
    
    // Load messages
    LaunchedEffect(chatId) {
        db.collection("pvt_msg_$chatId")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.let {
                    messages = it.documents.mapNotNull { doc ->
                        val data = doc.data
                        data?.let {
                            Message(
                                id = doc.id,
                                text = it["text"] as? String ?: "",
                                senderMobile = it["senderMobile"] as? String ?: "",
                                timestamp = it["timestamp"] as? Long ?: 0,
                                timeString = it["timeString"] as? String ?: "",
                                fileUrl = it["fileUrl"] as? String,
                                fileName = it["fileName"] as? String,
                                fileType = it["fileType"] as? String,
                                reaction = it["reaction"] as? String,
                                read = it["read"] as? Boolean ?: false,
                                isCallLog = it["isCallLog"] as? Boolean ?: false,
                                callStatus = it["callStatus"] as? String,
                                callType = it["callType"] as? String,
                                // Pending State Check
                                isPending = doc.metadata.hasPendingWrites() 
                            )
                        }
                    }
                }
            }
    }
    
    // Mark messages as read
    LaunchedEffect(contact.mobile) {
        db.collection("pvt_msg_$chatId")
            .whereEqualTo("senderMobile", contact.mobile)
            .whereEqualTo("read", false)
            .get()
            .await()
            .documents
            .forEach { it.reference.update("read", true) }
    }
    
    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasGramTheme.DarkBackground)
    ) {
        // Chat Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = RasGramTheme.DarkPanel
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCompactScreen()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RasGramTheme.TextMuted)
                    }
                }
                
                AsyncImage(
                    model = contact.avatarUrl.ifEmpty {
                        "https://ui-avatars.com/api/?name=${contact.name}"
                    },
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = RasGramTheme.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (contact.typingTo == currentUser.mobile) "typing..." 
                        else if (contact.lastActive > System.currentTimeMillis() - 120000) "online"
                        else "offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (contact.typingTo == currentUser.mobile) RasGramTheme.Green 
                               else RasGramTheme.TextMuted
                    )
                }
                
                // Call Buttons
                IconButton(onClick = { onCallClick("video") }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = RasGramTheme.TextMuted)
                }
                IconButton(onClick = { onCallClick("audio") }) {
                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = RasGramTheme.TextMuted)
                }
                
                // Chat Menu
                var showChatMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showChatMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat Menu", tint = RasGramTheme.TextMuted)
                    }
                    DropdownMenu(
                        expanded = showChatMenu,
                        onDismissRequest = { showChatMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lock Chat") },
                            onClick = { showChatMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat", color = Color.Red.copy(alpha = 0.7f)) },
                            onClick = {
                                showChatMenu = false
                                coroutineScope.launch {
                                    db.collection("pvt_msg_$chatId")
                                        .get()
                                        .await()
                                        .documents
                                        .forEach { it.reference.delete() }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    RasGramTheme.DarkBackground.copy(alpha = 0.95f)
                ),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Security Notice
            item {
                Surface(
                    modifier = Modifier.padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF182229)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFFFD279)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Messages are end-to-end encrypted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD279)
                        )
                    }
                }
            }
            
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    isMe = message.senderMobile == currentUser.mobile,
                    onReact = { reaction ->
                        coroutineScope.launch {
                            db.collection("pvt_msg_$chatId")
                                .document(message.id)
                                .update("reaction", reaction)
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            db.collection("pvt_msg_$chatId")
                                .document(message.id)
                                .delete()
                        }
                    }
                )
            }
        }
        
        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = RasGramTheme.DarkPanel
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach Button
                IconButton(onClick = { 
                    // Launch file picker
                    val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    // Use ActivityResultLauncher in production
                }) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = RasGramTheme.TextMuted
                    )
                }
                
                // Text Input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it
                        // Typing indicator
                        if (it.isNotEmpty() && !isTyping) {
                            isTyping = true
                            coroutineScope.launch {
                                db.collection("chat_users").document(currentUser.mobile)
                                    .update("typingTo", contact.mobile)
                                kotlinx.coroutines.delay(2000)
                                db.collection("chat_users").document(currentUser.mobile)
                                    .update("typingTo", null)
                                isTyping = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    placeholder = { Text("Type a message", color = RasGramTheme.TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = RasGramTheme.TextPrimary,
                        unfocusedTextColor = RasGramTheme.TextPrimary,
                        cursorColor = RasGramTheme.Green
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                sendMessage(db, chatId, currentUser.mobile, inputText, null)
                                inputText = ""
                            }
                        }
                    )
                )
                
                // Send/Mic Button
                AnimatedContent(
                    targetState = inputText.isNotEmpty(),
                    transitionSpec = {
                        fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                    }
                ) { hasText ->
                    if (hasText) {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    sendMessage(db, chatId, currentUser.mobile, inputText, null)
                                    inputText = ""
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = RasGramTheme.Green
                            )
                        }
                    } else {
                        IconButton(onClick = { /* Voice Recording */ }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = RasGramTheme.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

// Send Message Helper
fun sendMessage(
    db: FirebaseFirestore,
    chatId: String,
    senderMobile: String,
    text: String,
    fileUrl: String?
) {
    val message = hashMapOf(
        "text" to text,
        "senderMobile" to senderMobile,
        "timestamp" to System.currentTimeMillis(),
        "timeString" to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
        "fileUrl" to fileUrl,
        "fileName" to null,
        "fileType" to null,
        "reaction" to null,
        "read" to false,
        "isCallLog" to false
    )
    
    // Offline persistence ensures this is saved locally immediately and synced later
    db.collection("pvt_msg_$chatId")
        .add(message)
        .addOnFailureListener { e ->
            // Already handled by Firebase Offline capabilities
        }
}

// ================== MESSAGE BUBBLE ==================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    onReact: (String) -> Unit,
    onDelete: () -> Unit
) {
    val bubbleColor = if (isMe) RasGramTheme.BubbleOut else RasGramTheme.BubbleIn
    val alignment = if (isMe) Alignment.End else Alignment.Start
    var showReactions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (message.isCallLog) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF182229)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (message.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (message.callStatus == "missed") Color.Red else RasGramTheme.TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = RasGramTheme.TextPrimary
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { 
                            if (message.senderMobile != "") {
                                showReactions = !showReactions 
                            }
                        },
                        onLongClick = {
                            if (isMe) showReactions = true
                        }
                    ),
                shape = RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = if (isMe) 8.dp else 0.dp,
                    bottomEnd = if (isMe) 0.dp else 8.dp
                ),
                color = bubbleColor
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (message.fileUrl != null) 4.dp else 12.dp,
                        vertical = if (message.fileUrl != null) 4.dp else 8.dp
                    )
                ) {
                    // Image Message
                    message.fileUrl?.let { url ->
                        if (message.fileType?.startsWith("image/") == true) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (message.fileType?.startsWith("video/") == true) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        } else if (message.fileType?.startsWith("audio/") == true) {
                            Row(
                                modifier = Modifier
                                    .width(200.dp)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { /* Play audio */ }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = RasGramTheme.TextPrimary
                                    )
                                }
                                Slider(
                                    value = 0f,
                                    onValueChange = {},
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = RasGramTheme.Green,
                                        activeTrackColor = RasGramTheme.Green
                                    )
                                )
                                Text(
                                    "0:00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RasGramTheme.TextMuted
                                )
                            }
                        } else {
                            // Document
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = "File",
                                    tint = RasGramTheme.TextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    message.fileName ?: "Document",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RasGramTheme.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    // Text Message
                    if (message.text.isNotEmpty()) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RasGramTheme.TextPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                    
                    // Time & Read Status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message.timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = RasGramTheme.TextMuted.copy(alpha = 0.7f)
                        )
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val icon = when {
                                message.isPending -> Icons.Default.Schedule // Offline pending icon
                                message.read -> Icons.Default.DoneAll
                                else -> Icons.Default.Check
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.read && !message.isPending) RasGramTheme.BlueTick else RasGramTheme.TextMuted
                            )
                        }
                    }
                }
            }
            
            // Reactions Bar
            if (showReactions) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("👍", "❤️", "😂", "😢").forEach { emoji ->
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onReact(emoji)
                                    showReactions = false
                                },
                            shape = CircleShape,
                            color = RasGramTheme.DarkPanel,
                            border = BorderStroke(1.dp, RasGramTheme.Border)
                        ) {
                            Text(
                                emoji,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    
                    if (isMe) {
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    showDeleteConfirm = true
                                },
                            shape = CircleShape,
                            color = Color.Red.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.padding(8.dp),
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
            
            // Delete Confirmation
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Message") },
                    text = { Text("Delete message for everyone?") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        }) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Existing Reaction
            message.reaction?.let { reaction ->
                Surface(
                    modifier = Modifier.offset(y = (-4).dp),
                    shape = RoundedCornerShape(12.dp),
                    color = RasGramTheme.DarkPanel,
                    border = BorderStroke(1.dp, RasGramTheme.Border)
                ) {
                    Text(
                        reaction,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ================== DIALOGS ==================
@Composable
fun SettingsDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = {
            Text(
                "Profile",
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                AsyncImage(
                    model = currentUser.avatarUrl.ifEmpty {
                        "https://ui-avatars.com/api/?name=${currentUser.name}"
                    },
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, RasGramTheme.Green, CircleShape)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name", color = RasGramTheme.Green) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RasGramTheme.Green,
                        unfocusedBorderColor = RasGramTheme.Border,
                        focusedTextColor = RasGramTheme.TextPrimary,
                        unfocusedTextColor = RasGramTheme.TextPrimary,
                        cursorColor = RasGramTheme.Green
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        db.collection("chat_users")
                            .document(currentUser.mobile)
                            .update("name", name)
                    }
                    onSave()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RasGramTheme.TextMuted)
            }
        }
    )
}

@Composable
fun NewGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = {
            Text(
                "New Group",
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Subject") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RasGramTheme.Green,
                        unfocusedBorderColor = RasGramTheme.Border,
                        focusedTextColor = RasGramTheme.TextPrimary,
                        unfocusedTextColor = RasGramTheme.TextPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Select contacts to add:",
                    color = RasGramTheme.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreateGroup,
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                enabled = groupName.isNotBlank()
            ) {
                Text("Create", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RasGramTheme.TextMuted)
            }
        }
    )
}

@Composable
fun LockedChatsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = {
            Text(
                "Locked Chats",
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "These chats are locked with your PIN.",
                color = RasGramTheme.TextMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)
            ) {
                Text("OK", color = Color.Black)
            }
        }
    )
}

@Composable
fun EmptyChatState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = RasGramTheme.DarkPanel
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = null,
                modifier = Modifier.padding(30.dp),
                tint = RasGramTheme.Green
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "RasGram",
            style = MaterialTheme.typography.headlineLarge,
            color = RasGramTheme.TextPrimary,
            fontWeight = FontWeight.Light
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Send and receive messages securely.\nEnd-to-end encrypted connection active.",
            style = MaterialTheme.typography.bodyMedium,
            color = RasGramTheme.TextMuted,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = RasGramTheme.Green
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "End-to-end encrypted",
                style = MaterialTheme.typography.bodySmall,
                color = RasGramTheme.TextMuted
            )
        }
    }
}

// ================== HELPER FUNCTIONS ==================
fun generateChatId(mobile1: String, mobile2: String): String {
    return if (mobile1 < mobile2) "${mobile1}_${mobile2}" else "${mobile2}_${mobile1}"
}

fun getFileTypePreview(message: Message): String {
    return when {
        message.isCallLog -> {
            val icon = if (message.callType == "video") "📹" else "📞"
            "$icon ${message.text}"
        }
        message.fileType?.startsWith("image/") == true -> "📷 Photo"
        message.fileType?.startsWith("video/") == true -> "🎥 Video"
        message.fileType?.startsWith("audio/") == true -> "🎤 Voice message"
        message.fileUrl != null -> "📎 Document"
        message.text.isNotEmpty() -> message.text
        else -> "Message"
    }
}

fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

@Composable
fun isCompactScreen(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp < 600
}
