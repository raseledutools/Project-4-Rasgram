package com.tanimul.android_template_kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

// ==========================================
// GLOBALS & DATA CLASSES
// ==========================================
val BackgroundColor = Color(0xFF020617)
val TextColor = Color(0xFFF8FAFC)
val CardBorder = Color(0x1AFFFFFF)

data class ScanImage(val id: String = UUID.randomUUID().toString(), val dataUrl: String = "", val thumbUrl: String = "")
data class SavedDocument(val id: String = UUID.randomUUID().toString(), val name: String, val date: String, val pages: Int, val thumb: String, val pdfBase64: String) : Serializable
enum class ImageFilter(val label: String) { NONE("Original"), MAGIC_PRO("Magic Scan (Printable)"), PRINT_PRO("B&W Signature"), CLEAR_PRO("Clear Shadow"), SUPER_BW("Super B&W") }
enum class PageSize(val label: String) { FIT("Fit Image"), A4("A4 Document") }
data class ConverterTool(val id: String, val title: String, val description: String, val icon: ImageVector, val tag: String, val gradientColors: List<Color>)
data class ToolData(val id: String, val title: String, val tag: String, val description: String, val icon: ImageVector, val gradient: List<Color>)

// ==========================================
// VIEWMODEL (With Local Persistence)
// ==========================================
class AppViewModel : ViewModel() {
    var currentScreen by mutableStateOf("home") 

    private val _images = MutableStateFlow<List<ScanImage>>(emptyList())
    val images: StateFlow<List<ScanImage>> = _images.asStateFlow()

    private val _savedDocuments = MutableStateFlow<List<SavedDocument>>(emptyList())
    val savedDocuments: StateFlow<List<SavedDocument>> = _savedDocuments.asStateFlow()

    val isLoading = MutableStateFlow(false)
    val loadingTitle = MutableStateFlow("Processing...")
    val loadingDesc = MutableStateFlow("Please wait")
    val progress = MutableStateFlow(0f)
    val progressText = MutableStateFlow("0 / 0")

    val selectedFilter = MutableStateFlow(ImageFilter.MAGIC_PRO) 
    val pageSize = MutableStateFlow(PageSize.A4) 
    val autoCrop = MutableStateFlow(true)

    val showCamera = MutableStateFlow(false)
    val showPdfPreview = MutableStateFlow(false)
    val previewDoc = MutableStateFlow<SavedDocument?>(null)
    val showPdfImportModal = MutableStateFlow(false)

    var tempImportPdfUri: Uri? = null
    val toastMessage = MutableStateFlow<String?>(null)

    val selectedDocsForMerge = mutableStateListOf<String>()
    var isSelectionMode by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var docToRename by mutableStateOf<SavedDocument?>(null)

    // Converter States
    var convInputText by mutableStateOf("")
    var convOutputText by mutableStateOf("")
    var convImageUri by mutableStateOf<Uri?>(null)
    var convImageBitmap by mutableStateOf<Bitmap?>(null)

    // History Save/Load Logic
    fun loadHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "ras_scanner_history.dat")
            if (file.exists()) {
                try {
                    ObjectInputStream(FileInputStream(file)).use { 
                        @Suppress("UNCHECKED_CAST")
                        val docs = it.readObject() as List<SavedDocument>
                        _savedDocuments.value = docs
                    }
                } catch (e: Exception) { Log.e("ViewModel", "Failed to load history", e) }
            }
        }
    }

    private fun saveHistoryLocal(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "ras_scanner_history.dat")
                ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(_savedDocuments.value) }
            } catch (e: Exception) { Log.e("ViewModel", "Failed to save history", e) }
        }
    }

    fun showToast(message: String) { toastMessage.value = message }
    fun clearToast() { toastMessage.value = null }
    fun setLoading(status: Boolean, title: String = "Processing...", desc: String = "Please wait...") { isLoading.value = status; loadingTitle.value = title; loadingDesc.value = desc }
    fun updateProgress(current: Int, total: Int) { progress.value = if (total > 0) current.toFloat() / total else 0f; progressText.value = "$current / $total" }
    
    fun addImage(image: ScanImage) { _images.value += image }
    fun removeImage(id: String) { _images.value = _images.value.filter { it.id != id } }
    fun clearImages() { _images.value = emptyList() }
    
    fun addSavedDocument(context: Context, doc: SavedDocument) { 
        _savedDocuments.value = listOf(doc) + _savedDocuments.value
        saveHistoryLocal(context)
    }
    fun deleteDocument(context: Context, id: String) { 
        _savedDocuments.value = _savedDocuments.value.filter { it.id != id }
        saveHistoryLocal(context)
    }
    
    fun setShowPdfImportModal(show: Boolean, uri: Uri? = null) { showPdfImportModal.value = show; if (show) tempImportPdfUri = uri }
    fun setShowPdfPreview(show: Boolean, doc: SavedDocument? = null) { showPdfPreview.value = show; previewDoc.value = doc }
    fun toggleSelectionMode() { isSelectionMode = !isSelectionMode; if (!isSelectionMode) selectedDocsForMerge.clear() }
    fun toggleDocSelection(id: String) { if (selectedDocsForMerge.contains(id)) selectedDocsForMerge.remove(id) else selectedDocsForMerge.add(id) }

    fun renameDocument(context: Context, docId: String, newName: String) {
        val finalName = if (newName.endsWith(".pdf")) newName else "$newName.pdf"
        _savedDocuments.value = _savedDocuments.value.map { if (it.id == docId) it.copy(name = finalName) else it }
        saveHistoryLocal(context)
        showToast("Renamed successfully!")
    }

    fun resetConverterStates() { convInputText = ""; convOutputText = ""; convImageUri = null; convImageBitmap = null }
}

// ==========================================
// MAIN ACTIVITY
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action
        val type = intent.type
        val uri: Uri? = intent.data

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF4F46E5), secondary = Color(0xFF10B981), background = Color(0xFFF9FAFB), surface = Color.White)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: AppViewModel = viewModel()
                    val context = LocalContext.current
                    
                    LaunchedEffect(Unit) { viewModel.loadHistory(context) }
                    
                    LaunchedEffect(uri) {
                        if (Intent.ACTION_VIEW == action && type == "application/pdf" && uri != null) {
                            viewModel.currentScreen = "scanner"
                            viewModel.setShowPdfImportModal(true, uri)
                        }
                    }
                    
                    Crossfade(targetState = viewModel.currentScreen, label = "Router") { screen ->
                        when (screen) {
                            "home" -> LocalToolsetScreen(viewModel)
                            "scanner" -> RasScannerScreen(viewModel)
                            "converters" -> ConverterSuiteScreen(viewModel)
                            "tool_txt_b64" -> ToolTextBase64Screen(viewModel)
                            "tool_img_b64" -> ToolImageBase64Screen(viewModel)
                            "tool_ocr" -> ToolOcrScreen(viewModel)
                            "tool_word_pdf" -> ToolWordToPdfScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalToolsetScreen(viewModel: AppViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAdminDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = { SidebarContent(onClose = { scope.launch { drawerState.close() } }) }) {
        Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            // Top Right Nav Buttons
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).zIndex(10f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.currentScreen = "scanner" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp))) {
                    Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(16.dp), tint = Color.White); Spacer(modifier = Modifier.width(4.dp)); Text("Scanner", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
                Button(onClick = { viewModel.currentScreen = "converters" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008CBA)), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp))) {
                    Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp), tint = Color.White); Spacer(modifier = Modifier.width(4.dp)); Text("Converters", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
            
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 300.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 60.dp, bottom = 20.dp)) {
                        Box(modifier = Modifier.size(110.dp).clip(CircleShape).border(2.dp, Color(0x4DFFFFFF), CircleShape).clickable { showAdminDialog = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = Color.White) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(50.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFEC4899), CircleShape)); Spacer(modifier = Modifier.width(8.dp)); Text("SYSTEM ONLINE", fontSize = 12.sp, color = TextColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp)); Text("Rasel Edu Tools", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(24.dp))
                        TickerView(); Spacer(modifier = Modifier.height(24.dp)); ActionButtons(SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US).format(Date()), context)
                    }
                }
                items(getToolsList()) { tool ->
                    ToolCard(tool = tool, context = context, onClick = {
                        if (tool.id == "converter") viewModel.currentScreen = "converters"
                        else if (tool.id == "scanner") viewModel.currentScreen = "scanner"
                        else Toast.makeText(context, "Opening ${tool.title}", Toast.LENGTH_SHORT).show()
                    })
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Footer { showAdminDialog = true } }
            }
            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color(0x1AFFFFFF), CircleShape).border(1.dp, Color(0x33FFFFFF), CircleShape)) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
        }
    }
    if (showAdminDialog) AdminLoginDialog(onDismiss = { showAdminDialog = false })
}

fun getToolsList() = listOf(
    ToolData("diary", "Professional Diary", "PERSONAL", "Encrypted daily journal. Track tasks, log thoughts.", Icons.Default.Book, listOf(Color(0xCC9333EA), Color(0xCCDB2777))),
    ToolData("rasbook", "RasBook", "DOCUMENT", "A professional social media like facebook. Fast and secure.", Icons.Default.Face, listOf(Color(0xCC2563EB), Color(0xCC0891B2))),
    ToolData("scanner", "RasScanner Pro", "DOCUMENT", "Scan Documents, Edit PDF, Create Digital Records.", Icons.Default.DocumentScanner, listOf(Color(0xE64F46E5), Color(0xB3EC4899))),
    ToolData("converter", "Universal Converter", "UTILITY", "Precision tools for format conversion, unit calculation.", Icons.Default.Refresh, listOf(Color(0xCC059669), Color(0xCC0D9488))),
    ToolData("workspace", "Code Workspace", "DEVELOPER", "Live IDE environment. Write, debug, and execute HTML/CSS/JS.", Icons.Default.Code, listOf(Color(0xCCEA580C), Color(0xCCDC2626))),
    ToolData("gallery", "Media Gallery", "MEDIA", "High-performance image viewer. Organize visual assets.", Icons.Default.AccountBox, listOf(Color(0xCCE11D48), Color(0xCCEC4899))),
    ToolData("audio", "Focus Noise & Sleep", "AUDIO", "Advanced frequency generation engine.", Icons.Default.PlayArrow, listOf(Color(0xE64F46E5), Color(0xE67C3AED))),
    ToolData("ramadan", "Ramadan Plan Book", "LIFESTYLE", "Interactive guide for a productive Ramadan.", Icons.Default.Star, listOf(Color(0xCCD97706), Color(0xCCF97316))),
    ToolData("chat", "Connect Globally", "UPDATES", "Give any suggestion or view broadcast messages.", Icons.Default.Share, listOf(Color(0xCCC026D3), Color(0xCC9333EA)))
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TickerView() {
    val tickerText by remember { mutableStateOf("Connecting to server... Global Active.") }
    Row(modifier = Modifier.fillMaxWidth(0.9f).height(48.dp).background(Color(0x800F172A), RoundedCornerShape(50.dp)).border(1.dp, CardBorder, RoundedCornerShape(50.dp)), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFDB2777), Color(0xFF9333EA))), RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { Text("UPDATE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        Text(text = tickerText, color = TextColor, modifier = Modifier.padding(horizontal = 16.dp).basicMarquee(), maxLines = 1)
    }
}

@Composable
fun ActionButtons(currentDate: String, context: Context) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton("View Notice Board", Icons.Default.Notifications, listOf(Color(0xFFC026D3), Color(0xFFE11D48))) { Toast.makeText(context, "Navigating", Toast.LENGTH_SHORT).show() }
                ActionButton("Today's Live Exam\n$currentDate", Icons.Default.Edit, listOf(Color(0xFF059669), Color(0xFF0891B2))) { }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton("Join RasGram", Icons.Default.Chat, listOf(Color(0xFF2563EB), Color(0xFF4F46E5))) { }
                ActionButton("PSC Solutions", Icons.Default.Book, listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))) { }
            }
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, gradient: List<Color>, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(Color.Transparent), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(50.dp), modifier = Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50.dp))) {
        Row(modifier = Modifier.background(Brush.horizontalGradient(gradient)).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ToolCard(tool: ToolData, context: Context, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(Color.Transparent), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.background(Brush.linearGradient(tool.gradient)).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(Color(0x33000000), RoundedCornerShape(12.dp)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = Color.White) }
                Text(text = tool.tag, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(50.dp)).padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp)); Text(tool.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp)); Text(tool.description, fontSize = 14.sp, color = Color(0xCCFFFFFF), lineHeight = 20.sp)
        }
    }
}

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF0F172A),
        title = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null, tint = Color(0xFFEC4899), modifier = Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text("Admin Access", color = Color.White, fontWeight = FontWeight.Bold); Text("Restricted area. Verification required.", color = Color(0xFF94A3B8), fontSize = 12.sp) } },
        text = { OutlinedTextField(value = password, onValueChange = { password = it; isError = false }, placeholder = { Text("••••••••", color = Color.Gray) }, singleLine = true, isError = isError, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFEC4899), unfocusedBorderColor = Color(0x1AFFFFFF))) },
        confirmButton = { Button(onClick = { if (password == "    ") { Toast.makeText(context, "Admin Logged In!", Toast.LENGTH_SHORT).show(); onDismiss() } else { isError = true; password = "" } }, colors = ButtonDefaults.buttonColors(Color(0xFFDB2777))) { Text("LOGIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White) } }
    )
}

@Composable
fun Footer(onAdminClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = CardBorder); Spacer(modifier = Modifier.height(16.dp)); Text("© Rasel Edu Tools", color = Color(0xFFCBD5E1), fontSize = 14.sp); Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ENGINEERED BY ", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(8.dp))
            Row(modifier = Modifier.background(Color(0x0DFFFFFF), CircleShape).clickable { onAdminClick() }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Rasel Mia", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(8.dp)); Text("📞 01566054963", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SidebarContent(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxHeight().width(300.dp).background(Color(0xF20F172A)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Web Tools", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.Gray) } }
        HorizontalDivider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 16.dp))
        Text("MAIN NAVIGATION", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold); DrawerItem("Notice Board", Icons.Default.Notifications); DrawerItem("Live Exam Center", Icons.Default.Edit); Spacer(modifier = Modifier.height(16.dp))
        Text("PROFESSIONAL TOOLS", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold); DrawerItem("Professional Diary", Icons.Default.Book); DrawerItem("RasBook", Icons.Default.Face); DrawerItem("Code Workspace", Icons.Default.Code); DrawerItem("Universal Converter", Icons.Default.Refresh); DrawerItem("Media Gallery", Icons.Default.AccountBox)
    }
}

@Composable
fun DrawerItem(title: String, icon: ImageVector) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { }, verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(16.dp)); Text(title, color = Color(0xFFE2E8F0), fontSize = 14.sp) } }

// ==========================================
// 2. SCANNER SCREEN (With PDF & QR)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RasScannerScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler { viewModel.currentScreen = "home" }

    // HOISTED STATES
    val images by viewModel.images.collectAsState()
    val savedDocs by viewModel.savedDocuments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingTitle by viewModel.loadingTitle.collectAsState()
    val loadingDesc by viewModel.loadingDesc.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val showCamera by viewModel.showCamera.collectAsState()
    val showPdfPreview by viewModel.showPdfPreview.collectAsState()
    val showPdfImportModal by viewModel.showPdfImportModal.collectAsState()
    val autoCrop by viewModel.autoCrop.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val previewDoc by viewModel.previewDoc.collectAsState()

    LaunchedEffect(toastMessage) { toastMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearToast() } }

    val multipleImagePickerLauncher = rememberLauncherForActivityResult(PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setLoading(true, "Importing Images...", "Optimizing quality for scan.")
            viewModel.viewModelScope.launch { for ((index, uri) in uris.withIndex()) { processImageUri(context, viewModel, uri); viewModel.updateProgress(index + 1, uris.size) }; viewModel.setLoading(false) }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(GetContent()) { uri -> uri?.let { viewModel.setShowPdfImportModal(true, it) } }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted -> if (granted) viewModel.showCamera.value = true else viewModel.showToast("Camera permission required") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
            Surface(color = Color(0xFF4F46E5), shadowElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.currentScreen = "home" }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                        Icon(Icons.Default.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(28.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("RasScanner", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    if (images.isNotEmpty()) { Button(onClick = { viewModel.clearImages() }, colors = ButtonDefaults.buttonColors(Color.White), shape = RoundedCornerShape(50), contentPadding = PaddingValues(16.dp, 8.dp)) { Text("Clear", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                ActionButtonsGridScanner(
                    onCameraClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) viewModel.showCamera.value = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onGalleryClick = { multipleImagePickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    onImportPdfClick = { pdfPickerLauncher.launch("application/pdf") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsCard(autoCrop, { viewModel.autoCrop.value = it }, selectedFilter, { viewModel.selectedFilter.value = it }, pageSize, { viewModel.pageSize.value = it })
                Spacer(modifier = Modifier.height(16.dp))

                if (images.isNotEmpty()) {
                    Text("Selected Pages (${images.size})", fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5), modifier = Modifier.padding(bottom = 8.dp))
                    ImageGridScanner(images = images, onRemoveImage = { viewModel.removeImage(it) })
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.setLoading(true, "Scanning & Processing...", "Applying magic filters and creating PDF."); viewModel.viewModelScope.launch(Dispatchers.IO) { generatePDF(context, viewModel); withContext(Dispatchers.Main) { viewModel.setLoading(false) } } },
                        modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(Color(0xFF10B981)), shape = RoundedCornerShape(20.dp), elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("CREATE PDF NOW", fontWeight = FontWeight.Black, fontSize = 18.sp) }
                } else {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White.copy(0.7f)), border = BorderStroke(2.dp, Color(0xFFD1D5DB).copy(0.5f))) {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DocumentScanner, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text("Ready to Scan", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4B5563))
                            Text("Select photos or use Camera (Continuous shooting enabled).", fontSize = 13.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (viewModel.isSelectionMode && viewModel.selectedDocsForMerge.size > 1) {
                    Button(
                        onClick = { viewModel.setLoading(true, "Merging PDFs...", "Combining selected documents."); viewModel.viewModelScope.launch(Dispatchers.IO) { mergeSelectedPdfs(context, viewModel); withContext(Dispatchers.Main) { viewModel.setLoading(false) } } },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(Color(0xFFF97316))
                    ) { Text("MERGE ${viewModel.selectedDocsForMerge.size} PDFs", fontWeight = FontWeight.Bold) }
                }

                HistorySection(viewModel = viewModel, documents = savedDocs, onViewPdf = { doc -> viewModel.setShowPdfPreview(true, doc) }, onDownload = { doc -> saveToLocal(context, doc) }, onDelete = { doc -> viewModel.deleteDocument(context, doc.id) }, onRename = { doc -> viewModel.docToRename = doc; viewModel.showRenameDialog = true })
            }
        }

        // Overlays
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize().zIndex(100f)) { LoadingOverlay(title = loadingTitle, description = loadingDesc, progress = progress, progressText = progressText) }
        AnimatedVisibility(visible = showCamera, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically(), modifier = Modifier.fillMaxSize().zIndex(60f)) { CameraOverlayWithQR(context = context, lifecycleOwner = lifecycleOwner, viewModel = viewModel, onClose = { viewModel.showCamera.value = false }) }
        AnimatedVisibility(visible = showPdfPreview, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically(), modifier = Modifier.fillMaxSize().zIndex(70f)) { previewDoc?.let { doc -> PdfPreviewOverlay(context = context, document = doc, onClose = { viewModel.setShowPdfPreview(false) }) } }
        AnimatedVisibility(visible = showPdfImportModal, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize().zIndex(80f)) {
            PdfImportModal(viewModel = viewModel, onScan = { viewModel.setShowPdfImportModal(false); viewModel.tempImportPdfUri?.let { uri -> viewModel.viewModelScope.launch { importPdfAsScan(context, viewModel, uri) } } }, onRead = { viewModel.setShowPdfImportModal(false); viewModel.tempImportPdfUri?.let { uri -> viewModel.viewModelScope.launch { importPdfAsReader(context, viewModel, uri) } } }, onCancel = { viewModel.setShowPdfImportModal(false) })
        }
        if (viewModel.showRenameDialog && viewModel.docToRename != null) { RenameDialog(currentName = viewModel.docToRename!!.name, onDismiss = { viewModel.showRenameDialog = false }, onConfirm = { newName -> viewModel.renameDocument(context, viewModel.docToRename!!.id, newName); viewModel.showRenameDialog = false }) }
    }
}

// ==========================================
// 3. CONVERTER SUITE MENU SCREEN 
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterSuiteScreen(viewModel: AppViewModel) {
    BackHandler { viewModel.currentScreen = "home" }
    
    // HOISTED STATES
    val savedDocs by viewModel.savedDocuments.collectAsState()
    val showPdfPreview by viewModel.showPdfPreview.collectAsState()
    val previewDoc by viewModel.previewDoc.collectAsState()
    val context = LocalContext.current

    val toolsList = listOf(
        ConverterTool("scanner", "Image to PDF", "Convert single or multiple JPG/PNG images into a single, high-quality PDF document instantly.", Icons.Default.PictureAsPdf, "Document", listOf(Color(0xFF2563EB), Color(0xFF3B82F6))),
        ConverterTool("tool_ocr", "Image to Text", "Extract readable and editable text from images using advanced OCR technology.", Icons.Default.FontDownload, "OCR AI", listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6))),
        ConverterTool("tool_word_pdf", "Word to PDF", "Securely extract text from MS Word (.docx) files into standard PDF format.", Icons.Default.Description, "Document", listOf(Color(0xFF059669), Color(0xFF10B981))),
        ConverterTool("tool_txt_b64", "Text ⇆ Base64", "Encode plain string text into Base64 format, or decode Base64 back into readable text.", Icons.Default.Code, "Developer", listOf(Color(0xFFDB2777), Color(0xFFEC4899))),
        ConverterTool("tool_img_b64", "Image ⇆ Base64", "Convert image files into Base64 strings for direct HTML embedding, or decode back.", Icons.Default.Image, "Developer", listOf(Color(0xFFEA580C), Color(0xFFF97316)))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).background(brush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))), shape = RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.SyncAlt, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Spacer(modifier = Modifier.width(12.dp)); Text("Converter Suite", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B))
                    }
                },
                navigationIcon = { IconButton(onClick = { viewModel.currentScreen = "home" }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF475569)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(0.95f)), modifier = Modifier.shadow(4.dp)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(24.dp)); Text(text = "Universal Converter Suite", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp)); Text(text = "A high-performance toolkit engineered by Rasel Mia.", fontSize = 14.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(24.dp))

            toolsList.forEach { tool ->
                ConverterCardItem(tool = tool) {
                    viewModel.resetConverterStates()
                    if (tool.id == "scanner") viewModel.showToast("Use Scanner interface for Image to PDF!")
                    viewModel.currentScreen = tool.id
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            HistorySection(viewModel = viewModel, documents = savedDocs, onViewPdf = { doc -> viewModel.setShowPdfPreview(true, doc) }, onDownload = { doc -> saveToLocal(context, doc) }, onDelete = { doc -> viewModel.deleteDocument(context, doc.id) }, onRename = { doc -> viewModel.docToRename = doc; viewModel.showRenameDialog = true })
            Spacer(modifier = Modifier.height(32.dp))
        }

        AnimatedVisibility(visible = showPdfPreview, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically(), modifier = Modifier.fillMaxSize().zIndex(70f)) { previewDoc?.let { doc -> PdfPreviewOverlay(context = context, document = doc, onClose = { viewModel.setShowPdfPreview(false) }) } }
        if (viewModel.showRenameDialog && viewModel.docToRename != null) { RenameDialog(currentName = viewModel.docToRename!!.name, onDismiss = { viewModel.showRenameDialog = false }, onConfirm = { newName -> viewModel.renameDocument(context, viewModel.docToRename!!.id, newName); viewModel.showRenameDialog = false }) }
    }
}

@Composable
fun ConverterCardItem(tool: ConverterTool, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }.clip(RoundedCornerShape(20.dp)), elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(54.dp).background(Brush.linearGradient(tool.gradientColors), RoundedCornerShape(14.dp)).shadow(4.dp, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(50)) { Text(text = tool.tag.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), letterSpacing = 1.sp) }
            }
            Spacer(modifier = Modifier.height(16.dp)); Text(tool.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)); Spacer(modifier = Modifier.height(8.dp)); Text(tool.description, fontSize = 14.sp, color = Color(0xFF64748B), lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text("Open Tool", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tool.gradientColors.first()); Spacer(modifier = Modifier.width(6.dp)); Icon(Icons.Default.ArrowForward, null, tint = tool.gradientColors.first(), modifier = Modifier.size(16.dp)) }
        }
    }
}

// ==========================================
// 4. CONVERTER TOOLS LOGIC SCREENS
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTextBase64Screen(viewModel: AppViewModel) {
    val clipboard = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    BackHandler { viewModel.currentScreen = "converters" }
    Scaffold(topBar = { TopAppBar(title = { Text("Text ⇆ Base64") }, navigationIcon = { IconButton(onClick = { viewModel.currentScreen = "converters" }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = viewModel.convInputText, onValueChange = { viewModel.convInputText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Input Text or Base64") })
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { try { viewModel.convOutputText = Base64.encodeToString(viewModel.convInputText.toByteArray(), Base64.DEFAULT) } catch(e:Exception){ viewModel.showToast("Error Encoding") } }) { Text("Encode") }
                Button(onClick = { try { viewModel.convOutputText = String(Base64.decode(viewModel.convInputText, Base64.DEFAULT)) } catch(e:Exception){ viewModel.showToast("Invalid Base64") } }) { Text("Decode") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = viewModel.convOutputText, onValueChange = { }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Output Result") }, readOnly = true)
            if(viewModel.convOutputText.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Button(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Result", viewModel.convOutputText)); viewModel.showToast("Copied!") }, modifier = Modifier.fillMaxWidth()) { Text("Copy Result") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolImageBase64Screen(viewModel: AppViewModel) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    BackHandler { viewModel.currentScreen = "converters" }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if(uri != null) {
            viewModel.convImageUri = uri; val bitmap = MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
            val baos = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            viewModel.convOutputText = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Image ⇆ Base64") }, navigationIcon = { IconButton(onClick = { viewModel.currentScreen = "converters" }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text("Select Image to Encode") }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = viewModel.convOutputText, onValueChange = { viewModel.convOutputText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Base64 String") })
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("B64", viewModel.convOutputText)); viewModel.showToast("Copied!") }) { Text("Copy Base64") }
                Button(onClick = { try { val bytes = Base64.decode(viewModel.convOutputText.substringAfter(","), Base64.DEFAULT); viewModel.convImageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (e:Exception){ viewModel.showToast("Invalid Base64") } }) { Text("Decode to Image") }
            }
            Spacer(Modifier.height(16.dp))
            if(viewModel.convImageBitmap != null) { Image(bitmap = viewModel.convImageBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(300.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolOcrScreen(viewModel: AppViewModel) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    BackHandler { viewModel.currentScreen = "converters" }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if(uri != null) {
            viewModel.convImageUri = uri; val bitmap = MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
            viewModel.convImageBitmap = bitmap; viewModel.convOutputText = "Scanning with AI..."
            val image = InputImage.fromBitmap(bitmap, 0); val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).addOnSuccessListener { visionText -> viewModel.convOutputText = visionText.text }.addOnFailureListener { e -> viewModel.convOutputText = "OCR Failed: ${e.message}" }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Image to Text (OCR)") }, navigationIcon = { IconButton(onClick = { viewModel.currentScreen = "converters" }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text("Select Document Image") }
            Spacer(Modifier.height(16.dp))
            if(viewModel.convImageBitmap != null) { Image(bitmap = viewModel.convImageBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Fit); Spacer(Modifier.height(16.dp)) }
            OutlinedTextField(value = viewModel.convOutputText, onValueChange = { viewModel.convOutputText = it }, modifier = Modifier.fillMaxWidth().height(250.dp), label = { Text("Extracted Text") })
            Spacer(Modifier.height(8.dp))
            if(viewModel.convOutputText.isNotEmpty() && !viewModel.convOutputText.contains("Scanning")) { Button(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("OCR", viewModel.convOutputText)); viewModel.showToast("Copied!") }, modifier = Modifier.fillMaxWidth()) { Text("Copy Text") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWordToPdfScreen(viewModel: AppViewModel) {
    val ctx = LocalContext.current
    BackHandler { viewModel.currentScreen = "converters" }
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingTitle by viewModel.loadingTitle.collectAsState()
    val loadingDesc by viewModel.loadingDesc.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()

    val docPicker = rememberLauncherForActivityResult(GetContent()) { uri ->
        if(uri != null) {
            viewModel.setLoading(true, "Converting DOCX...", "Parsing document XML and generating PDF.")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sb = java.lang.StringBuilder()
                    ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name == "word/document.xml") {
                                    val xml = zis.bufferedReader().readText(); val matcher = java.util.regex.Pattern.compile("<w:t.*?>(.*?)</w:t>").matcher(xml)
                                    while (matcher.find()) { sb.append(matcher.group(1)).append(" ") }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                    val extractedText = sb.toString().trim()
                    if(extractedText.isEmpty()) { withContext(Dispatchers.Main) { viewModel.showToast("No text found."); viewModel.setLoading(false) }; return@launch }

                    val document = PdfDocument(); val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create(); val page = document.startPage(pageInfo)
                    val textPaint = TextPaint().apply { color = android.graphics.Color.BLACK; textSize = 14f }
                    val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { StaticLayout.Builder.obtain(extractedText, 0, extractedText.length, textPaint, 500).build() } else { @Suppress("DEPRECATION") StaticLayout(extractedText, textPaint, 500, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false) }
                    
                    page.canvas.translate(40f, 40f); staticLayout.draw(page.canvas); document.finishPage(page)
                    val baos = ByteArrayOutputStream(); document.writeTo(baos); document.close()
                    val pdfBase64 = "data:application/pdf;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    
                    val savedDoc = SavedDocument(id = "doc_${System.currentTimeMillis()}", name = "Converted_Doc_$timestamp.pdf", date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date()), pages = 1, thumb = "", pdfBase64 = pdfBase64)
                    withContext(Dispatchers.Main) { viewModel.addSavedDocument(ctx, savedDoc); viewModel.showToast("Success!"); viewModel.setLoading(false); viewModel.currentScreen = "converters" } 
                } catch (e: Exception) { Log.e("Convert", "Fail", e); withContext(Dispatchers.Main) { viewModel.showToast("Failed to parse file."); viewModel.setLoading(false) } }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Word to PDF") }, navigationIcon = { IconButton(onClick = { viewModel.currentScreen = "converters" }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { p ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(p).padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Description, null, modifier = Modifier.size(80.dp), tint = Color(0xFF059669)); Spacer(Modifier.height(16.dp))
                Text("Select a .docx file to extract its text and generate a clean PDF document.", textAlign = TextAlign.Center, color = Color.Gray); Spacer(Modifier.height(32.dp))
                Button(onClick = { docPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Select DOCX File") }
            }
            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize().zIndex(100f)) { LoadingOverlay(title = loadingTitle, description = loadingDesc, progress = progress, progressText = progressText) }
        }
    }
}

// ==========================================
// RENAME DIALOG
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName.removeSuffix(".pdf")) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Rename PDF", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ==========================================
// SHARED UI COMPONENTS
// ==========================================
@Composable
fun ScannerHeader(hasImages: Boolean, onClear: () -> Unit) {
    Surface(color = Color(0xFF4F46E5), shadowElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(32.dp)); Spacer(modifier = Modifier.width(10.dp)); Text("RasScanner", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
            if (hasImages) { Button(onClick = onClear, colors = ButtonDefaults.buttonColors(Color.White), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("Clear", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
        }
    }
}

@Composable
fun ActionButtonsGridScanner(onCameraClick: () -> Unit, onGalleryClick: () -> Unit, onImportPdfClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButtonScan(Modifier.weight(1f), "Camera & QR", Color(0xFF4F46E5), Color.White, Icons.Default.CameraAlt, onClick = onCameraClick)
        ActionButtonScan(Modifier.weight(1f), "Gallery", Color.White, Color(0xFF4F46E5), Icons.Default.PhotoLibrary, borderColor = Color(0xFFC7D2FE), onClick = onGalleryClick)
        ActionButtonScan(Modifier.weight(1f), "Import PDF", Color.White, Color(0xFF059669), Icons.Default.PictureAsPdf, borderColor = Color(0xFFA7F3D0), onClick = onImportPdfClick)
    }
}

@Composable
fun ActionButtonScan(modifier: Modifier, title: String, containerColor: Color, contentColor: Color, icon: ImageVector, borderColor: Color? = null, onClick: () -> Unit) {
    Card(modifier = modifier.height(85.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor), border = borderColor?.let { BorderStroke(1.dp, it) }, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, tint = contentColor, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(6.dp)); Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCard(autoCrop: Boolean, onAutoCropChange: (Boolean) -> Unit, selectedFilter: ImageFilter, onFilterChange: (ImageFilter) -> Unit, pageSize: PageSize, onPageSizeChange: (PageSize) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DropdownSetting("Scan Filter", selectedFilter.label, ImageFilter.entries.map { it.label }) { idx -> onFilterChange(ImageFilter.entries[idx]) }
            DropdownSetting("Export Size", pageSize.label, PageSize.entries.map { it.label }) { idx -> onPageSizeChange(PageSize.entries[idx]) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSetting(label: String, selectedText: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = selectedText, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5), unfocusedBorderColor = Color(0xFFE5E7EB)), shape = RoundedCornerShape(16.dp))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEachIndexed { i, opt -> DropdownMenuItem(text = { Text(opt, fontWeight = FontWeight.Bold, fontSize = 14.sp) }, onClick = { onSelect(i); expanded = false }) } }
        }
    }
}

@Composable
fun ImageGridScanner(images: List<ScanImage>, onRemoveImage: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = images, key = { it.id }) { img -> ImageGridItem(img, images.indexOf(img) + 1, onRemove = { onRemoveImage(img.id) }) }
    }
}

@Composable
fun ImageGridItem(image: ScanImage, pageNumber: Int, onRemove: () -> Unit) {
    Card(modifier = Modifier.aspectRatio(0.75f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(6.dp)) {
        Box(Modifier.fillMaxSize()) {
            val bitmap = remember(image.thumbUrl) { if (image.thumbUrl.isNotEmpty()) decodeBase64(image.thumbUrl) else null }
            if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Page $pageNumber", modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) 
            else Box(Modifier.fillMaxSize().background(Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, tint = Color.Gray) }
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).background(Color(0xFFEF4444).copy(alpha=0.9f), CircleShape)) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF4F46E5).copy(0.9f)) { Text("Page $pageNumber", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
        }
    }
}

@Composable
fun EmptyState() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White.copy(0.7f)), border = BorderStroke(2.dp, Color(0xFFD1D5DB).copy(0.5f))) {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DocumentScanner, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp))
            Text("Ready to Scan", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4B5563))
            Text("Select photos or use Camera.", fontSize = 13.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun GeneratePdfButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(Color(0xFF10B981)), shape = RoundedCornerShape(20.dp), elevation = ButtonDefaults.buttonElevation(8.dp)) {
        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("CREATE PDF NOW", fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun HistorySection(viewModel: AppViewModel, documents: List<SavedDocument>, onViewPdf: (SavedDocument) -> Unit, onDownload: (SavedDocument) -> Unit, onDelete: (SavedDocument) -> Unit, onRename: (SavedDocument) -> Unit) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FolderZip, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text("Saved Documents Library", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1F2937)) }
            if (documents.size > 1) { TextButton(onClick = { viewModel.toggleSelectionMode() }) { Text(if (viewModel.isSelectionMode) "Cancel Merge" else "Merge PDFs", color = if (viewModel.isSelectionMode) Color.Red else Color(0xFF4F46E5), fontWeight = FontWeight.Bold) } }
        }
        Spacer(Modifier.height(8.dp))
        if (documents.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White.copy(0.7f))) { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("No PDFs generated yet.", color = Color.Gray) } }
        } else {
            documents.forEach { doc ->
                val isSelected = viewModel.selectedDocsForMerge.contains(doc.id)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { if (viewModel.isSelectionMode) viewModel.toggleDocSelection(doc.id) else onViewPdf(doc) },
                    shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(if (isSelected) Color(0xFFEEF2FF) else Color.White), border = if (isSelected) BorderStroke(2.dp, Color(0xFF4F46E5)) else null, elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleDocSelection(doc.id) }) else Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(doc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${doc.pages} Pages • ${doc.date}", fontSize = 11.sp, color = Color.Gray) }
                        if (!viewModel.isSelectionMode) {
                            IconButton(onClick = { sharePdf(context, doc) }) { Icon(Icons.Default.Share, null, tint = Color(0xFF10B981)) }
                            IconButton(onClick = { onRename(doc) }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFF97316)) }
                            IconButton(onClick = { onDelete(doc) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                        }
                    }
                }
            }
        }
    }
}

// 📸 CAMERA OVERLAY WITH CONTINUOUS SHOOTING 📸
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraOverlayWithQR(context: Context, lifecycleOwner: LifecycleOwner, viewModel: AppViewModel, onClose: () -> Unit) {
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var detectedQrText by remember { mutableStateOf<String?>(null) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    val currentImagesCount = viewModel.images.collectAsState().value.size

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView?.surfaceProvider) }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                
                val barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) { val rawValue = barcode.rawValue; if (rawValue != null && detectedQrText != rawValue) detectedQrText = rawValue }
                        }.addOnCompleteListener { imageProxy.close() }
                    } else { imageProxy.close() }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis)
            } catch (e: Exception) { Log.e("CameraOverlay", "Camera failed", e) }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(16.dp).zIndex(10f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
            IconButton(onClick = onClose, modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            if (currentImagesCount > 0) {
                Surface(color = Color(0xFF4F46E5), shape = RoundedCornerShape(50)) { Text("$currentImagesCount Captured", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            }
        }
        AndroidView(factory = { ctx -> PreviewView(ctx).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); scaleType = PreviewView.ScaleType.FILL_CENTER }.also { previewView = it } }, modifier = Modifier.fillMaxSize())
        
        if (detectedQrText != null) {
            Card(modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp, start = 24.dp, end = 24.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Color.White.copy(0.9f)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("QR Code Detected!", fontWeight = FontWeight.Bold, color = Color(0xFF10B981)); Spacer(Modifier.height(8.dp)); Text(detectedQrText!!, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp))
                    Button(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("QR", detectedQrText)); viewModel.showToast("Copied!"); detectedQrText = null }) { Text("Copy & Dismiss") }
                }
            }
        }
        
        Box(Modifier.align(Alignment.BottomCenter).padding(40.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Button(onClick = {
                    val capture = imageCapture ?: return@Button
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")).build()
                    capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: return
                            viewModel.viewModelScope.launch(Dispatchers.IO) { processCapturedImage(context, viewModel, savedUri); withContext(Dispatchers.Main) { viewModel.showToast("Captured! Take another or click Done.") } }
                        }
                        override fun onError(exc: ImageCaptureException) { Log.e("Camera", "Capture failed", exc) }
                    })
                }, modifier = Modifier.size(80.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(Color.White), border = BorderStroke(4.dp, Color(0xFF9CA3AF))) { Box(Modifier.size(64.dp).background(Color.White, CircleShape).border(4.dp, Color(0xFFD1D5DB), CircleShape)) }
                
                if (currentImagesCount > 0) {
                    IconButton(onClick = onClose, modifier = Modifier.size(64.dp).background(Color(0xFF10B981), CircleShape)) { Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }
}

@Composable
fun PdfPreviewOverlay(context: Context, document: SavedDocument, onClose: () -> Unit) {
    val pdfBytes = remember(document.pdfBase64) { try { Base64.decode(document.pdfBase64.substringAfter("base64,"), Base64.DEFAULT) } catch (e: Exception) { null } }
    val tmpFile = remember(pdfBytes) { if (pdfBytes != null) { val f = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf"); f.writeBytes(pdfBytes); f } else null }
    val renderer = remember(tmpFile) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && tmpFile != null) { try { val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer(pfd) } catch (e: Exception) { null } } else null }
    val pageCount = renderer?.pageCount ?: 0
    val renderMutex = remember { Mutex() } 
    DisposableEffect(Unit) { onDispose { renderer?.close(); tmpFile?.delete() } }

    Column(Modifier.fillMaxSize().background(Color(0xFFE5E7EB))) {
        Surface(color = Color(0xFF4F46E5), shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }; Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) { Text(document.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$pageCount Page(s)", color = Color.White.copy(0.8f), fontSize = 12.sp) }
            }
        }
        if (renderer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val state = rememberTransformableState { zoomChange, offsetChange, _ -> scale = (scale * zoomChange).coerceIn(1f, 4f); offset += offsetChange }
            LazyColumn(modifier = Modifier.fillMaxSize().transformable(state = state).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = 16.dp)) {
                items(pageCount) { pageIndex ->
                    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(pageIndex) {
                        withContext(Dispatchers.IO) {
                            renderMutex.withLock {
                                try {
                                    val page = renderer.openPage(pageIndex); val w = (page.width * 2.0).toInt(); val h = (page.height * 2.0).toInt()
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
                                    canvas.drawColor(android.graphics.Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                                    pageBitmap = bmp
                                } catch (e: Exception) { Log.e("PDF", "Error", e) }
                            }
                        }
                    }
                    Card(modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(0.95f), elevation = CardDefaults.cardElevation(8.dp)) {
                        if (pageBitmap != null) Image(bitmap = pageBitmap!!.asImageBitmap(), contentDescription = "Page ${pageIndex + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        else Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4F46E5)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfImportModal(viewModel: AppViewModel, onScan: () -> Unit, onRead: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).fillMaxWidth().clickable(enabled = false, onClick = {}), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Import PDF Options", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1F2937)); Spacer(Modifier.height(8.dp))
                Text("How do you want to process this PDF file?", fontSize = 14.sp, color = Color(0xFF9CA3AF)); Spacer(Modifier.height(24.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF4F46E5)), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) { Icon(Icons.Default.Scanner, null); Spacer(Modifier.width(8.dp)); Text("Scan & Extract Pages", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRead, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 16.dp), border = BorderStroke(1.dp, Color(0xFFA7F3D0))) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(8.dp)); Text("Save Directly as Reader", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text("Cancel", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun LoadingOverlay(title: String, description: String, progress: Float, progressText: String) {
    Box(Modifier.fillMaxSize().background(Color.White.copy(0.95f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            CircularProgressIndicator(Modifier.size(64.dp), color = Color(0xFF4F46E5), strokeWidth = 5.dp); Spacer(Modifier.height(24.dp))
            Text(title, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF1F2937)); Spacer(Modifier.height(8.dp))
            Text(description, fontSize = 14.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center); Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)), color = Color(0xFF4F46E5), trackColor = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(progressText, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF6B7280)); Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4F46E5))
                }
            }
        }
    }
}

// ==========================================
// CORE PROCESSING FUNCTIONS
// ==========================================
fun bitmapToBase64(bitmap: Bitmap): String {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
    return "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
}

fun decodeBase64(base64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(base64.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

suspend fun processImageUri(context: Context, viewModel: AppViewModel, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
            } else { MediaStore.Images.Media.getBitmap(context.contentResolver, uri) }
            val maxDim = 1600f; val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
            val scaledBmp = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            val dataUrl = bitmapToBase64(scaledBmp)
            val thumbBitmap = Bitmap.createScaledBitmap(scaledBmp, 200, (200f / scaledBmp.width * scaledBmp.height).toInt(), true)
            val thumbUrl = bitmapToBase64(thumbBitmap)
            withContext(Dispatchers.Main) { viewModel.addImage(ScanImage(dataUrl = dataUrl, thumbUrl = thumbUrl)) }
        } catch (e: Exception) { Log.e("Scanner", "Failed to process image", e) }
    }
}

suspend fun processCapturedImage(context: Context, viewModel: AppViewModel, uri: Uri) { processImageUri(context, viewModel, uri) }

suspend fun generatePDF(context: Context, viewModel: AppViewModel) {
    withContext(Dispatchers.IO) {
        val currentImages = viewModel.images.value
        if (currentImages.isEmpty()) return@withContext
        val filter = viewModel.selectedFilter.value
        val pageSizeSetting = viewModel.pageSize.value
        val document = android.graphics.pdf.PdfDocument()
        
        for ((index, img) in currentImages.withIndex()) {
            withContext(Dispatchers.Main) { viewModel.updateProgress(index + 1, currentImages.size) }
            val bytes = Base64.decode(img.dataUrl.substringAfter(","), Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val processedBmp = applyImageFilter(bmp, filter)
            val pageW = if (pageSizeSetting == PageSize.A4) 1190 else processedBmp.width
            val pageH = if (pageSizeSetting == PageSize.A4) 1684 else processedBmp.height
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
            val page = document.startPage(pageInfo)
            
            if (pageSizeSetting == PageSize.A4) {
                val ratio = minOf(pageW.toFloat() / processedBmp.width, pageH.toFloat() / processedBmp.height)
                val newW = (processedBmp.width * ratio).toInt(); val newH = (processedBmp.height * ratio).toInt()
                val scaledBmp = Bitmap.createScaledBitmap(processedBmp, newW, newH, true)
                val left = (pageW - newW) / 2f; val top = (pageH - newH) / 2f
                page.canvas.drawColor(android.graphics.Color.WHITE); page.canvas.drawBitmap(scaledBmp, left, top, null)
            } else { page.canvas.drawBitmap(processedBmp, 0f, 0f, null) }
            document.finishPage(page)
        }
        val baos = ByteArrayOutputStream(); document.writeTo(baos); document.close()
        val pdfBase64 = "data:application/pdf;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val savedDoc = SavedDocument(id = "doc_${System.currentTimeMillis()}", name = "RasScan_$timestamp.pdf", date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date()), pages = currentImages.size, thumb = currentImages.firstOrNull()?.thumbUrl ?: "", pdfBase64 = pdfBase64)
        withContext(Dispatchers.Main) { viewModel.addSavedDocument(context, savedDoc); viewModel.clearImages(); viewModel.showToast("PDF Saved!") }
    }
}

fun applyImageFilter(bitmap: Bitmap, filter: ImageFilter): Bitmap {
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true); val canvas = Canvas(result); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (filter) {
        ImageFilter.NONE -> { }
        ImageFilter.MAGIC_PRO -> { val cm = ColorMatrix(floatArrayOf(1.3f, 0f, 0f, 0f, 20f, 0f, 1.3f, 0f, 0f, 20f, 0f, 0f, 1.3f, 0f, 20f, 0f, 0f, 0f, 1f, 0f)); paint.colorFilter = ColorMatrixColorFilter(cm); canvas.drawBitmap(result, 0f, 0f, paint) }
        ImageFilter.PRINT_PRO -> { val cm = ColorMatrix().apply { setSaturation(0f) }; val contrast = ColorMatrix(floatArrayOf(1.8f, 0f, 0f, 0f, -60f, 0f, 1.8f, 0f, 0f, -60f, 0f, 0f, 1.8f, 0f, -60f, 0f, 0f, 0f, 1f, 0f)); cm.postConcat(contrast); paint.colorFilter = ColorMatrixColorFilter(cm); canvas.drawBitmap(result, 0f, 0f, paint) }
        ImageFilter.CLEAR_PRO -> { val cm = ColorMatrix(floatArrayOf(1.5f, 0f, 0f, 0f, 30f, 0f, 1.5f, 0f, 0f, 30f, 0f, 0f, 1.5f, 0f, 30f, 0f, 0f, 0f, 1f, 0f)); paint.colorFilter = ColorMatrixColorFilter(cm); canvas.drawBitmap(result, 0f, 0f, paint) }
        ImageFilter.SUPER_BW -> { val cm = ColorMatrix().apply { setSaturation(0f) }; val contrast = ColorMatrix(floatArrayOf(2.5f, 0f, 0f, 0f, -120f, 0f, 2.5f, 0f, 0f, -120f, 0f, 0f, 2.5f, 0f, -120f, 0f, 0f, 0f, 1f, 0f)); cm.postConcat(contrast); paint.colorFilter = ColorMatrixColorFilter(cm); canvas.drawBitmap(result, 0f, 0f, paint) }
    }
    return result
}

suspend fun mergeSelectedPdfs(context: Context, viewModel: AppViewModel) {
    withContext(Dispatchers.IO) {
        val selectedIds = viewModel.selectedDocsForMerge
        val docsToMerge = viewModel.savedDocuments.value.filter { selectedIds.contains(it.id) }
        if (docsToMerge.size < 2) return@withContext
        try {
            val mergedDocument = android.graphics.pdf.PdfDocument()
            var totalPages = 0
            for ((docIndex, doc) in docsToMerge.withIndex()) {
                withContext(Dispatchers.Main) { viewModel.updateProgress(docIndex + 1, docsToMerge.size) }
                val pdfBytes = Base64.decode(doc.pdfBase64.substringAfter(","), Base64.DEFAULT)
                val tmpFile = File(context.cacheDir, "temp_merge_${System.currentTimeMillis()}.pdf")
                tmpFile.writeBytes(pdfBytes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i); val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, totalPages + 1).create()
                        val newPage = mergedDocument.startPage(pageInfo); newPage.canvas.drawBitmap(bmp, 0f, 0f, null); mergedDocument.finishPage(newPage); totalPages++
                    }
                    renderer.close(); pfd.close()
                }
                tmpFile.delete()
            }
            val baos = ByteArrayOutputStream(); mergedDocument.writeTo(baos); mergedDocument.close()
            val pdfBase64 = "data:application/pdf;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val savedDoc = SavedDocument(id = "doc_${System.currentTimeMillis()}", name = "RasScan_Merged_$timestamp.pdf", date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date()), pages = totalPages, thumb = docsToMerge.first().thumb, pdfBase64 = pdfBase64)
            withContext(Dispatchers.Main) { viewModel.addSavedDocument(context, savedDoc); viewModel.toggleSelectionMode(); viewModel.showToast("Merged successfully!") }
        } catch (e: Exception) { Log.e("Scanner", "Merge failed", e); withContext(Dispatchers.Main) { viewModel.showToast("Failed to merge PDFs") } }
    }
}

suspend fun importPdfAsScan(context: Context, viewModel: AppViewModel, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri); val bytes = inputStream?.readBytes(); inputStream?.close()
            if (bytes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val tmpFile = File(context.cacheDir, "temp_import_scan_${System.currentTimeMillis()}.pdf"); tmpFile.writeBytes(bytes)
                val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY); val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i); val bmp = Bitmap.createBitmap((page.width * 1.5).toInt(), (page.height * 1.5).toInt(), Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                    val dataUrl = bitmapToBase64(bmp); val thumbBmp = Bitmap.createScaledBitmap(bmp, 200, (200f / bmp.width * bmp.height).toInt(), true); val thumbUrl = bitmapToBase64(thumbBmp)
                    withContext(Dispatchers.Main) { viewModel.addImage(ScanImage(dataUrl = dataUrl, thumbUrl = thumbUrl)) }
                }
                renderer.close(); pfd.close(); tmpFile.delete()
                withContext(Dispatchers.Main) { viewModel.showToast("PDF Pages extracted!") }
            }
        } catch (e: Exception) { Log.e("Scanner", "PDF scan import failed", e) }
    }
}

suspend fun importPdfAsReader(context: Context, viewModel: AppViewModel, uri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri); val bytes = inputStream?.readBytes(); inputStream?.close()
            if (bytes != null) {
                val pdfBase64 = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.DEFAULT)
                var thumbBase64 = ""; var pageCount = 1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val tmpFile = File(context.cacheDir, "temp_thumb_${System.currentTimeMillis()}.pdf"); tmpFile.writeBytes(bytes)
                    val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY); val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        val page = renderer.openPage(0); val bmp = Bitmap.createBitmap(200, (200f / page.width * page.height).toInt(), Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                        thumbBase64 = bitmapToBase64(bmp)
                    }
                    renderer.close(); pfd.close(); tmpFile.delete()
                }
                var name = uri.path?.substringAfterLast('/') ?: "Imported.pdf"
                if (uri.scheme == "content") { context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) name = cursor.getString(idx) } } }
                val savedDoc = SavedDocument(id = "doc_${System.currentTimeMillis()}", name = name, date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date()), pages = pageCount, thumb = thumbBase64, pdfBase64 = pdfBase64)
                withContext(Dispatchers.Main) { viewModel.addSavedDocument(context, savedDoc); viewModel.showToast("Saved to History!") }
            }
        } catch (e: Exception) { Log.e("Scanner", "PDF reader import failed", e) }
    }
}

fun sharePdf(context: Context, document: SavedDocument) {
    try {
        val bytes = Base64.decode(document.pdfBase64.substringAfter("base64,"), Base64.DEFAULT)
        val dir = File(context.cacheDir, "shared_pdfs"); dir.mkdirs(); val file = File(dir, document.name); file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (e: Exception) { Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show() }
}

fun saveToLocal(context: Context, document: SavedDocument) {
    try {
        val bytes = Base64.decode(document.pdfBase64.substringAfter("base64,"), Base64.DEFAULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, document.name); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/RasScanner") }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
            Toast.makeText(context, "Saved to Documents/RasScanner", Toast.LENGTH_LONG).show()
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RasScanner"); dir.mkdirs(); val file = File(dir, document.name); file.writeBytes(bytes)
            Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) { Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show() }
}
