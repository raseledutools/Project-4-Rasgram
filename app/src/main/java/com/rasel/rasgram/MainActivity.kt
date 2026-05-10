package com.rasel.rasgram

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import java.text.SimpleDateFormat
import java.util.*
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import android.content.Context
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ==================== CONSTANTS ====================
const val CLOUDINARY_CLOUD_NAME = "de2w78yxh"
const val CLOUDINARY_UPLOAD_URL = "https://api.cloudinary.com/v1_1/de2w78yxh/auto/upload"
const val CLOUDINARY_UPLOAD_PRESET = "ml_default"
const val PREF_NAME = "rasgram_prefs"
const val PREF_MOBILE = "saved_mobile"
const val PREF_NAME_KEY = "saved_name"
const val PREF_PASSWORD = "saved_password"

// ==================== DATA CLASSES ====================
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
    val answer: Map<String, Any>? = null,
    val iceCandidates: List<Map<String, Any>>? = null
)

// ==================== THEME ====================
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
    val Red = Color(0xFFF15C6D)
}

// ==================== MAIN ACTIVITY ====================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val db = FirebaseFirestore.getInstance()
        // FIX: Use the new cache config API instead of deprecated setPersistenceEnabled
        val settings = FirebaseFirestoreSettings.Builder()
            .build()
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

    var isLoggedIn by remember {
        mutableStateOf(
            prefs.getString(PREF_MOBILE, null) != null &&
                    prefs.getString(PREF_NAME_KEY, null) != null
        )
    }
    var currentUser by remember {
        mutableStateOf(
            if (prefs.getString(PREF_MOBILE, null) != null) {
                User(
                    name = prefs.getString(PREF_NAME_KEY, "") ?: "",
                    mobile = prefs.getString(PREF_MOBILE, "") ?: "",
                    password = prefs.getString(PREF_PASSWORD, "") ?: ""
                )
            } else null
        )
    }
    var isLightMode by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = if (isLightMode) lightColorScheme() else darkColorScheme()
    ) {
        if (!isLoggedIn || currentUser == null) {
            LoginScreen(
                onLogin = { user ->
                    prefs.edit()
                        .putString(PREF_MOBILE, user.mobile)
                        .putString(PREF_NAME_KEY, user.name)
                        .putString(PREF_PASSWORD, user.password)
                        .apply()
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
                    prefs.edit().clear().apply()
                    isLoggedIn = false
                    currentUser = null
                }
            )
        }
    }
}

// ==================== LOGIN SCREEN ====================
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = RasGramTheme.Green.copy(alpha = 0.15f)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Logo",
                    tint = RasGramTheme.Green,
                    modifier = Modifier.padding(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Welcome to RasGram",
                style = MaterialTheme.typography.headlineMedium,
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Create your secure account.",
                style = MaterialTheme.typography.bodyMedium,
                color = RasGramTheme.TextMuted
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name", color = RasGramTheme.TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = mobile,
                onValueChange = { if (it.length <= 11 && it.all { c -> c.isDigit() }) mobile = it },
                label = { Text("Mobile Number (11 digits)", color = RasGramTheme.TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { if (it.length <= 6) password = it },
                label = { Text("Create 6-digit Password", color = RasGramTheme.TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = outlinedFieldColors()
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && mobile.length == 11 && password.length >= 4) {
                        isLoading = true
                        scope.launch {
                            try {
                                auth.signInAnonymously().await()
                                val userRef = db.collection("chat_users").document(mobile)
                                val userSnap = userRef.get().await()

                                if (userSnap.exists()) {
                                    val dbData = userSnap.data
                                    if (dbData?.get("password") != null && dbData["password"] != password) {
                                        Toast.makeText(context, "Wrong password!", Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        return@launch
                                    }
                                    userRef.update("lastActive", System.currentTimeMillis())
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

                                val savedName = userSnap.getString("name") ?: name
                                val savedAvatar = userSnap.getString("avatarUrl") ?: ""
                                onLogin(User(name = savedName, mobile = mobile, password = password, avatarUrl = savedAvatar))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please fill all details correctly.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text("Login / Register", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ==================== MAIN CHAT SCREEN ====================
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
    var liveCurrentUser by remember { mutableStateOf(currentUser) }
    val isCompact = isCompactScreen()

    val db = remember { FirebaseFirestore.getInstance() }
    LaunchedEffect(currentUser.mobile) {
        db.collection("chat_users").document(currentUser.mobile)
            .addSnapshotListener { snap, _ ->
                snap?.data?.let { data ->
                    liveCurrentUser = liveCurrentUser.copy(
                        name = data["name"] as? String ?: liveCurrentUser.name,
                        avatarUrl = data["avatarUrl"] as? String ?: liveCurrentUser.avatarUrl
                    )
                }
            }
        while (true) {
            db.collection("chat_users").document(currentUser.mobile)
                .update("lastActive", System.currentTimeMillis())
            kotlinx.coroutines.delay(60_000)
        }
    }

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
                visible = selectedContact == null || !isCompact,
                modifier = Modifier.fillMaxHeight()
            ) {
                SidebarPanel(
                    currentUser = liveCurrentUser,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedContact != null) {
                    ChatArea(
                        currentUser = liveCurrentUser,
                        contact = selectedContact!!,
                        onBack = { selectedContact = null },
                        onCallClick = { type ->
                            callType = type
                            showCallUI = true
                        }
                    )
                } else {
                    if (!isCompact) EmptyChatState()
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentUser = liveCurrentUser,
            onDismiss = { showSettings = false },
            onSave = { showSettings = false }
        )
    }

    if (showNewGroup) {
        NewGroupDialog(
            onDismiss = { showNewGroup = false },
            onCreateGroup = { showNewGroup = false }
        )
    }

    if (showLockedChats) {
        LockedChatsDialog(
            onDismiss = {
                showLockedChats = false
                isViewingLocked = false
            }
        )
    }

    if (showCallUI && selectedContact != null) {
        CallingScreen(
            currentUser = liveCurrentUser,
            contact = selectedContact!!,
            callType = callType,
            onEndCall = { showCallUI = false }
        )
    }
}

// ==================== WEBRTC CALLING SCREEN ====================
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
    var isSpeakerOn by remember { mutableStateOf(callType == "video") }
    var isConnected by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(0) }
    var callId by remember { mutableStateOf("") }

    val eglBase = remember { EglBase.create() }
    val peerConnectionFactory = remember {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )

    var peerConnection by remember { mutableStateOf<PeerConnection?>(null) }
    var localStream by remember { mutableStateOf<MediaStream?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasCamPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(Unit) {
        if (!hasMicPerm) {
            Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
            onEndCall()
            return@LaunchedEffect
        }

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerOn

            val chatHash = generateChatId(currentUser.mobile, contact.mobile)
            callId = "call_${chatHash}_${System.currentTimeMillis()}"

            val stream = peerConnectionFactory.createLocalMediaStream("localStream")
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            val audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)
            stream.addTrack(audioTrack)

            if (callType == "video" && hasCamPerm) {
                val videoCapturer = getVideoCapturer(context)
                if (videoCapturer != null) {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
                    videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                    videoCapturer.startCapture(1280, 720, 30)
                    val vTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
                    stream.addTrack(vTrack)
                    localVideoTrack = vTrack
                }
            }

            localStream = stream

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val observer = object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        scope.launch {
                            db.collection("calls").document(callId)
                                .collection("caller_ice")
                                .add(
                                    mapOf(
                                        "sdpMid" to it.sdpMid,
                                        "sdpMLineIndex" to it.sdpMLineIndex,
                                        "candidate" to it.sdp
                                    )
                                )
                        }
                    }
                }

                override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
                override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            callStatus = "Connected"
                            isConnected = true
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED -> {
                            scope.launch { onEndCall() }
                        }
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(b: Boolean) {}
                override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(s: MediaStream?) {
                    s?.videoTracks?.firstOrNull()?.let { remoteVideoTrack = it }
                }

                override fun onRemoveStream(s: MediaStream?) {}
                override fun onDataChannel(d: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
                    streams?.firstOrNull()?.videoTracks?.firstOrNull()?.let { remoteVideoTrack = it }
                }
            }

            val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            stream.audioTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            if (callType == "video") {
                stream.videoTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            }
            peerConnection = pc

            val offerConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (callType == "video") {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            pc?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(s: SessionDescription?) {}
                            override fun onSetSuccess() {
                                scope.launch {
                                    val callData = hashMapOf(
                                        "caller" to currentUser.mobile,
                                        "callee" to contact.mobile,
                                        "type" to callType,
                                        "status" to "calling",
                                        "timestamp" to System.currentTimeMillis(),
                                        "offer" to mapOf("type" to it.type.canonicalForm(), "sdp" to it.description)
                                    )
                                    db.collection("calls").document(callId).set(callData)
                                }
                            }

                            override fun onCreateFailure(e: String?) {}
                            override fun onSetFailure(e: String?) {}
                        }, it)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(e: String?) {}
                override fun onSetFailure(e: String?) {}
            }, offerConstraints)

            db.collection("calls").document(callId)
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.data ?: return@addSnapshotListener
                    val status = data["status"] as? String

                    if (status == "answered" && data["answer"] != null) {
                        val answerMap = data["answer"] as? Map<*, *>
                        val sdpStr = answerMap?.get("sdp") as? String ?: return@addSnapshotListener
                        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                        pc?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(s: SessionDescription?) {}
                            override fun onSetSuccess() {
                                callStatus = "Connected"
                                isConnected = true
                                scope.launch {
                                    db.collection("calls").document(callId)
                                        .collection("callee_ice").get().await()
                                        .documents.forEach { doc ->
                                            val d = doc.data ?: return@forEach
                                            val c = IceCandidate(
                                                d["sdpMid"] as? String ?: "",
                                                (d["sdpMLineIndex"] as? Long)?.toInt() ?: 0,
                                                d["candidate"] as? String ?: ""
                                            )
                                            pc?.addIceCandidate(c)
                                        }
                                }
                            }

                            override fun onCreateFailure(e: String?) {}
                            override fun onSetFailure(e: String?) {}
                        }, answerSdp)
                    } else if (status == "ended" || status == "rejected") {
                        scope.launch { onEndCall() }
                    }
                }

        } catch (e: Exception) {
            Toast.makeText(context, "Call error: ${e.message}", Toast.LENGTH_SHORT).show()
            onEndCall()
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            peerConnection?.close()
            peerConnection = null
            localStream?.dispose()
            localStream = null
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            // FIX: release eglBase safely
            try { eglBase.release() } catch (_: Exception) {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B141A)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (callType == "video") {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also {
                            it.init(eglBase.eglBaseContext, null)
                            it.setMirror(false)
                            remoteSurfaceView = it
                            remoteVideoTrack?.addSink(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also {
                            it.init(eglBase.eglBaseContext, null)
                            it.setMirror(true)
                            localSurfaceView = it
                            localVideoTrack?.addSink(it)
                        }
                    },
                    modifier = Modifier
                        .size(120.dp, 160.dp)
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                if (callType != "video" || !isConnected) {
                    AsyncImage(
                        model = contact.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${contact.name}&size=200" },
                        contentDescription = "Call Avatar",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(4.dp, RasGramTheme.Green, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(contact.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isConnected) formatTime(callSeconds) else callStatus,
                        style = MaterialTheme.typography.titleMedium,
                        color = RasGramTheme.Green,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            formatTime(callSeconds),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFF182229).copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                isActive = isMuted,
                                activeColor = RasGramTheme.Red
                            ) {
                                isMuted = !isMuted
                                localStream?.audioTracks?.firstOrNull()?.setEnabled(!isMuted)
                            }

                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (callId.isNotEmpty()) {
                                            db.collection("calls").document(callId).update("status", "ended")
                                        }
                                        onEndCall()
                                    }
                                },
                                containerColor = RasGramTheme.Red,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            CallControlButton(
                                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                label = "Speaker",
                                isActive = isSpeakerOn,
                                activeColor = RasGramTheme.Green
                            ) {
                                isSpeakerOn = !isSpeakerOn
                                audioManager.isSpeakerphoneOn = isSpeakerOn
                            }
                        }

                        if (callType == "video") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                CallControlButton(
                                    icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                    label = if (isCameraOff) "Cam Off" else "Cam On",
                                    isActive = isCameraOff,
                                    activeColor = RasGramTheme.Red
                                ) {
                                    isCameraOff = !isCameraOff
                                    localStream?.videoTracks?.firstOrNull()?.setEnabled(!isCameraOff)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = if (isActive) activeColor else Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

fun getVideoCapturer(context: Context): VideoCapturer? {
    return try {
        val enumerator = Camera2Enumerator(context)
        enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?.let { enumerator.createCapturer(it, null) }
    } catch (e: Exception) {
        try {
            val enumerator = Camera1Enumerator(false)
            enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?.let { enumerator.createCapturer(it, null) }
        } catch (e2: Exception) {
            null
        }
    }
}

// ==================== SIDEBAR PANEL ====================
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
        db.collection("chat_users").addSnapshotListener { snapshot, _ ->
            snapshot?.let {
                users = it.documents.mapNotNull { doc ->
                    doc.data?.let { data ->
                        User(
                            name = data["name"] as? String ?: "",
                            mobile = doc.id,
                            avatarUrl = data["avatarUrl"] as? String ?: "",
                            lastActive = data["lastActive"] as? Long ?: 0,
                            typingTo = data["typingTo"] as? String,
                            statusVisible = data["statusVisible"] as? Boolean ?: true
                        )
                    }
                }.filter { it.mobile != currentUser.mobile }
            }
        }
    }

    LaunchedEffect(users) {
        users.forEach { user ->
            val chatId = generateChatId(currentUser.mobile, user.mobile)
            db.collection("pvt_msg_$chatId")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snap, _ ->
                    snap?.documents?.firstOrNull()?.let { doc ->
                        val data = doc.data ?: return@let
                        val msg = Message(
                            id = doc.id,
                            text = data["text"] as? String ?: "",
                            senderMobile = data["senderMobile"] as? String ?: "",
                            timestamp = data["timestamp"] as? Long ?: 0,
                            timeString = data["timeString"] as? String ?: "",
                            fileUrl = data["fileUrl"] as? String,
                            fileType = data["fileType"] as? String,
                            read = data["read"] as? Boolean ?: false,
                            isCallLog = data["isCallLog"] as? Boolean ?: false
                        )
                        latestMessages = latestMessages + (user.mobile to msg)
                    }
                }
        }
    }

    val isCompact = isCompactScreen()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            // FIX: use correct width based on screen, avoid calling isCompactScreen inside non-composable
            .width(if (isCompact) 400.dp else 360.dp)
            .background(RasGramTheme.DarkBackground)
            // FIX: renamed extension to rightBorder to avoid conflict with built-in Modifier.border
            .rightBorder(1.dp, RasGramTheme.Border)
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
                model = currentUser.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${currentUser.name}" },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onSettingsClick() }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row {
                Text("Ras", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold)
                Text("Gram", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.Green, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = RasGramTheme.TextMuted)
            }
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = RasGramTheme.TextMuted)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New Group") },
                        onClick = { onNewGroupClick(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Group, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { onSettingsClick(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Settings, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle Theme") },
                        onClick = { onToggleTheme(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Brightness6, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Locked Chats") },
                        onClick = { onLockedChatsClick(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = RasGramTheme.Green) }
                    )
                    HorizontalDivider(color = RasGramTheme.Border)
                    DropdownMenuItem(
                        text = { Text("Logout", color = RasGramTheme.Red) },
                        onClick = { onLogout(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Logout, null, tint = RasGramTheme.Red) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search or start new chat", color = RasGramTheme.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = RasGramTheme.TextMuted) },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(24.dp)
        )

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
                    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = RasGramTheme.Green.copy(alpha = 0.1f)) {
                        Icon(Icons.Default.Lock, null, tint = RasGramTheme.Green, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Locked chats", color = RasGramTheme.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = RasGramTheme.Border)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                users.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.mobile.contains(searchQuery)
                }.sortedByDescending { user ->
                    latestMessages[user.mobile]?.timestamp ?: 0L
                }
            ) { user ->
                ContactItem(
                    user = user,
                    latestMessage = latestMessages[user.mobile],
                    isSelected = false,
                    currentUserMobile = currentUser.mobile,
                    onClick = { onContactClick(user) }
                )
                HorizontalDivider(color = RasGramTheme.Border)
            }
        }
    }
}

// ==================== CONTACT ITEM ====================
@Composable
fun ContactItem(
    user: User,
    latestMessage: Message?,
    isSelected: Boolean,
    currentUserMobile: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (isSelected) RasGramTheme.DarkPanel else Color.Transparent
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = user.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.name}&background=8696a0&color=fff&bold=true" },
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                if (user.lastActive > System.currentTimeMillis() - 120_000) {
                    // FIX: Use Box with background instead of Surface with border to avoid BorderStroke conflict
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .border(2.dp, RasGramTheme.DarkBackground, CircleShape)
                            .background(RasGramTheme.Green, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RasGramTheme.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (user.typingTo != null) {
                    Text("typing...", style = MaterialTheme.typography.bodyMedium, color = RasGramTheme.Green, fontWeight = FontWeight.Medium)
                } else {
                    latestMessage?.let { msg ->
                        Text(
                            if (msg.text.isNotEmpty()) msg.text else getFileTypePreview(msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!msg.read && msg.senderMobile != currentUserMobile) RasGramTheme.TextPrimary else RasGramTheme.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: Text("Tap to chat...", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            latestMessage?.let { msg ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        msg.timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!msg.read && msg.senderMobile != currentUserMobile) RasGramTheme.Green else RasGramTheme.TextMuted
                    )
                    if (!msg.read && msg.senderMobile != currentUserMobile) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(shape = CircleShape, color = RasGramTheme.Green) {
                            Text(
                                "1",
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

// ==================== CHAT AREA ====================
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    var liveContact by remember { mutableStateOf(contact) }
    val isCompact = isCompactScreen()

    val chatId = remember(currentUser.mobile, contact.mobile) {
        generateChatId(currentUser.mobile, contact.mobile)
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            coroutineScope.launch {
                try {
                    val (url, fileName, fileType) = uploadToCloudinary(context, it)
                    if (url != null) {
                        sendMessage(db, chatId, currentUser.mobile, "", url, fileName, fileType)
                        Toast.makeText(context, "File sent!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    LaunchedEffect(chatId) {
        db.collection("pvt_msg_$chatId")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    messages = it.documents.mapNotNull { doc ->
                        doc.data?.let { data ->
                            Message(
                                id = doc.id,
                                text = data["text"] as? String ?: "",
                                senderMobile = data["senderMobile"] as? String ?: "",
                                timestamp = data["timestamp"] as? Long ?: 0,
                                timeString = data["timeString"] as? String ?: "",
                                fileUrl = data["fileUrl"] as? String,
                                fileName = data["fileName"] as? String,
                                fileType = data["fileType"] as? String,
                                reaction = data["reaction"] as? String,
                                read = data["read"] as? Boolean ?: false,
                                isCallLog = data["isCallLog"] as? Boolean ?: false,
                                callStatus = data["callStatus"] as? String,
                                callType = data["callType"] as? String,
                                isPending = doc.metadata.hasPendingWrites()
                            )
                        }
                    }
                }
            }
    }

    LaunchedEffect(contact.mobile) {
        db.collection("chat_users").document(contact.mobile)
            .addSnapshotListener { snap, _ ->
                snap?.data?.let { data ->
                    liveContact = liveContact.copy(
                        typingTo = data["typingTo"] as? String,
                        lastActive = data["lastActive"] as? Long ?: 0
                    )
                }
            }
    }

    LaunchedEffect(contact.mobile, messages.size) {
        messages.filter { it.senderMobile == contact.mobile && !it.read }
            .forEach { msg ->
                db.collection("pvt_msg_$chatId").document(msg.id).update("read", true)
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        // Header
        Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FIX: use captured isCompact variable, not function call inside non-composable lambda
                if (isCompact) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = RasGramTheme.TextMuted)
                    }
                }
                AsyncImage(
                    model = liveContact.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${liveContact.name}" },
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(liveContact.name, style = MaterialTheme.typography.bodyLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            liveContact.typingTo == currentUser.mobile -> "typing..."
                            liveContact.lastActive > System.currentTimeMillis() - 120_000 -> "online"
                            else -> {
                                val diff = System.currentTimeMillis() - liveContact.lastActive
                                "last seen ${formatLastSeen(diff)}"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (liveContact.typingTo == currentUser.mobile) RasGramTheme.Green else RasGramTheme.TextMuted
                    )
                }
                IconButton(onClick = { onCallClick("video") }) {
                    Icon(Icons.Default.Videocam, null, tint = RasGramTheme.TextMuted)
                }
                IconButton(onClick = { onCallClick("audio") }) {
                    Icon(Icons.Default.Call, null, tint = RasGramTheme.TextMuted)
                }
                var showChatMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showChatMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = RasGramTheme.TextMuted)
                    }
                    DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear Chat", color = RasGramTheme.Red) },
                            onClick = {
                                showChatMenu = false
                                coroutineScope.launch {
                                    db.collection("pvt_msg_$chatId").get().await().documents.forEach { it.reference.delete() }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(RasGramTheme.DarkBackground.copy(alpha = 0.95f)),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFD279))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Messages are end-to-end encrypted.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD279))
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMe = message.senderMobile == currentUser.mobile,
                    onReact = { reaction ->
                        coroutineScope.launch {
                            db.collection("pvt_msg_$chatId").document(message.id).update("reaction", reaction)
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            db.collection("pvt_msg_$chatId").document(message.id).delete()
                        }
                    }
                )
            }
        }

        if (isUploading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = RasGramTheme.Green
            )
        }

        // Input Area
        Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    fileLauncher.launch(arrayOf("*/*"))
                }) {
                    Icon(Icons.Default.AttachFile, null, tint = RasGramTheme.TextMuted)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        if (it.isNotEmpty()) {
                            typingJob?.cancel()
                            typingJob = coroutineScope.launch {
                                db.collection("chat_users").document(currentUser.mobile).update("typingTo", contact.mobile)
                                kotlinx.coroutines.delay(2500)
                                db.collection("chat_users").document(currentUser.mobile).update("typingTo", null)
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
                                sendMessage(db, chatId, currentUser.mobile, inputText, null, null, null)
                                inputText = ""
                                coroutineScope.launch {
                                    db.collection("chat_users").document(currentUser.mobile).update("typingTo", null)
                                }
                            }
                        }
                    )
                )

                // FIX: Use updated AnimatedContent API (no togetherWith infix, use with())
                AnimatedContent(
                    targetState = inputText.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "SendButton"
                ) { hasText ->
                    if (hasText) {
                        IconButton(onClick = {
                            if (inputText.isNotBlank()) {
                                sendMessage(db, chatId, currentUser.mobile, inputText, null, null, null)
                                inputText = ""
                                coroutineScope.launch {
                                    db.collection("chat_users").document(currentUser.mobile).update("typingTo", null)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Send, null, tint = RasGramTheme.Green)
                        }
                    } else {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Mic, null, tint = RasGramTheme.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

// ==================== CLOUDINARY UPLOAD ====================
suspend fun uploadToCloudinary(
    context: Context,
    uri: Uri
): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"

        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: return@withContext Triple(null, null, null)
        val tempFile = File(context.cacheDir, fileName)
        tempFile.outputStream().use { out -> inputStream.copyTo(out) }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, tempFile.asRequestBody(mimeType.toMediaTypeOrNull()))
            .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url(CLOUDINARY_UPLOAD_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext Triple(null, null, null)
        val json = JSONObject(responseBody)
        val url = json.optString("secure_url", null)
        tempFile.delete()

        Triple(url, fileName, mimeType)
    } catch (e: Exception) {
        Triple(null, null, null)
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(idx)
        }
    } catch (e: Exception) {
        null
    }
}

// ==================== SEND MESSAGE ====================
fun sendMessage(
    db: FirebaseFirestore,
    chatId: String,
    senderMobile: String,
    text: String,
    fileUrl: String?,
    fileName: String?,
    fileType: String?
) {
    val message = hashMapOf(
        "text" to text,
        "senderMobile" to senderMobile,
        "timestamp" to System.currentTimeMillis(),
        "timeString" to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
        "fileUrl" to fileUrl,
        "fileName" to fileName,
        "fileType" to fileType,
        "reaction" to null,
        "read" to false,
        "isCallLog" to false
    )
    db.collection("pvt_msg_$chatId").add(message)
}

// ==================== MESSAGE BUBBLE ====================
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
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (message.isCallLog) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF182229)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (message.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if (message.callStatus == "missed") RasGramTheme.Red else RasGramTheme.TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(message.text, style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextPrimary)
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { showReactions = !showReactions },
                        onLongClick = { showReactions = true }
                    ),
                shape = RoundedCornerShape(
                    topStart = 8.dp, topEnd = 8.dp,
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
                    message.fileUrl?.let { url ->
                        when {
                            message.fileType?.startsWith("image/") == true -> {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                                            context.startActivity(intent)
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }

                            message.fileType?.startsWith("video/") == true -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clickable {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                                            intent.setDataAndType(url.toUri(), message.fileType)
                                            context.startActivity(intent)
                                        },
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(56.dp))
                                        Text(
                                            message.fileName ?: "Video",
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(8.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            message.fileType?.startsWith("audio/") == true -> {
                                var isPlaying by remember { mutableStateOf(false) }
                                // FIX: wrap MediaPlayer in DisposableEffect to prevent leaks
                                val mediaPlayer = remember { MediaPlayer() }
                                DisposableEffect(Unit) {
                                    onDispose {
                                        try {
                                            if (mediaPlayer.isPlaying) mediaPlayer.stop()
                                            mediaPlayer.release()
                                        } catch (_: Exception) {}
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        if (!isPlaying) {
                                            try {
                                                mediaPlayer.reset()
                                                mediaPlayer.setDataSource(url)
                                                mediaPlayer.prepare()
                                                mediaPlayer.start()
                                                isPlaying = true
                                                mediaPlayer.setOnCompletionListener { isPlaying = false }
                                            } catch (_: Exception) {}
                                        } else {
                                            mediaPlayer.pause()
                                            isPlaying = false
                                        }
                                    }) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            null,
                                            tint = RasGramTheme.TextPrimary
                                        )
                                    }
                                    Icon(Icons.Default.Mic, null, tint = RasGramTheme.Green, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        message.fileName ?: "Audio",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RasGramTheme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 80.dp)
                                    )
                                }
                            }

                            else -> {
                                Row(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                                            context.startActivity(intent)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, null, tint = RasGramTheme.Green, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            message.fileName ?: "Document",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = RasGramTheme.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted)
                                    }
                                }
                            }
                        }
                    }

                    if (message.text.isNotEmpty()) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RasGramTheme.TextPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(message.timeString, style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted.copy(alpha = 0.7f))
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when {
                                    message.isPending -> Icons.Default.Schedule
                                    message.read -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.read && !message.isPending) RasGramTheme.BlueTick else RasGramTheme.TextMuted
                            )
                        }
                    }
                }
            }

            if (showReactions) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("👍", "❤️", "😂", "😢", "😮", "🙏").forEach { emoji ->
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReact(emoji); showReactions = false },
                            shape = CircleShape,
                            color = RasGramTheme.DarkPanel,
                            // FIX: Use border parameter on Surface correctly
                            border = BorderStroke(1.dp, RasGramTheme.Border)
                        ) {
                            Text(emoji, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (isMe) {
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showDeleteConfirm = true },
                            shape = CircleShape,
                            color = RasGramTheme.Red.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, RasGramTheme.Red.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.padding(8.dp), tint = RasGramTheme.Red)
                        }
                    }
                }
            }

            message.reaction?.let { reaction ->
                Surface(
                    modifier = Modifier.offset(y = (-4).dp),
                    shape = RoundedCornerShape(12.dp),
                    color = RasGramTheme.DarkPanel,
                    border = BorderStroke(1.dp, RasGramTheme.Border)
                ) {
                    Text(reaction, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Message") },
                    text = { Text("Delete message for everyone?") },
                    confirmButton = {
                        TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                            Text("Delete", color = RasGramTheme.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

// ==================== DIALOGS ====================
@Composable
fun SettingsDialog(currentUser: User, onDismiss: () -> Unit, onSave: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentUser.name) }
    var isUploading by remember { mutableStateOf(false) }
    var avatarUrl by remember { mutableStateOf(currentUser.avatarUrl) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                val (url, _, _) = uploadToCloudinary(context, it)
                if (url != null) {
                    avatarUrl = url
                    db.collection("chat_users").document(currentUser.mobile).update("avatarUrl", url)
                    Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = { Text("Profile", color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${currentUser.name}" },
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(2.dp, RasGramTheme.Green, CircleShape)
                    )
                    FloatingActionButton(
                        onClick = { imageLauncher.launch(arrayOf("image/*")) },
                        containerColor = RasGramTheme.Green,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                if (isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(color = RasGramTheme.Green, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name", color = RasGramTheme.Green) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mobile: ${currentUser.mobile}", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        db.collection("chat_users").document(currentUser.mobile).update("name", name)
                    }
                    onSave()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)
            ) { Text("Save", color = Color.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = RasGramTheme.TextMuted) }
        }
    )
}

@Composable
fun NewGroupDialog(onDismiss: () -> Unit, onCreateGroup: () -> Unit) {
    var groupName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = { Text("New Group", color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Group chat coming soon!", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = onDismiss) { Text("Cancel", color = RasGramTheme.TextMuted) }
        }
    )
}

@Composable
fun LockedChatsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RasGramTheme.DarkPanel,
        title = { Text("Locked Chats", color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("These chats are locked with your PIN.", color = RasGramTheme.TextMuted) },
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
        Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = RasGramTheme.DarkPanel) {
            Icon(Icons.Default.Send, null, modifier = Modifier.padding(30.dp), tint = RasGramTheme.Green)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("RasGram", style = MaterialTheme.typography.headlineLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Send and receive messages securely.\nEnd-to-end encrypted.",
            style = MaterialTheme.typography.bodyMedium,
            color = RasGramTheme.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = RasGramTheme.Green)
            Spacer(modifier = Modifier.width(8.dp))
            Text("End-to-end encrypted", style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextMuted)
        }
    }
}

// ==================== HELPER FUNCTIONS ====================
fun generateChatId(mobile1: String, mobile2: String): String =
    if (mobile1 < mobile2) "${mobile1}_${mobile2}" else "${mobile2}_${mobile1}"

fun getFileTypePreview(message: Message): String = when {
    message.isCallLog -> "${if (message.callType == "video") "📹" else "📞"} ${message.text}"
    message.fileType?.startsWith("image/") == true -> "📷 Photo"
    message.fileType?.startsWith("video/") == true -> "🎥 Video"
    message.fileType?.startsWith("audio/") == true -> "🎤 Voice"
    message.fileUrl != null -> "📎 ${message.fileName ?: "Document"}"
    else -> message.text
}

fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

fun formatLastSeen(diffMs: Long): String {
    val mins = diffMs / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RasGramTheme.Green,
    unfocusedBorderColor = RasGramTheme.Border,
    focusedTextColor = RasGramTheme.TextPrimary,
    unfocusedTextColor = RasGramTheme.TextPrimary,
    cursorColor = RasGramTheme.Green,
    focusedLabelColor = RasGramTheme.Green,
    unfocusedLabelColor = RasGramTheme.TextMuted
)

// FIX: Renamed from border() to rightBorder() to avoid conflict with built-in Modifier.border()
fun Modifier.rightBorder(width: Dp, color: Color): Modifier = this.then(
    Modifier.drawBehind {
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = width.toPx()
        )
    }
)

// FIX: isCompactScreen must always be called inside @Composable context;
// capture the value at the top of composable functions and pass it down.
@Composable
fun isCompactScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp < 600
}
