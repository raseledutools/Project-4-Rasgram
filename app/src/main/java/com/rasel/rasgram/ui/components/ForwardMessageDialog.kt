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

// ==================== FORWARD MESSAGE DIALOG ====================
@Composable
fun ForwardMessageDialog(
    currentUser: User,
    messages: List<Message>,
    onDismiss: () -> Unit,
    onForwardComplete: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<User>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Fetch contacts
        db.collection("chat_users").get().addOnSuccessListener { snap ->
            contacts = snap.documents.mapNotNull { doc ->
                doc.data?.let { d ->
                    User(
                        uid = doc.id, name = d["name"] as? String ?: "",
                        mobile = d["mobile"] as? String ?: "", avatarUrl = d["avatarUrl"] as? String ?: ""
                    )
                }
            }.filter { it.mobile != currentUser.mobile }
        }
        
        // Fetch groups
        db.collection("groups").whereArrayContains("members", currentUser.mobile).get().addOnSuccessListener { snap ->
            groups = snap.documents.mapNotNull { doc ->
                doc.data?.let { d ->
                    Group(
                        id = doc.id, name = d["name"] as? String ?: "",
                        avatarUrl = d["avatarUrl"] as? String ?: "", description = d["description"] as? String ?: "",
                        members = (d["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        admins = (d["admins"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        createdBy = d["createdBy"] as? String ?: "",
                        createdAt = d["createdAt"] as? Long ?: 0
                    )
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 500.dp), shape = RoundedCornerShape(20.dp), color = RasGramTheme.DarkPanel) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Forward to...", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSending) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RasGramTheme.Green)
                    }
                } else {
                    LazyColumn {
                        // Contacts
                        if (contacts.isNotEmpty()) {
                            item { Text("Contacts", color = RasGramTheme.Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(contacts, key = { "contact_${it.mobile}" }) { contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        isSending = true
                                        scope.launch {
                                            val chatId = if (currentUser.mobile < contact.mobile) "${currentUser.mobile}_${contact.mobile}" else "${contact.mobile}_${currentUser.mobile}"
                                            messages.forEach { msg ->
                                                val fwdText = "[Forwarded]\n" + (msg.text.ifEmpty { "Media/Voice Note" })
                                                sendMessage(db, chatId, currentUser.mobile, contact.mobile, fwdText, msg.fileUrl, msg.fileName, msg.fileType, null, null, null, msg.duration)
                                                delay(100) // slight delay to ensure order
                                            }
                                            isSending = false
                                            onForwardComplete()
                                        }
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = contact.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${contact.name.replace(" ", "+")}&background=008069&color=fff" }, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(contact.name, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        
                        // Groups
                        if (groups.isNotEmpty()) {
                            item { Text("Groups", color = RasGramTheme.Green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(groups, key = { "group_${it.id}" }) { group ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        isSending = true
                                        scope.launch {
                                            messages.forEach { msg ->
                                                val fwdText = "[Forwarded]\n" + (msg.text.ifEmpty { "Media/Voice Note" })
                                                val encryptedText = AESCrypto.encrypt(group.id, fwdText)
                                                val now = System.currentTimeMillis()
                                                val msgMap = hashMapOf(
                                                    "text" to encryptedText, "senderMobile" to currentUser.mobile,
                                                    "fileUrl" to msg.fileUrl, "fileName" to msg.fileName,
                                                    "fileType" to msg.fileType, "duration" to msg.duration,
                                                    "timestamp" to now, "timeString" to java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))
                                                )
                                                db.collection("groups").document(group.id).collection("messages").add(msgMap)
                                                db.collection("groups").document(group.id).update("lastMessageTime", now)
                                                delay(100)
                                            }
                                            isSending = false
                                            onForwardComplete()
                                        }
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = group.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${group.name.replace(" ", "+")}&background=005C4B&color=fff&bold=true" }, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(group.name, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

