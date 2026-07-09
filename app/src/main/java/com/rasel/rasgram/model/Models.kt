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
import androidx.room.Entity
import androidx.room.PrimaryKey

// ==================== DATA CLASSES ====================
@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String = "",
    val name: String = "",
    val mobile: String = "",
    val avatarUrl: String = "",
    val lastActive: Long = 0,
    val typingTo: String? = null,
    val statusVisible: Boolean = true,
    val about: String = "Hey there! I am using RasGram.",
    val fcmToken: String = "",
    val isBlocked: Boolean = false,
    val disappearingTimer: Long = 0
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = "",
    val text: String = "",
    val senderMobile: String = "",
    val receiverMobile: String = "",
    val timestamp: Long = 0,
    val timeString: String = "",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileType: String? = null,
    val fileSizeBytes: Long = 0,
    val thumbnailUrl: String? = null,
    val reaction: String? = null,
    val read: Boolean = false,
    val delivered: Boolean = false,
    val isCallLog: Boolean = false,
    val callStatus: String? = null,
    val callType: String? = null,
    val isPending: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val isDeleted: Boolean = false,
    val isForwarded: Boolean = false,
    val isStarred: Boolean = false,
    val duration: Int = 0,
    val waveform: List<Float> = emptyList()
)

@Entity(tableName = "statuses")
data class Status(
    @PrimaryKey val id: String = "",
    val userMobile: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image",
    val caption: String = "",
    val timestamp: Long = 0,
    val viewedBy: List<String> = emptyList(),
    val expiresAt: Long = 0
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

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val members: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val typingUsers: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0,
    val disappearingTimer: Long = 0
)

data class ChatPreview(
    val user: User? = null,
    val group: Group? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false
)

