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

// ==================== GROUP CHAT AREA ====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatArea(
    currentUser: User,
    group: Group,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCompact = isCompactScreen()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var selectedMessages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var liveGroup by remember { mutableStateOf(group) }

    // Group members cache for name mapping
    var membersMap by remember { mutableStateOf<Map<String, User>>(emptyMap()) }

    // File launchers
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val imageVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                try {
                    val (url, fileName, fileType) = uploadToCloudinary(context, it) { prog -> uploadProgress = prog }
                    if (url != null) {
                        val msgMap = hashMapOf(
                            "text" to "", "senderMobile" to currentUser.mobile, "fileUrl" to url,
                            "fileName" to fileName, "fileType" to fileType, "timestamp" to System.currentTimeMillis(),
                            "timeString" to java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        db.collection("groups").document(group.id).collection("messages").add(msgMap)
                    } else android.widget.Toast.makeText(context, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                isUploading = false
                uploadProgress = 0f
            }
        }
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                try {
                    val (url, fileName, fileType) = uploadToCloudinary(context, it) { prog -> uploadProgress = prog }
                    if (url != null) {
                        val msgMap = hashMapOf(
                            "text" to "", "senderMobile" to currentUser.mobile, "fileUrl" to url,
                            "fileName" to fileName, "fileType" to fileType, "timestamp" to System.currentTimeMillis(),
                            "timeString" to java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        db.collection("groups").document(group.id).collection("messages").add(msgMap)
                    } else android.widget.Toast.makeText(context, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                isUploading = false
                uploadProgress = 0f
            }
        }
    }

    LaunchedEffect(group.id) {
        if (group.members.isNotEmpty()) {
            db.collection("chat_users").whereIn("mobile", group.members.take(10)).get().addOnSuccessListener { snap ->
                val map = mutableMapOf<String, User>()
                snap.documents.forEach { doc ->
                    doc.data?.let { d ->
                        val u = User(
                            uid = doc.id, name = d["name"] as? String ?: "",
                            mobile = d["mobile"] as? String ?: "", avatarUrl = d["avatarUrl"] as? String ?: ""
                        )
                        map[u.mobile] = u
                    }
                }
                membersMap = map
            }
        }

        db.collection("groups").document(group.id).addSnapshotListener { snap, _ ->
            snap?.data?.let { d ->
                liveGroup = liveGroup.copy(
                    name = d["name"] as? String ?: liveGroup.name,
                    typingUsers = (d["typingUsers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            }
        }

        db.collection("groups").document(group.id).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                snap?.documents?.mapNotNull { doc ->
                    doc.data?.let { d ->
                        val timestamp = d["timestamp"] as? Long ?: 0
                        val timer = group.disappearingTimer
                        if (timer > 0 && (System.currentTimeMillis() - timestamp) > timer) return@mapNotNull null

                        Message(
                            id = doc.id,
                            text = AESCrypto.decrypt(group.id, d["text"] as? String ?: ""),
                            senderMobile = d["senderMobile"] as? String ?: "",
                            receiverMobile = group.id,
                            timestamp = timestamp,
                            timeString = d["timeString"] as? String ?: "",
                            fileUrl = d["fileUrl"] as? String,
                            fileName = d["fileName"] as? String,
                            fileType = d["fileType"] as? String,
                            fileSizeBytes = d["fileSizeBytes"] as? Long ?: 0,
                            reaction = d["reaction"] as? String,
                            isDeleted = d["isDeleted"] as? Boolean ?: false,
                            replyToId = d["replyToId"] as? String,
                            replyToText = d["replyToText"]?.let { AESCrypto.decrypt(group.id, it as String) },
                            replyToSender = d["replyToSender"] as? String,
                            duration = (d["duration"] as? Long)?.toInt() ?: 0
                        )
                    }
                }?.also { msgs -> messages = msgs }
            }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    LaunchedEffect(isRecording) {
        if (isRecording) { recordingSeconds = 0; while (isRecording) { delay(1000); recordingSeconds++ } }
    }

    BackHandler(enabled = selectedMessages.isNotEmpty()) { selectedMessages = emptySet() }

    Column(modifier = Modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        if (selectedMessages.isNotEmpty()) {
            SelectionHeader(
                count = selectedMessages.size,
                onClose = { selectedMessages = emptySet() },
                onDelete = {
                    scope.launch {
                        selectedMessages.forEach { id ->
                            db.collection("groups").document(group.id).collection("messages").document(id).update("isDeleted", true, "text", "")
                        }
                        selectedMessages = emptySet()
                    }
                },
                onForward = { showForwardDialog = true }, onStar = { },
                onCopy = {
                    val text = messages.filter { it.id in selectedMessages }.joinToString("\n") { it.text }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("messages", text))
                    selectedMessages = emptySet()
                }
            )
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel, shadowElevation = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isCompact) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = RasGramTheme.TextPrimary) } }
                    AsyncImage(
                        model = liveGroup.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${liveGroup.name.replace(" ", "+")}&background=005C4B&color=fff&bold=true" },
                        contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(liveGroup.name, style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        
                        val otherTypingUsers = liveGroup.typingUsers.filter { it != currentUser.mobile }
                        val subtitle = if (otherTypingUsers.isNotEmpty()) {
                            val typingNames = otherTypingUsers.map { membersMap[it]?.name ?: it }
                            "${typingNames.joinToString(", ")} is typing..."
                        } else {
                            "${liveGroup.members.size} members"
                        }
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (otherTypingUsers.isNotEmpty()) RasGramTheme.Green else RasGramTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { /* Call feature in group */ }) { Icon(Icons.Default.Call, null, tint = RasGramTheme.Green) }
                    IconButton(onClick = { /* Group info */ }) { Icon(Icons.Default.MoreVert, null, tint = RasGramTheme.TextMuted) }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { EncryptionNotice() }
                messages.forEach { msg ->
                    item(key = msg.id) {
                        val isMe = msg.senderMobile == currentUser.mobile
                        val senderName = if (isMe) "You" else membersMap[msg.senderMobile]?.name ?: msg.senderMobile
                        MessageBubble(
                            message = msg, isMe = isMe, isSelected = msg.id in selectedMessages, senderName = senderName,
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedMessages = selectedMessages + msg.id },
                            onClick = { if (selectedMessages.isNotEmpty()) selectedMessages = if (msg.id in selectedMessages) selectedMessages - msg.id else selectedMessages + msg.id },
                            onReact = { rx -> scope.launch { db.collection("groups").document(group.id).collection("messages").document(msg.id).update("reaction", if (msg.reaction == rx) null else rx) } },
                            onReply = { replyToMessage = msg },
                            onDelete = { scope.launch { db.collection("groups").document(group.id).collection("messages").document(msg.id).update("isDeleted", true, "text", "") } },
                            onStar = { }, onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", msg.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (isUploading) {
            LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth(), color = RasGramTheme.Green, trackColor = RasGramTheme.DarkPanel)
        }

        replyToMessage?.let { reply ->
            ReplyPreview(message = reply, currentUserMobile = currentUser.mobile, onDismiss = { replyToMessage = null })
        }

        ChatInputBar(
            inputText = inputText,
            onTextChange = { text ->
                inputText = text
                if (text.isNotEmpty()) {
                    typingJob?.cancel()
                    typingJob = scope.launch {
                        db.collection("groups").document(group.id).update(
                            "typingUsers", com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.mobile)
                        )
                        delay(2000L) // TYPING_DEBOUNCE_MS
                        db.collection("groups").document(group.id).update(
                            "typingUsers", com.google.firebase.firestore.FieldValue.arrayRemove(currentUser.mobile)
                        )
                    }
                }
            },
            onSend = {
                val text = inputText.trim()
                if (text.isNotBlank()) {
                    val encryptedText = AESCrypto.encrypt(group.id, text)
                    val encryptedReply = replyToMessage?.text?.let { AESCrypto.encrypt(group.id, it) }
                    val now = System.currentTimeMillis()
                    val msgMap = hashMapOf(
                        "text" to encryptedText, "senderMobile" to currentUser.mobile,
                        "timestamp" to now, "timeString" to java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)),
                        "replyToId" to replyToMessage?.id, "replyToText" to encryptedReply, "replyToSender" to replyToMessage?.senderMobile
                    )
                    db.collection("groups").document(group.id).collection("messages").add(msgMap)
                    db.collection("groups").document(group.id).update("lastMessageTime", now)
                    inputText = ""
                    replyToMessage = null
                    typingJob?.cancel()
                    scope.launch {
                        db.collection("groups").document(group.id).update(
                            "typingUsers", com.google.firebase.firestore.FieldValue.arrayRemove(currentUser.mobile)
                        )
                    }
                }
            },
            onAttachClick = { showAttachMenu = true },
            isRecording = isRecording, recordingSeconds = recordingSeconds,
            onMicPress = {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); return@ChatInputBar }
                if (!isRecording) {
                    try {
                        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                        recordingFile = file
                        val recorder = MediaRecorder()
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setOutputFile(file.absolutePath)
                        recorder.prepare(); recorder.start()
                        mediaRecorder = recorder
                        isRecording = true
                    } catch (e: Exception) {}
                }
            },
            onMicRelease = {
                if (isRecording) {
                    try {
                        mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null; isRecording = false
                        val file = recordingFile ?: return@ChatInputBar
                        if (file.exists() && file.length() > 0) {
                            isUploading = true
                            scope.launch {
                                val (url, fileName, _) = uploadToCloudinary(context, file.toUri()) { prog -> uploadProgress = prog }
                                if (url != null) {
                                    val now = System.currentTimeMillis()
                                    val msgMap = hashMapOf(
                                        "text" to "", "senderMobile" to currentUser.mobile, "fileUrl" to url, "fileName" to (fileName ?: "voice.m4a"),
                                        "fileType" to "audio/mp4", "duration" to recordingSeconds, "timestamp" to now,
                                        "timeString" to java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))
                                    )
                                    db.collection("groups").document(group.id).collection("messages").add(msgMap)
                                    db.collection("groups").document(group.id).update("lastMessageTime", now)
                                }
                                isUploading = false; uploadProgress = 0f; file.delete()
                            }
                        }
                    } catch (e: Exception) { isRecording = false }
                }
            },
            onMicCancel = { mediaRecorder?.release(); mediaRecorder = null; isRecording = false; recordingFile?.delete() }
        )
    }

    if (showForwardDialog) {
        ForwardMessageDialog(
            currentUser = currentUser,
            messages = messages.filter { it.id in selectedMessages },
            onDismiss = { showForwardDialog = false },
            onForwardComplete = { showForwardDialog = false; selectedMessages = emptySet() }
        )
    }

    if (showAttachMenu) {
        AttachmentMenuSheet(
            onDismiss = { showAttachMenu = false },
            onImageVideo = { imageVideoLauncher.launch(arrayOf("image/*", "video/*")); showAttachMenu = false },
            onDocument = { docLauncher.launch(arrayOf("*/*")); showAttachMenu = false },
            onAudio = { docLauncher.launch(arrayOf("audio/*")); showAttachMenu = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewBroadcastDialog(onDismiss: () -> Unit, currentUser: User) {
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var allUsers by remember { mutableStateOf(emptyList<User>()) }
    var step by remember { mutableIntStateOf(0) }
    var messageText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("chat_users").get().addOnSuccessListener { snap ->
            allUsers = snap.documents.mapNotNull {
                val u = User(
                    uid = it.id, name = it.getString("name") ?: "",
                    mobile = it.getString("mobile") ?: "", avatarUrl = it.getString("avatarUrl") ?: ""
                )
                if (u.mobile != currentUser.mobile) u else null
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = RasGramTheme.DarkBackground) {
            Column {
                // App Bar
                Row(modifier = Modifier.fillMaxWidth().background(RasGramTheme.DarkPanel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (step == 1) step = 0 else onDismiss() }) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Text(if (step == 0) "New Broadcast" else "Compose Message", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (step == 0 && selectedMembers.isNotEmpty()) {
                        FloatingActionButton(onClick = { step = 1 }, modifier = Modifier.size(44.dp), containerColor = RasGramTheme.Green) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, null, tint = Color.White)
                        }
                    }
                }

                if (step == 0) {
                    // Select members
                    if (selectedMembers.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(allUsers.filter { it.mobile in selectedMembers }) { u ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        AsyncImage(model = u.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${u.name}" }, contentDescription = null, modifier = Modifier.size(50.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        IconButton(onClick = { selectedMembers = selectedMembers - u.mobile }, modifier = Modifier.size(20.dp).align(Alignment.BottomEnd).background(RasGramTheme.DarkPanel, androidx.compose.foundation.shape.CircleShape)) {
                                            Icon(androidx.compose.material.icons.Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Text(u.name, style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextPrimary)
                                }
                            }
                        }
                        HorizontalDivider(color = RasGramTheme.Border)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allUsers) { u ->
                            val isSelected = u.mobile in selectedMembers
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectedMembers = if (isSelected) selectedMembers - u.mobile else selectedMembers + u.mobile
                            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = u.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${u.name}" }, contentDescription = null, modifier = Modifier.size(50.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(u.name, style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary, modifier = Modifier.weight(1f))
                                if (isSelected) Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, null, tint = RasGramTheme.Green)
                            }
                        }
                    }
                } else {
                    // Compose message
                    Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Broadcast to ${selectedMembers.size} recipients", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Type a message") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = RasGramTheme.TextPrimary, fontSize = 16.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RasGramTheme.Green,
                                unfocusedBorderColor = RasGramTheme.Border,
                                focusedContainerColor = RasGramTheme.DarkPanel,
                                unfocusedContainerColor = RasGramTheme.DarkPanel
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    scope.launch {
                                        selectedMembers.forEach { receiverMobile ->
                                            val chatId = generateChatId(currentUser.mobile, receiverMobile)
                                            val msg = Message(
                                                id = java.util.UUID.randomUUID().toString(),
                                                text = AESCrypto.encrypt(chatId, messageText),
                                                senderMobile = currentUser.mobile,
                                                receiverMobile = receiverMobile,
                                                timestamp = System.currentTimeMillis(),
                                                timeString = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                                            )
                                            db.collection("pvt_msg_${chatId}").add(msg)
                                        }
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text("Send Broadcast", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
