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

    DisposableEffect(Unit) {
        if (callType == "video") MainActivity.isVideoCallActive = true
        onDispose { MainActivity.isVideoCallActive = false }
    }

    val eglBase = remember { EglBase.create() }
    val peerConnectionFactory = remember {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }
    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
    )

    var peerConnection by remember { mutableStateOf<PeerConnection?>(null) }
    var localStream by remember { mutableStateOf<MediaStream?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(Unit) {
        val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) { Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show(); onEndCall(); return@LaunchedEffect }

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerOn
            val chatHash = generateChatId(currentUser.mobile, contact.mobile)
            callId = "call_${chatHash}_${System.currentTimeMillis()}"

            val stream = peerConnectionFactory.createLocalMediaStream("localStream")
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            stream.addTrack(peerConnectionFactory.createAudioTrack("audioTrack", audioSource))

            if (callType == "video") {
                val hasCamPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasCamPerm) {
                    getVideoCapturer(context)?.let { capturer ->
                        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                        val videoSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
                        capturer.initialize(helper, context, videoSource.capturerObserver)
                        capturer.startCapture(1280, 720, 30)
                        val vTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
                        stream.addTrack(vTrack)
                        localVideoTrack = vTrack
                    }
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
                    candidate?.let { c ->
                        scope.launch {
                            db.collection("calls").document(callId).collection("caller_ice").add(
                                mapOf("sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex, "candidate" to c.sdp)
                            )
                        }
                    }
                }
                override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
                override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> { callStatus = "Connected"; isConnected = true }
                        PeerConnection.IceConnectionState.DISCONNECTED, PeerConnection.IceConnectionState.FAILED -> scope.launch { onEndCall() }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(b: Boolean) {}
                override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(s: MediaStream?) { s?.videoTracks?.firstOrNull()?.let { remoteVideoTrack = it } }
                override fun onRemoveStream(s: MediaStream?) {}
                override fun onDataChannel(d: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
                    streams?.firstOrNull()?.videoTracks?.firstOrNull()?.let { remoteVideoTrack = it }
                }
            }

            val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            stream.audioTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            if (callType == "video") stream.videoTracks.forEach { pc?.addTrack(it, listOf("localStream")) }
            peerConnection = pc

            val offerConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (callType == "video") mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

            pc?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let { s ->
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(s2: SessionDescription?) {}
                            override fun onSetSuccess() {
                                scope.launch {
                                    db.collection("calls").document(callId).set(hashMapOf(
                                        "caller" to currentUser.mobile, "callee" to contact.mobile,
                                        "type" to callType, "status" to "calling",
                                        "timestamp" to System.currentTimeMillis(),
                                        "offer" to mapOf("type" to s.type.canonicalForm(), "sdp" to s.description)
                                    ))
                                    // Send FCM push to callee so call arrives even if app is closed
                                    sendFcmCallNotification(
                                        calleeMobile = contact.mobile,
                                        callerName = currentUser.name,
                                        callType = callType,
                                        callId = callId,
                                        db = db,
                                        context = context
                                    )
                                }
                            }
                            override fun onCreateFailure(e: String?) {}
                            override fun onSetFailure(e: String?) {}
                        }, s)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(e: String?) {}
                override fun onSetFailure(e: String?) {}
            }, offerConstraints)

            db.collection("calls").document(callId).addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data ?: return@addSnapshotListener
                when (data["status"] as? String) {
                    "answered" -> {
                        (data["answer"] as? Map<*, *>)?.let { ans ->
                            val sdpStr = ans["sdp"] as? String ?: return@addSnapshotListener
                            pc?.setRemoteDescription(object : SdpObserver {
                                override fun onCreateSuccess(s: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    callStatus = "Connected"; isConnected = true
                                    scope.launch {
                                        db.collection("calls").document(callId).collection("callee_ice").get().await()
                                            .documents.forEach { doc ->
                                                doc.data?.let { d ->
                                                    pc?.addIceCandidate(IceCandidate(d["sdpMid"] as? String ?: "", (d["sdpMLineIndex"] as? Long)?.toInt() ?: 0, d["candidate"] as? String ?: ""))
                                                }
                                            }
                                    }
                                }
                                override fun onCreateFailure(e: String?) {}
                                override fun onSetFailure(e: String?) {}
                            }, SessionDescription(SessionDescription.Type.ANSWER, sdpStr))
                        }
                    }
                    "ended", "rejected" -> scope.launch { onEndCall() }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Call error: ${e.message}", Toast.LENGTH_SHORT).show()
            onEndCall()
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) while (true) { delay(1000); callSeconds++ }
    }

    DisposableEffect(Unit) {
        onDispose {
            peerConnection?.close()
            localStream?.dispose()
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            try { eglBase.release() } catch (_: Exception) {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B141A)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (callType == "video") {
                AndroidView(factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { it.init(eglBase.eglBaseContext, null); it.setMirror(false); remoteSurfaceView = it; remoteVideoTrack?.addSink(it) }
                }, modifier = Modifier.fillMaxSize())
                AndroidView(factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { it.init(eglBase.eglBaseContext, null); it.setMirror(true); localSurfaceView = it; localVideoTrack?.addSink(it) }
                }, modifier = Modifier.size(120.dp, 160.dp).align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(12.dp)))
            }

            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                if (callType != "video" || !isConnected) {
                    AsyncImage(
                        model = contact.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${contact.name.replace(" ", "+")}&size=200&background=008069&color=fff" },
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).clip(CircleShape).border(3.dp, RasGramTheme.Green, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(contact.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isConnected) formatTime(callSeconds) else callStatus, color = RasGramTheme.Green, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.weight(1f))

                Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = Color(0xFF182229).copy(0.95f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CallControlButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = if (isMuted) "Unmute" else "Mute", isActive = isMuted, activeColor = RasGramTheme.Red) {
                                isMuted = !isMuted
                                localStream?.audioTracks?.firstOrNull()?.setEnabled(!isMuted)
                            }
                            FloatingActionButton(onClick = { scope.launch { if (callId.isNotEmpty()) db.collection("calls").document(callId).update("status", "ended"); onEndCall() } }, containerColor = RasGramTheme.Red, modifier = Modifier.size(72.dp)) {
                                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            CallControlButton(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, label = "Speaker", isActive = isSpeakerOn, activeColor = RasGramTheme.Green) {
                                isSpeakerOn = !isSpeakerOn
                                audioManager.isSpeakerphoneOn = isSpeakerOn
                            }
                        }
                        if (callType == "video") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                CallControlButton(icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, label = "Camera", isActive = isCameraOff, activeColor = RasGramTheme.Red) {
                                    isCameraOff = !isCameraOff
                                    localStream?.videoTracks?.firstOrNull()?.setEnabled(!isCameraOff)
                                }
                                CallControlButton(icon = Icons.Default.Cameraswitch, label = "Flip", isActive = false, activeColor = RasGramTheme.Green) { }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallControlButton(icon: ImageVector, label: String, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = if (isActive) activeColor else Color.White.copy(0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, label, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

