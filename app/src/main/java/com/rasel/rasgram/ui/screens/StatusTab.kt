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

// ==================== STATUS TAB ====================
@Composable
fun StatusTab(currentUser: User, onStatusClick: (List<Status>) -> Unit, modifier: Modifier = Modifier) {
    val db = remember { FirebaseFirestore.getInstance() }
    var statuses by remember { mutableStateOf<List<Status>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                val (url, _, fileType) = uploadToCloudinary(context, it) { }
                if (url != null) {
                    val status = hashMapOf(
                        "userMobile" to currentUser.mobile,
                        "userName" to currentUser.name,
                        "userAvatar" to currentUser.avatarUrl,
                        "mediaUrl" to url,
                        "mediaType" to if (fileType?.startsWith("video") == true) "video" else "image",
                        "caption" to "",
                        "timestamp" to System.currentTimeMillis(),
                        "viewedBy" to listOf<String>(),
                        "expiresAt" to (System.currentTimeMillis() + 86400_000)
                    )
                    db.collection("statuses").add(status).await()
                    Toast.makeText(context, "Status posted!", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        db.collection("statuses")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .orderBy("expiresAt", Query.Direction.DESCENDING)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                snap?.documents?.mapNotNull { doc ->
                    doc.data?.let { d ->
                        Status(
                            id = doc.id,
                            userMobile = d["userMobile"] as? String ?: "",
                            userName = d["userName"] as? String ?: "",
                            userAvatar = d["userAvatar"] as? String ?: "",
                            mediaUrl = d["mediaUrl"] as? String ?: "",
                            mediaType = d["mediaType"] as? String ?: "image",
                            caption = d["caption"] as? String ?: "",
                            timestamp = d["timestamp"] as? Long ?: 0,
                            viewedBy = (d["viewedBy"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            expiresAt = d["expiresAt"] as? Long ?: 0
                        )
                    }
                }?.also { statuses = it }
            }
    }

    Column(modifier = modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { imageLauncher.launch(arrayOf("image/*", "video/*")) }) {
                    Icon(Icons.Default.Edit, null, tint = RasGramTheme.TextMuted)
                }
            }
        }

        val myStatuses = statuses.filter { it.userMobile == currentUser.mobile }
        val othersStatuses = statuses.filter { it.userMobile != currentUser.mobile }
            .groupBy { it.userMobile }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // My status
            item {
                StatusItem(
                    avatarUrl = currentUser.avatarUrl,
                    name = "My Status",
                    subtitle = if (isUploading) "Uploading..." else "Tap to add status update",
                    hasNewStatus = false,
                    onClick = { 
                        if (myStatuses.isNotEmpty()) onStatusClick(myStatuses) 
                        else imageLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    isMyStatus = true
                )
                HorizontalDivider(color = RasGramTheme.DividerColor, modifier = Modifier.padding(start = 80.dp))
            }

            if (othersStatuses.isNotEmpty()) {
                item {
                    Text("Recent Updates", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                items(othersStatuses.entries.toList()) { (mobile, userStatuses) ->
                    val first = userStatuses.first()
                    val viewed = userStatuses.all { currentUser.mobile in it.viewedBy }
                    StatusItem(
                        avatarUrl = first.userAvatar,
                        name = first.userName,
                        subtitle = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(first.timestamp)),
                        hasNewStatus = !viewed,
                        onClick = { onStatusClick(userStatuses) },
                        isMyStatus = false
                    )
                    HorizontalDivider(color = RasGramTheme.DividerColor, modifier = Modifier.padding(start = 80.dp))
                }
            } else {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Circle, null, modifier = Modifier.size(64.dp), tint = RasGramTheme.TextMuted.copy(0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No recent updates", color = RasGramTheme.TextMuted)
                        Text("Status updates from your contacts will appear here.", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusItem(avatarUrl: String, name: String, subtitle: String, hasNewStatus: Boolean, onClick: () -> Unit, isMyStatus: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier = Modifier.fillMaxSize().border(
                    width = 2.5.dp,
                    color = when {
                        isMyStatus -> RasGramTheme.Green
                        hasNewStatus -> RasGramTheme.Green
                        else -> RasGramTheme.TextMuted.copy(0.3f)
                    },
                    shape = CircleShape
                ).padding(3.dp)
            ) {
                AsyncImage(
                    model = avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=008069&color=fff" },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            if (isMyStatus) {
                Surface(
                    modifier = Modifier.size(20.dp).align(Alignment.BottomEnd),
                    shape = CircleShape,
                    color = RasGramTheme.Green,
                    border = BorderStroke(2.dp, RasGramTheme.DarkBackground)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextMuted)
        }
    }
}

