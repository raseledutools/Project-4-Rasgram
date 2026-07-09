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

// ==================== CHAT AREA ====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatArea(
    currentUser: User,
    contact: User,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCompact = isCompactScreen()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var smartReplies by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveContact by remember { mutableStateOf(contact) }
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
    
    var showSummarizeDialog by remember { mutableStateOf(false) }
    var chatSummary by remember { mutableStateOf<String?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    val chatId = remember(currentUser.mobile, contact.mobile) {
        generateChatId(currentUser.mobile, contact.mobile)
    }

    // File launchers
    val imageVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                try {
                    val (url, fileName, fileType) = uploadToCloudinary(context, it) { prog -> uploadProgress = prog }
                    if (url != null) {
                        sendMessage(db, chatId, currentUser.mobile, contact.mobile, "", url, fileName, fileType)
                    } else Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        sendMessage(db, chatId, currentUser.mobile, contact.mobile, "", url, fileName, fileType)
                    } else Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
                uploadProgress = 0f
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.RECORD_AUDIO] == true) {
            // Start recording
        }
    }

    // Load messages from Local Database
    val appDb = com.rasel.rasgram.model.AppDatabase.getDatabase(context)
    LaunchedEffect(chatId) {
        appDb.messageDao().getMessagesBetween(currentUser.mobile, contact.mobile).collect { msgs ->
            val timer = maxOf(currentUser.disappearingTimer, contact.disappearingTimer)
            messages = msgs.mapNotNull { m ->
                if (timer > 0 && (System.currentTimeMillis() - m.timestamp) > timer) {
                    null
                } else {
                    m.copy(
                        text = AESCrypto.decrypt(chatId, m.text),
                        replyToText = m.replyToText?.let { AESCrypto.decrypt(chatId, it) }
                    )
                }
            }
            
            // Mark as read in Firestore (SyncManager will pull the read update later)
            msgs.filter { it.senderMobile == contact.mobile && !it.read }.forEach { m ->
                db.collection("pvt_msg_$chatId").document(m.id).update("read", true, "delivered", true)
            }
        }
    }

    LaunchedEffect(messages) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.senderMobile == contact.mobile) {
            val replies = com.rasel.rasgram.utils.AIAssistant.generateSmartReplies(messages)
            smartReplies = replies
        } else {
            smartReplies = emptyList()
        }
    }

    // Contact live status
    LaunchedEffect(contact.mobile) {
        db.collection("chat_users").document(contact.mobile).addSnapshotListener { snap, _ ->
            snap?.data?.let { d ->
                liveContact = liveContact.copy(
                    name = d["name"] as? String ?: liveContact.name,
                    avatarUrl = d["avatarUrl"] as? String ?: liveContact.avatarUrl,
                    typingTo = d["typingTo"] as? String,
                    lastActive = d["lastActive"] as? Long ?: 0
                )
            }
        }
    }

    // Auto scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) { delay(1000); recordingSeconds++ }
        }
    }

    var typingJob by remember { mutableStateOf<Job?>(null) }

    fun sendText() {
        val text = inputText.trim()
        if (text.isBlank()) return
        sendMessage(
            db, chatId, currentUser.mobile, contact.mobile, text, null, null, null,
            replyToMessage?.id, replyToMessage?.text, replyToMessage?.senderMobile
        )
        inputText = ""
        replyToMessage = null
        typingJob?.cancel()
        scope.launch { db.collection("chat_users").document(currentUser.mobile).update("typingTo", null) }
    }

    BackHandler(enabled = selectedMessages.isNotEmpty()) {
        selectedMessages = emptySet()
    }

    Column(modifier = Modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        // Header
        if (selectedMessages.isNotEmpty()) {
            SelectionHeader(
                count = selectedMessages.size,
                onClose = { selectedMessages = emptySet() },
                onDelete = {
                    scope.launch {
                        selectedMessages.forEach { id ->
                            db.collection("pvt_msg_$chatId").document(id).update("isDeleted", true, "text", "")
                        }
                        selectedMessages = emptySet()
                    }
                },
                onForward = { showForwardDialog = true },
                onStar = {
                    scope.launch {
                        selectedMessages.forEach { id ->
                            db.collection("pvt_msg_$chatId").document(id).update("isStarred", true)
                        }
                        selectedMessages = emptySet()
                    }
                },
                onCopy = {
                    val text = messages.filter { it.id in selectedMessages }.joinToString("\n") { it.text }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("messages", text))
                    selectedMessages = emptySet()
                }
            )
        } else {
            ChatHeader(
                contact = liveContact,
                currentUserMobile = currentUser.mobile,
                isCompact = isCompact,
                onBack = onBack,
                onCallClick = onCallClick,
                onClearChat = {
                    scope.launch {
                        db.collection("pvt_msg_$chatId").get().await().documents.forEach { it.reference.delete() }
                    }
                },
                onSummarizeChat = { 
                    showSummarizeDialog = true 
                    isSummarizing = true
                    scope.launch {
                        chatSummary = com.rasel.rasgram.utils.AIAssistant.summarizeChat(messages)
                        isSummarizing = false
                    }
                },
                onViewContact = { /* View contact */ }
            )
        }

        // Messages area
        Box(modifier = Modifier.weight(1f)) {
            // Chat wallpaper pattern (subtle)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // subtle background pattern
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    EncryptionNotice()
                }

                var lastDateString = ""
                messages.forEach { message ->
                    val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(message.timestamp))
                    if (dateStr != lastDateString) {
                        lastDateString = dateStr
                        item(key = "date_$dateStr") {
                            DateDivider(dateStr)
                        }
                    }
                    item(key = message.id) {
                        MessageBubble(
                            message = message,
                            isMe = message.senderMobile == currentUser.mobile,
                            isSelected = message.id in selectedMessages,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedMessages = selectedMessages + message.id
                            },
                            onClick = {
                                if (selectedMessages.isNotEmpty()) {
                                    selectedMessages = if (message.id in selectedMessages)
                                        selectedMessages - message.id else selectedMessages + message.id
                                }
                            },
                            onReact = { reaction ->
                                scope.launch {
                                    db.collection("pvt_msg_$chatId").document(message.id)
                                        .update("reaction", if (message.reaction == reaction) null else reaction)
                                }
                            },
                            onReply = { replyToMessage = message },
                            onDelete = {
                                scope.launch {
                                    db.collection("pvt_msg_$chatId").document(message.id)
                                        .update("isDeleted", true, "text", "")
                                }
                            },
                            onStar = {
                                scope.launch {
                                    db.collection("pvt_msg_$chatId").document(message.id)
                                        .update("isStarred", !message.isStarred)
                                }
                            },
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", message.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Scroll-to-bottom FAB - inside Box(weight(1f)) BoxScope, using wrapContentSize
            val showScrollFab by remember { derivedStateOf { listState.firstVisibleItemIndex < messages.size - 5 && messages.size > 10 } }
            if (showScrollFab) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    FloatingActionButton(
                        onClick = { scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) } },
                        modifier = Modifier.size(40.dp),
                        containerColor = RasGramTheme.DarkPanel,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = RasGramTheme.TextMuted)
                    }
                }
            }
        }

        // Upload progress
        if (isUploading) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = RasGramTheme.Green,
                trackColor = RasGramTheme.DarkPanel
            )
        }

        // Reply preview
        replyToMessage?.let { reply ->
            ReplyPreview(
                message = reply,
                currentUserMobile = currentUser.mobile,
                onDismiss = { replyToMessage = null }
            )
        }

        // Smart Replies
        if (smartReplies.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(smartReplies) { reply ->
                    Surface(
                        color = RasGramTheme.GreenDark.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, RasGramTheme.Green.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { 
                            inputText = reply
                            smartReplies = emptyList()
                        }
                    ) {
                        Text(
                            text = reply,
                            color = RasGramTheme.Green,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Input area
        ChatInputBar(
            inputText = inputText,
            onTextChange = { text ->
                inputText = text
                if (text.isNotEmpty()) {
                    typingJob?.cancel()
                    typingJob = scope.launch {
                        db.collection("chat_users").document(currentUser.mobile).update("typingTo", contact.mobile)
                        delay(TYPING_DEBOUNCE_MS)
                        db.collection("chat_users").document(currentUser.mobile).update("typingTo", null)
                    }
                }
            },
            onSend = { sendText() },
            onAttachClick = { showAttachMenu = true },
            isRecording = isRecording,
            recordingSeconds = recordingSeconds,
            onMicPress = {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    return@ChatInputBar
                }
                if (!isRecording) {
                    try {
                        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                        recordingFile = file
                        val recorder = MediaRecorder()
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setOutputFile(file.absolutePath)
                        recorder.prepare()
                        recorder.start()
                        mediaRecorder = recorder
                        isRecording = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMicRelease = {
                if (isRecording) {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                        mediaRecorder = null
                        isRecording = false
                        val file = recordingFile ?: return@ChatInputBar
                        if (file.exists() && file.length() > 0) {
                            isUploading = true
                            scope.launch {
                                val (url, fileName, _) = uploadToCloudinary(context, file.toUri()) { prog -> uploadProgress = prog }
                                if (url != null) {
                                    sendMessage(db, chatId, currentUser.mobile, contact.mobile, "", url, fileName ?: "voice.m4a", "audio/mp4", null, null, null, recordingSeconds)
                                }
                                isUploading = false
                                uploadProgress = 0f
                                file.delete()
                            }
                        }
                    } catch (e: Exception) {
                        isRecording = false
                    }
                }
            },
            onMicCancel = {
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                recordingFile?.delete()
            }
        )
    }

    // Attachment menu
    if (showAttachMenu) {
        AttachmentMenuSheet(
            onDismiss = { showAttachMenu = false },
            onImageVideo = { imageVideoLauncher.launch(arrayOf("image/*", "video/*")); showAttachMenu = false },
            onDocument = { docLauncher.launch(arrayOf("*/*")); showAttachMenu = false },
            onAudio = { docLauncher.launch(arrayOf("audio/*")); showAttachMenu = false }
        )
    }
}


@Composable
fun DateDivider(dateString: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = RasGramTheme.Border, thickness = 0.5.dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF182229),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(dateString, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = RasGramTheme.Border, thickness = 0.5.dp)
    }
}

@Composable
fun ChatHeader(
    contact: User,
    currentUserMobile: String,
    isCompact: Boolean,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit,
    onClearChat: () -> Unit,
    onSummarizeChat: () -> Unit,
    onViewContact: () -> Unit
) {
    val isOnline = contact.lastActive > System.currentTimeMillis() - ONLINE_THRESHOLD_MS
    var showMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompact) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = RasGramTheme.TextPrimary)
                }
            }

            Row(
                modifier = Modifier.weight(1f).clickable { onViewContact() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    AsyncImage(
                        model = contact.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${contact.name.replace(" ", "+")}&background=008069&color=fff&bold=true&size=80" },
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    if (isOnline) {
                        Box(modifier = Modifier.size(10.dp).align(Alignment.BottomEnd).border(2.dp, RasGramTheme.DarkPanel, CircleShape).background(RasGramTheme.OnlineGreen, CircleShape))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(contact.name, style = MaterialTheme.typography.bodyLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            contact.typingTo == currentUserMobile -> "typing..."
                            isOnline -> "online"
                            else -> "last seen ${formatLastSeen(System.currentTimeMillis() - contact.lastActive)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (contact.typingTo == currentUserMobile || isOnline) RasGramTheme.Green else RasGramTheme.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = { onCallClick("video") }) {
                Icon(Icons.Default.VideoCall, null, tint = RasGramTheme.TextMuted)
            }
            IconButton(onClick = { onCallClick("audio") }) {
                Icon(Icons.Default.Call, null, tint = RasGramTheme.TextMuted)
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
                        text = { Text("View Contact", color = RasGramTheme.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = RasGramTheme.TextMuted) },
                        onClick = { onViewContact(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Summarize Chat (AI)", color = RasGramTheme.Green) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = RasGramTheme.Green) },
                        onClick = { onSummarizeChat(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Chat", color = RasGramTheme.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = RasGramTheme.Red) },
                        onClick = { onClearChat(); showMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onStar: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = RasGramTheme.TextPrimary)
            }
            Text("$count selected", style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onStar) { Icon(Icons.Default.Star, null, tint = RasGramTheme.Yellow) }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, null, tint = RasGramTheme.TextMuted) }
            IconButton(onClick = onForward) { Icon(Icons.Default.Forward, null, tint = RasGramTheme.TextMuted) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = RasGramTheme.Red) }
        }
    }
}

@Composable
fun ReplyPreview(message: Message, currentUserMobile: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RasGramTheme.DarkPanel
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(36.dp).background(RasGramTheme.Green, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (message.senderMobile == currentUserMobile) "You" else "Contact",
                    color = RasGramTheme.Green,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (message.isDeleted) "This message was deleted" else if (message.text.isNotEmpty()) message.text else getFileTypePreview(message),
                    color = RasGramTheme.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, null, tint = RasGramTheme.TextMuted)
            }
        }
    }

    if (showSummarizeDialog) {
        Dialog(onDismissRequest = { showSummarizeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = RasGramTheme.DarkPanel,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Chat Summary",
                        style = MaterialTheme.typography.titleLarge,
                        color = RasGramTheme.Green,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (isSummarizing) {
                        CircularProgressIndicator(color = RasGramTheme.Green, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Text(
                            text = chatSummary ?: "Failed to generate summary.",
                            color = RasGramTheme.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showSummarizeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    isRecording: Boolean,
    recordingSeconds: Int,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onMicCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkBackground) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (isRecording) {
                // Recording UI
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = RasGramTheme.InputBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RecordingWaveAnimation()
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            formatTime(recordingSeconds),
                            color = RasGramTheme.Red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("< Slide to cancel", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = onMicRelease,
                    modifier = Modifier.size(48.dp),
                    containerColor = RasGramTheme.Green,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp)
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else {
                // Emoji + Attach
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = RasGramTheme.InputBg
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { /* emoji picker */ }) {
                            Icon(Icons.Default.EmojiEmotions, null, tint = RasGramTheme.TextMuted)
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp),
                            placeholder = { Text("Message", color = RasGramTheme.TextMuted) },
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = RasGramTheme.TextPrimary,
                                unfocusedTextColor = RasGramTheme.TextPrimary,
                                cursorColor = RasGramTheme.Green
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() })
                        )
                        IconButton(onClick = onAttachClick) {
                            Icon(Icons.Default.AttachFile, null, tint = RasGramTheme.TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                AnimatedContent(
                    targetState = inputText.isNotEmpty(),
                    transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                    label = "SendMicButton"
                ) { hasText ->
                    FloatingActionButton(
                        onClick = if (hasText) onSend else onMicPress,
                        modifier = Modifier.size(48.dp),
                        containerColor = RasGramTheme.Green,
                        elevation = FloatingActionButtonDefaults.elevation(2.dp)
                    ) {
                        Icon(
                            if (hasText) Icons.Default.Send else Icons.Default.Mic,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val bars = remember { (1..5).map { it } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.size(8.dp).background(RasGramTheme.Red, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        bars.forEachIndexed { i, _ ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 80),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(modifier = Modifier.width(3.dp).height((16 * scale).dp).background(RasGramTheme.Green, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun AttachmentMenuSheet(
    onDismiss: () -> Unit,
    onImageVideo: () -> Unit,
    onDocument: () -> Unit,
    onAudio: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = RasGramTheme.DarkPanel
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Share", style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachOption(Icons.Default.Image, "Photos & Videos", RasGramTheme.Orange, onImageVideo)
                    AttachOption(Icons.Default.InsertDriveFile, "Document", Color(0xFF6C63FF), onDocument)
                    AttachOption(Icons.Default.AudioFile, "Audio", Color(0xFF00BFA5), onAudio)
                    AttachOption(Icons.Default.Camera, "Camera", RasGramTheme.Green, onDismiss)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachOption(Icons.Default.LocationOn, "Location", RasGramTheme.Red, onDismiss)
                    AttachOption(Icons.Default.ContactPage, "Contact", Color(0xFF2196F3), onDismiss)
                    AttachOption(Icons.Default.Poll, "Poll", Color(0xFFFF9800), onDismiss)
                    AttachOption(Icons.Default.Gif, "GIF", Color(0xFF9C27B0), onDismiss)
                }
            }
        }
    }
}

@Composable
fun AttachOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), modifier = Modifier.size(56.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.padding(14.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

