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

// ==================== CLOUDINARY UPLOAD ====================
suspend fun uploadToCloudinary(
    context: Context,
    uri: Uri,
    onProgress: (Float) -> Unit = {}
): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"

        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: return@withContext Triple(null, null, null)
        val tempFile = File(context.cacheDir, fileName)
        tempFile.outputStream().use { out -> inputStream.copyTo(out) }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val fileBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())

        val progressBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = tempFile.length()
            override fun writeTo(sink: okio.BufferedSink) {
                val buf = okio.Buffer()
                // FIX #4: was okio.Okio.source(tempFile) Ã¢â‚¬â€ now extension function tempFile.source()
                val src = tempFile.source()
                val total = tempFile.length()
                var uploaded = 0L
                val segmentSize = 2048L
                var read: Long
                while (src.read(buf, segmentSize).also { read = it } != -1L) {
                    sink.write(buf, read)
                    uploaded += read
                    val prog = uploaded.toFloat() / total.toFloat()
                    kotlinx.coroutines.runBlocking { withContext(Dispatchers.Main) { onProgress(prog) } }
                }
            }
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, progressBody)
            .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
            .build()

        val request = Request.Builder().url(CLOUDINARY_UPLOAD_URL).post(requestBody).build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
            println("Cloudinary error: ${response.code} $responseBody")
            return@withContext Triple(null, null, null)
        }

        val json = JSONObject(responseBody)
        val url = json.optString("secure_url", null)
        tempFile.delete()
        Triple(url, fileName, mimeType)
    } catch (e: Exception) {
        println("Cloudinary Exception: ${e.message}")
        Triple(null, null, null)
    }
}

fun getFileName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (_: Exception) { null }

