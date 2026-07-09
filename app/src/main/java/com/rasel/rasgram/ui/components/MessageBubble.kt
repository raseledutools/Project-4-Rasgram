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

// ==================== MESSAGE BUBBLE ====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    isSelected: Boolean,
    senderName: String? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onStar: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    val bubbleColor = if (isMe) RasGramTheme.BubbleOut else RasGramTheme.BubbleIn
    val alignment = if (isMe) Alignment.End else Alignment.Start
    var showContextMenu by remember { mutableStateOf(false) }

    val selectionBg = if (isSelected) RasGramTheme.Green.copy(alpha = 0.15f) else Color.Transparent

    SwipeToReplyWrapper(onReply = onReply) {
        Column(
            modifier = Modifier.fillMaxWidth().background(selectionBg).padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = alignment
        ) {
            if (message.isCallLog) {
                CallLogBubble(message = message)
                return@Column
            }

            if (message.isDeleted) {
                DeletedMessageBubble(isMe = isMe, timeString = message.timeString)
                return@Column
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isMe) { Spacer(modifier = Modifier.width(4.dp)) }

            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = onClick, onLongClick = { showContextMenu = true; onLongClick() }),
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = bubbleColor,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(0.dp)) {
                    // Sender Name (For Groups)
                    if (!isMe && senderName != null) {
                        Text(
                            senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = RasGramTheme.Yellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp)
                        )
                    }
                    
                    // Reply preview inside bubble
                    message.replyToId?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.2f)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.width(2.dp).height(28.dp).background(RasGramTheme.Green, RoundedCornerShape(1.dp)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(message.replyToSender ?: "Unknown", color = RasGramTheme.Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(message.replyToText ?: "", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    // Forward badge
                    if (message.isForwarded) {
                        Row(modifier = Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forward, null, modifier = Modifier.size(12.dp), tint = RasGramTheme.TextMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Forwarded", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }

                    // File content
                    message.fileUrl?.let { url ->
                        when {
                            message.fileType?.startsWith("image/") == true -> ImageMessageContent(url = url, context = context)
                            message.fileType?.startsWith("video/") == true -> VideoMessageContent(url = url, fileName = message.fileName, fileType = message.fileType, context = context)
                            message.fileType?.startsWith("audio/") == true -> AudioMessageContent(url = url, fileName = message.fileName, duration = message.duration)
                            else -> DocumentMessageContent(url = url, fileName = message.fileName, fileType = message.fileType, fileSize = message.fileSizeBytes, context = context)
                        }
                    }

                    // Text
                    if (message.text.isNotEmpty()) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RasGramTheme.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (message.fileUrl != null) 4.dp else 8.dp)
                        )
                    }

                    // Time + ticks row
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp, top = if (message.text.isEmpty() && message.fileUrl != null) 4.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isStarred) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(10.dp), tint = RasGramTheme.StarColor)
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(message.timeString, style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted.copy(0.8f), fontSize = 10.sp)
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when {
                                    message.isPending -> Icons.Default.Schedule
                                    message.read -> Icons.Default.DoneAll
                                    message.delivered -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = when {
                                    message.read -> RasGramTheme.BlueTick
                                    else -> RasGramTheme.TextMuted
                                }
                            )
                        }
                    }
                }
            }

            if (isMe) { Spacer(modifier = Modifier.width(4.dp)) }
        }

        // Reaction bubble
        message.reaction?.let { emoji ->
            Surface(
                modifier = Modifier.offset(y = (-6).dp).padding(horizontal = if (isMe) 8.dp else 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = RasGramTheme.DarkPanel,
                border = BorderStroke(1.dp, RasGramTheme.Border)
            ) {
                Text(emoji, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 14.sp)
            }
        }
    }
    } // Close SwipeToReplyWrapper

    // Context menu
    if (showContextMenu) {
        MessageContextMenu(
            message = message,
            isMe = isMe,
            onDismiss = { showContextMenu = false },
            onReact = { emoji -> onReact(emoji); showContextMenu = false },
            onReply = { onReply(); showContextMenu = false },
            onDelete = { onDelete(); showContextMenu = false },
            onCopy = { onCopy(); showContextMenu = false },
            onStar = { onStar(); showContextMenu = false },
            onForward = { showContextMenu = false }
        )
    }
}

@Composable
fun DeletedMessageBubble(isMe: Boolean, timeString: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMe) RasGramTheme.BubbleOut.copy(alpha = 0.6f) else RasGramTheme.BubbleIn.copy(alpha = 0.6f)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = RasGramTheme.TextMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Text("This message was deleted", style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Spacer(modifier = Modifier.width(8.dp))
                Text(timeString, style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CallLogBubble(message: Message) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF182229),
            border = BorderStroke(0.5.dp, RasGramTheme.Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (message.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                    null, modifier = Modifier.size(14.dp),
                    tint = if (message.callStatus == "missed") RasGramTheme.Red else RasGramTheme.Green
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(message.text, style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(message.timeString, style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ImageMessageContent(url: String, context: Context) {
    var showFullScreen by remember { mutableStateOf(false) }
    AsyncImage(
        model = url,
        contentDescription = "Image",
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)).clickable { showFullScreen = true },
        contentScale = ContentScale.Crop
    )
    if (showFullScreen) {
        Dialog(onDismissRequest = { showFullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { showFullScreen = false }, contentAlignment = Alignment.Center) {
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                IconButton(onClick = { showFullScreen = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                IconButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun VideoMessageContent(url: String, fileName: String?, fileType: String?, context: Context) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)).clickable {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.setDataAndType(url.toUri(), fileType)
            context.startActivity(intent)
        },
        color = Color.Black.copy(0.6f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(60.dp))
            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(4.dp), color = Color.Black.copy(0.6f)) {
                Text(fileName ?: "Video", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AudioMessageContent(url: String, fileName: String?, duration: Int) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    var durationMs by remember { mutableIntStateOf(if (duration > 0) duration * 1000 else 0) }
    val scope = rememberCoroutineScope()

    DisposableEffect(url) {
        onDispose {
            try { if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = {
                if (!isPlaying) {
                    try {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(url)
                        mediaPlayer.prepareAsync()
                        mediaPlayer.setOnPreparedListener { mp ->
                            mp.start()
                            durationMs = mp.duration
                            isPlaying = true
                            scope.launch {
                                while (mp.isPlaying) {
                                    progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
                                    delay(100)
                                }
                                progress = 0f
                                isPlaying = false
                            }
                        }
                        mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f }
                    } catch (_: Exception) {}
                } else {
                    mediaPlayer.pause(); isPlaying = false
                }
            },
            modifier = Modifier.size(40.dp),
            containerColor = RasGramTheme.Green,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = RasGramTheme.Green,
                trackColor = RasGramTheme.TextMuted.copy(0.3f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fileName ?: "Voice message", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (durationMs > 0) Text(formatTime(durationMs / 1000), color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DocumentMessageContent(url: String, fileName: String?, fileType: String?, fileSize: Long, context: Context) {
    val icon = when {
        fileType?.contains("pdf") == true -> Icons.Default.PictureAsPdf
        fileType?.contains("word") == true || fileType?.contains("document") == true -> Icons.Default.Description
        fileType?.contains("sheet") == true || fileType?.contains("excel") == true -> Icons.Default.TableChart
        fileType?.contains("presentation") == true || fileType?.contains("powerpoint") == true -> Icons.Default.Slideshow
        fileType?.contains("zip") == true || fileType?.contains("archive") == true -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
    val iconColor = when {
        fileType?.contains("pdf") == true -> RasGramTheme.Red
        fileType?.contains("word") == true -> Color(0xFF2196F3)
        fileType?.contains("sheet") == true || fileType?.contains("excel") == true -> RasGramTheme.Green
        else -> RasGramTheme.TextMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = iconColor.copy(0.1f), modifier = Modifier.size(46.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.padding(10.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(fileName ?: "Document", style = MaterialTheme.typography.bodyMedium, color = RasGramTheme.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (fileSize > 0) {
                Text(formatFileSize(fileSize), style = MaterialTheme.typography.labelSmall, color = RasGramTheme.TextMuted)
            }
        }
        Icon(Icons.Default.Download, null, tint = RasGramTheme.TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MessageContextMenu(
    message: Message,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onForward: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Emoji reactions
            Surface(shape = RoundedCornerShape(24.dp), color = RasGramTheme.DarkPanel, modifier = Modifier.padding(bottom = 8.dp)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        Text(
                            emoji,
                            modifier = Modifier.clickable { onReact(emoji) }.padding(4.dp),
                            fontSize = 26.sp
                        )
                    }
                }
            }

            // Action menu
            Surface(shape = RoundedCornerShape(16.dp), color = RasGramTheme.DarkPanel, modifier = Modifier.fillMaxWidth()) {
                Column {
                    val actions = buildList {
                        add(Triple(Icons.Default.Reply, "Reply", onReply))
                        if (message.text.isNotEmpty()) add(Triple(Icons.Default.ContentCopy, "Copy Text", onCopy))
                        add(Triple(Icons.Default.Forward, "Forward", onForward))
                        add(Triple(Icons.Default.Star, if (message.isStarred) "Unstar" else "Star", onStar))
                        if (isMe) add(Triple(Icons.Default.Delete, "Delete", onDelete))
                    }
                    actions.forEachIndexed { i, (icon, label, action) ->
                        val isLast = i == actions.size - 1
                        ListItem(
                            headlineContent = { Text(label, color = if (label == "Delete") RasGramTheme.Red else RasGramTheme.TextPrimary) },
                            leadingContent = { Icon(icon, null, tint = if (label == "Delete") RasGramTheme.Red else RasGramTheme.TextMuted) },
                            modifier = Modifier.clickable { action() },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (!isLast) HorizontalDivider(color = RasGramTheme.DividerColor, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(shape = RoundedCornerShape(16.dp), color = RasGramTheme.DarkPanel, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Cancel", color = RasGramTheme.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.clickable { onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

