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

// ==================== SETTINGS DIALOG ====================
@Composable
fun SettingsDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentUser.name) }
    var about by remember { mutableStateOf(currentUser.about) }
    var isUploading by remember { mutableStateOf(false) }
    var avatarUrl by remember { mutableStateOf(currentUser.avatarUrl) }
    var disappearingTimer by remember { mutableLongStateOf(currentUser.disappearingTimer) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            isUploading = true
            scope.launch {
                val (url, _, _) = uploadToCloudinary(context, it) { }
                if (url != null) {
                    avatarUrl = url
                    db.collection("chat_users").document(currentUser.mobile).update("avatarUrl", url)
                    Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), shape = RoundedCornerShape(20.dp), color = RasGramTheme.DarkPanel) {
            Column {
                // Header with gradient
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp).background(
                        Brush.verticalGradient(listOf(RasGramTheme.GreenDark, RasGramTheme.DarkPanel))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${currentUser.name.replace(" ", "+")}&background=008069&color=fff&size=200" },
                            contentDescription = null,
                            modifier = Modifier.size(96.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        FloatingActionButton(onClick = { imageLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.size(34.dp), containerColor = RasGramTheme.Green) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    // Tabs
                    Row(modifier = Modifier.fillMaxWidth().background(RasGramTheme.DarkBackground, RoundedCornerShape(10.dp)).padding(4.dp)) {
                        listOf("Profile", "Privacy", "Notifications").forEachIndexed { i, label ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable { selectedTab = i },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedTab == i) RasGramTheme.Green else Color.Transparent
                            ) {
                                Text(label, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, color = if (selectedTab == i) Color.Black else RasGramTheme.TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when (selectedTab) {
                        0 -> {
                            // Profile tab
                            OutlinedTextField(value = name, onValueChange = { if (it.length <= 25) name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), trailingIcon = { Text("${name.length}/25", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) })
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = about, onValueChange = { if (it.length <= 139) about = it }, label = { Text("About") }, modifier = Modifier.fillMaxWidth(), colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), maxLines = 2, trailingIcon = { Text("${about.length}/139", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) })
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = RasGramTheme.Green, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("+${currentUser.mobile}", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        1 -> {
                            // FIX #2: Icons.Default.ProfileBadge doesn't exist — replaced with AccountCircle
                            SettingsToggleRow(Icons.Default.Visibility, "Show Last Seen", true) { }
                            SettingsToggleRow(Icons.Default.Timer, "Disappearing Messages (24h)", disappearingTimer > 0) {
                                disappearingTimer = if (disappearingTimer > 0) 0L else 24 * 60 * 60 * 1000L
                            }
                            SettingsToggleRow(Icons.Default.DoneAll, "Show Read Receipts", true) { }
                            SettingsToggleRow(Icons.Default.AccountCircle, "Show Profile Photo", true) { }
                            SettingsToggleRow(Icons.Default.Circle, "Show Status", true) { }
                        }
                        2 -> {
                            SettingsToggleRow(Icons.Default.Notifications, "Message Notifications", true) { }
                            SettingsToggleRow(Icons.Default.VolumeUp, "Notification Sound", true) { }
                            SettingsToggleRow(Icons.Default.Vibration, "Vibration", true) { }
                            SettingsToggleRow(Icons.Default.Groups, "Group Notifications", true) { }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, RasGramTheme.Border), colors = ButtonDefaults.outlinedButtonColors(contentColor = RasGramTheme.TextMuted)) { Text("Cancel") }
                        Button(onClick = {
                            scope.launch {
                                db.collection("chat_users").document(currentUser.mobile).update("name", name, "about", about, "disappearingTimer", disappearingTimer)
                            }
                            onSave(currentUser.copy(name = name, about = about, avatarUrl = avatarUrl, disappearingTimer = disappearingTimer))
                        }, modifier = Modifier.weight(2f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)) {
                            Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, initialValue: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RasGramTheme.Green, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = RasGramTheme.TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RasGramTheme.Green, uncheckedTrackColor = RasGramTheme.TextMuted.copy(0.3f)))
    }
}

