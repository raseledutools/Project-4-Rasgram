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

// ==================== GROUPS TAB ====================
@Composable
fun GroupsTab(currentUser: User, onGroupSelect: (Group) -> Unit, modifier: Modifier = Modifier) {
    val db = remember { FirebaseFirestore.getInstance() }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var showCreateGroup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("groups")
            .whereArrayContains("members", currentUser.mobile)
            .addSnapshotListener { snap, _ ->
                snap?.documents?.mapNotNull { doc ->
                    doc.data?.let { d ->
                        Group(
                            id = doc.id,
                            name = d["name"] as? String ?: "",
                            avatarUrl = d["avatarUrl"] as? String ?: "",
                            description = d["description"] as? String ?: "",
                            members = (d["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            admins = (d["admins"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            typingUsers = (d["typingUsers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            createdBy = d["createdBy"] as? String ?: "",
                            createdAt = d["createdAt"] as? Long ?: 0
                        )
                    }
                }?.also { groups = it }
            }
    }

    Column(modifier = modifier.fillMaxSize().background(RasGramTheme.DarkBackground)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = RasGramTheme.DarkPanel) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", style = MaterialTheme.typography.titleLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreateGroup = true }) {
                    Icon(Icons.Default.GroupAdd, null, tint = RasGramTheme.TextMuted)
                }
            }
        }

        if (groups.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Groups, null, modifier = Modifier.size(80.dp), tint = RasGramTheme.TextMuted.copy(0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Groups Yet", style = MaterialTheme.typography.titleMedium, color = RasGramTheme.TextMuted)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showCreateGroup = true }, colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)) {
                    Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Group", color = Color.Black)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups, key = { it.id }) { group ->
                    GroupItem(group = group, onClick = { onGroupSelect(group) })
                    HorizontalDivider(color = RasGramTheme.DividerColor, modifier = Modifier.padding(start = 80.dp))
                }
            }
        }
    }

    if (showCreateGroup) {
        NewGroupDialog(onDismiss = { showCreateGroup = false }, currentUser = currentUser)
    }
}

@Composable
fun GroupItem(group: Group, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = group.avatarUrl.ifEmpty { "https://ui-avatars.com/api/?name=${group.name.replace(" ", "+")}&background=005C4B&color=fff&bold=true" },
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.bodyLarge, color = RasGramTheme.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("${group.members.size} members", style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextMuted)
        }
    }
}

