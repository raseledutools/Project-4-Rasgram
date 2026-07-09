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

// ==================== FCM CALL NOTIFICATION ====================
/**
 * Caller → callee-কে FCM data message পাঠায়
 * App বন্ধ থাকলেও কাজ করে (high priority data message)
 */
suspend fun sendFcmCallNotification(
    calleeMobile: String,
    callerName: String,
    callType: String,
    callId: String,
    db: FirebaseFirestore,
    context: Context
) = withContext(Dispatchers.IO) {
    try {
        // Get callee FCM token from Firestore
        val calleeDoc = db.collection("chat_users").document(calleeMobile).get().await()
        val fcmToken = calleeDoc.getString("fcmToken") ?: return@withContext

        // Read service account from assets
        val saStream = context.resources.openRawResource(
            context.resources.getIdentifier("service_account", "raw", context.packageName)
        )
        val saJson = org.json.JSONObject(saStream.bufferedReader().readText())

        // Get OAuth2 access token for FCM v1 API
        val privateKeyPem = saJson.getString("private_key")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = android.util.Base64.decode(privateKeyPem, android.util.Base64.DEFAULT)
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        val projectId = saJson.getString("project_id")
        val clientEmail = saJson.getString("client_email")
        val now = System.currentTimeMillis() / 1000
        val scope = "https://www.googleapis.com/auth/firebase.messaging"

        // Build JWT
        val header = android.util.Base64.encodeToString(
            """{"alg":"RS256","typ":"JWT"}""".toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
        val claims = android.util.Base64.encodeToString(
            """{"iss":"$clientEmail","scope":"$scope","aud":"https://oauth2.googleapis.com/token","iat":$now,"exp":${now + 3600}}""".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
        val toSign = "$header.$claims"
        val signer = java.security.Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(toSign.toByteArray())
        val sig = android.util.Base64.encodeToString(signer.sign(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        val jwt = "$toSign.$sig"

        // Exchange JWT for access token
        val client = okhttp3.OkHttpClient()
        val tokenReq = okhttp3.Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(okhttp3.FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt).build())
            .build()
        val tokenResp = client.newCall(tokenReq).execute()
        val accessToken = org.json.JSONObject(tokenResp.body?.string() ?: "").optString("access_token")
        if (accessToken.isEmpty()) return@withContext

        // Send FCM data message (high priority - works even when app is killed)
        val fcmPayload = org.json.JSONObject().apply {
            put("message", org.json.JSONObject().apply {
                put("token", fcmToken)
                put("data", org.json.JSONObject().apply {
                    put("type", "incoming_call")
                    put("callerName", callerName)
                    put("callerMobile", android.util.Base64.encodeToString(callerName.toByteArray(), android.util.Base64.NO_WRAP))
                    put("callType", callType)
                    put("callId", callId)
                })
                put("android", org.json.JSONObject().apply {
                    put("priority", "HIGH")
                })
            })
        }.toString()

        val fcmReq = okhttp3.Request.Builder()
            .url("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), fcmPayload))
            .build()
        client.newCall(fcmReq).execute()
    } catch (_: Exception) { }
}
