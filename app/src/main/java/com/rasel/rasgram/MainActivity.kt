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

// ==================== APP ENTRY POINT ====================

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf("") }
    
    fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric hardware or not enrolled, just unlock for now
            onUnlocked()
            return
        }
        
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(context as FragmentActivity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                errorMsg = "Authentication error: $errString"
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onUnlocked()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                errorMsg = "Authentication failed."
            }
        })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("RasGram Locked")
            .setSubtitle("Authenticate to access your chats")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        showBiometricPrompt()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RasGramTheme.DarkBackground) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = RasGramTheme.Green, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("RasGram is locked", color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (errorMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMsg, color = RasGramTheme.Red)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { showBiometricPrompt() }, colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green)) {
                Text("Unlock", color = Color.Black)
            }
        }
    }
}

@Composable
fun RasGramApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    val auth = remember { FirebaseAuth.getInstance() }
    var isLoggedIn by remember {
        mutableStateOf(
            prefs.getString(PREF_MOBILE, null) != null &&
            prefs.getString(PREF_UID, null) != null
        )
    }
    var currentUser by remember {
        mutableStateOf(
            if (prefs.getString(PREF_MOBILE, null) != null) User(
                uid = prefs.getString(PREF_UID, "") ?: "",
                name = prefs.getString(PREF_NAME_KEY, "") ?: "",
                mobile = prefs.getString(PREF_MOBILE, "") ?: "",
                avatarUrl = prefs.getString(PREF_AVATAR, "") ?: ""
            ) else null
        )
    }
    var isDarkMode by remember { mutableStateOf(true) }

    var isAppUnlocked by remember { mutableStateOf(false) }
    var remotePrimaryColor by remember { mutableStateOf(RemoteConfigManager.getPrimaryColor()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isAppUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            com.rasel.rasgram.utils.SyncManager(context, currentUser!!.mobile).startSyncing()
        }
        RemoteConfigManager.fetchAndActivate {
            remotePrimaryColor = RemoteConfigManager.getPrimaryColor()
        }
    }

    val dynamicPrimaryColor = try {
        Color(android.graphics.Color.parseColor(remotePrimaryColor))
    } catch (e: Exception) {
        RasGramTheme.Green
    }

    MaterialTheme(colorScheme = if (isDarkMode) darkColorScheme(
        primary = dynamicPrimaryColor,
        secondary = RasGramTheme.GreenDark,
        background = RasGramTheme.DarkBackground,
        surface = RasGramTheme.DarkPanel,
        onBackground = RasGramTheme.TextPrimary,
        onSurface = RasGramTheme.TextPrimary
    ) else lightColorScheme(
        primary = dynamicPrimaryColor,
        secondary = RasGramTheme.GreenDark
    )) {
        if (!isLoggedIn || currentUser == null) {
            OtpLoginScreen(
                onLogin = { user ->
                    prefs.edit()
                        .putString(PREF_MOBILE, user.mobile)
                        .putString(PREF_UID, user.uid)
                        .putString(PREF_NAME_KEY, user.name)
                        .putString(PREF_AVATAR, user.avatarUrl)
                        .apply()
                    currentUser = user
                    isLoggedIn = true
                    isAppUnlocked = true // Unlock app immediately after login
                }
            )
        } else if (!isAppUnlocked) {
            AppLockScreen(onUnlocked = { isAppUnlocked = true })
        } else {
            MainScreen(
                currentUser = currentUser!!,
                isDarkMode = isDarkMode,
                onToggleTheme = { isDarkMode = !isDarkMode },
                onLogout = {
                    prefs.edit().clear().apply()
                    isLoggedIn = false
                    currentUser = null
                    isAppUnlocked = false
                },
                onUserUpdate = { updated ->
                    currentUser = updated
                    prefs.edit()
                        .putString(PREF_NAME_KEY, updated.name)
                        .putString(PREF_AVATAR, updated.avatarUrl)
                        .apply()
                }
            )
        }
    }
}

// ==================== OTP LOGIN SCREEN ====================
@Composable
fun OtpLoginScreen(onLogin: (User) -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) } // 0=phone, 1=name, 2=otp
    var phoneNumber by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+880") }
    var userName by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == 2) {
            countdown = 60
            canResend = false
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            canResend = true
        }
    }

    fun sendOtp(forceResend: Boolean = false) {
        val fullPhone = "$countryCode$phoneNumber"
        if (phoneNumber.length < 9) {
            errorMsg = "Enter a valid phone number"
            return
        }
        isLoading = true
        errorMsg = ""

        // BYPASS FIREBASE AUTH (For testing)
        scope.launch {
            try {
                val uid = "user_${fullPhone.replace("+", "").replace(" ", "")}"
                val mobile = fullPhone.replace("+", "").replace(" ", "")
                val docRef = db.collection("chat_users").document(mobile)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(hashMapOf(
                        "uid" to uid, "name" to userName, "mobile" to mobile,
                        "avatarUrl" to "", "lastActive" to System.currentTimeMillis(),
                        "typingTo" to null, "statusVisible" to true,
                        "about" to "Hey there! I am using RasGram."
                    )).await()
                } else {
                    docRef.update("lastActive", System.currentTimeMillis(), "uid", uid).await()
                }
                val savedName = snap.getString("name") ?: userName
                
                // Save FCM token after login
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                        db.collection("chat_users").document(mobile).update("fcmToken", fcmToken)
                    }
                } catch (_: Exception) { }
                
                onLogin(User(uid = uid, name = savedName, mobile = mobile, avatarUrl = snap.getString("avatarUrl") ?: ""))
            } catch (e: Exception) {
                errorMsg = "Login failed: ${e.message}"
                isLoading = false
            }
        }
    }

    fun verifyOtp() {
        if (otpCode.length != 6) {
            errorMsg = "Enter 6-digit OTP"
            return
        }
        isLoading = true
        errorMsg = ""
        val fullPhone = "$countryCode$phoneNumber"
        scope.launch {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                val result = auth.signInWithCredential(credential).await()
                val uid = result.user?.uid ?: throw Exception("No UID")
                val mobile = fullPhone.replace("+", "").replace(" ", "")
                val docRef = db.collection("chat_users").document(mobile)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(hashMapOf(
                        "uid" to uid, "name" to userName, "mobile" to mobile,
                        "avatarUrl" to "", "lastActive" to System.currentTimeMillis(),
                        "typingTo" to null, "statusVisible" to true,
                        "about" to "Hey there! I am using RasGram."
                    )).await()
                } else {
                    docRef.update("lastActive", System.currentTimeMillis(), "uid", uid)
                }
                val savedName = snap.getString("name") ?: userName
                // Save FCM token after OTP login
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                        db.collection("chat_users").document(mobile).update("fcmToken", fcmToken)
                    }
                } catch (_: Exception) { }
                onLogin(User(uid = uid, name = savedName, mobile = mobile, avatarUrl = snap.getString("avatarUrl") ?: ""))
            } catch (e: Exception) {
                errorMsg = "Invalid OTP. Please try again."
                isLoading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RasGramTheme.DarkBackground) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Logo + Header
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(RasGramTheme.Green.copy(alpha = 0.3f), Color.Transparent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(modifier = Modifier.size(88.dp), shape = CircleShape, color = RasGramTheme.GreenDark) {
                    Icon(Icons.Default.Send, contentDescription = "Logo", tint = Color.White, modifier = Modifier.padding(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "RasGram",
                style = MaterialTheme.typography.headlineLarge,
                color = RasGramTheme.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                when (step) {
                    0 -> "Enter your phone number"
                    1 -> "What's your name?"
                    else -> "Enter verification code"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = RasGramTheme.TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RasGramTheme.DarkPanel),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    AnimatedContent(targetState = step, label = "step") { currentStep ->
                        when (currentStep) {
                            0 -> PhoneInputStep(
                                phoneNumber = phoneNumber,
                                countryCode = countryCode,
                                onPhoneChange = { phoneNumber = it },
                                onCountryCodeChange = { countryCode = it },
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                onNext = {
                                    if (phoneNumber.isNotBlank()) step = 1 else errorMsg = "Enter phone number"
                                }
                            )
                            1 -> NameInputStep(
                                userName = userName,
                                onNameChange = { userName = it },
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                onNext = {
                                    if (userName.isNotBlank()) sendOtp() else errorMsg = "Enter your name"
                                },
                                onBack = { step = 0 }
                            )
                            2 -> OtpInputStep(
                                otpCode = otpCode,
                                phoneNumber = "$countryCode$phoneNumber",
                                onOtpChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                countdown = countdown,
                                canResend = canResend,
                                onVerify = { verifyOtp() },
                                onResend = { sendOtp(forceResend = true) },
                                onBack = { step = 1; otpCode = "" }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = RasGramTheme.Green)
                Spacer(modifier = Modifier.width(6.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.bodySmall, color = RasGramTheme.TextMuted)
            }
        }
    }
}

@Composable
fun PhoneInputStep(
    phoneNumber: String,
    countryCode: String,
    onPhoneChange: (String) -> Unit,
    onCountryCodeChange: (String) -> Unit,
    isLoading: Boolean,
    errorMsg: String,
    onNext: () -> Unit
) {
    val countryCodes = listOf("+880" to "🇧🇩 BD", "+1" to "🇺🇸 US", "+44" to "🇬🇧 UK", "+91" to "🇮🇳 IN", "+971" to "🇦🇪 AE", "+966" to "🇸🇦 SA")
    var showDropdown by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(countryCodes[0]) }

    Column {
        Text("Phone Number", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(
                    onClick = { showDropdown = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RasGramTheme.TextPrimary),
                    border = BorderStroke(1.dp, RasGramTheme.Border),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(selectedCountry.second.take(2), fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(selectedCountry.first, color = RasGramTheme.TextPrimary, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = RasGramTheme.TextMuted)
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier.background(RasGramTheme.DarkPanel)
                ) {
                    countryCodes.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text("$label  $code", color = RasGramTheme.TextPrimary) },
                            onClick = {
                                selectedCountry = code to label
                                onCountryCodeChange(code)
                                showDropdown = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 11) onPhoneChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Phone number", color = RasGramTheme.TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                singleLine = true,
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = RasGramTheme.Red, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
            enabled = !isLoading && phoneNumber.isNotBlank()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            else Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun NameInputStep(
    userName: String,
    onNameChange: (String) -> Unit,
    isLoading: Boolean,
    errorMsg: String,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column {
        Text("Your Name", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = { if (it.length <= 25) onNameChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your name", color = RasGramTheme.TextMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onNext() }),
            singleLine = true,
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                Text("${userName.length}/25", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
            }
        )
        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            // FIX: was RasGramTheme.bodySmall (invalid), now MaterialTheme.typography.bodySmall
            Text(errorMsg, color = RasGramTheme.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, RasGramTheme.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RasGramTheme.TextMuted)
            ) { Text("Back") }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                enabled = !isLoading && userName.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                else Text("Send OTP", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OtpInputStep(
    otpCode: String,
    phoneNumber: String,
    onOtpChange: (String) -> Unit,
    isLoading: Boolean,
    errorMsg: String,
    countdown: Int,
    canResend: Boolean,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "OTP sent to $phoneNumber",
            style = MaterialTheme.typography.bodySmall,
            color = RasGramTheme.TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        // OTP boxes
        OtpBoxes(otpCode = otpCode, onOtpChange = onOtpChange)

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = RasGramTheme.Red, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (canResend) {
            TextButton(onClick = onResend) {
                Text("Resend OTP", color = RasGramTheme.Green, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Resend in ${countdown}s", color = RasGramTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, RasGramTheme.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RasGramTheme.TextMuted)
            ) { Text("Back") }
            Button(
                onClick = onVerify,
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RasGramTheme.Green),
                enabled = !isLoading && otpCode.length == 6
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                else Text("Verify", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun OtpBoxes(otpCode: String, onOtpChange: (String) -> Unit) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        repeat(6) { index ->
            val char = otpCode.getOrNull(index)?.toString() ?: ""
            val isFocused = focusedIndex == index || (index == otpCode.length && index < 6)
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (char.isNotEmpty()) RasGramTheme.Green.copy(alpha = 0.15f) else RasGramTheme.InputBg,
                border = BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) RasGramTheme.Green else RasGramTheme.Border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = char,
                        color = RasGramTheme.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    // Hidden field for input
    OutlinedTextField(
        value = otpCode,
        onValueChange = onOtpChange,
        modifier = Modifier.size(1.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

