package id.cachet.wallet.android.ui.verification

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import id.cachet.wallet.android.ui.theme.*
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    demoMode: Boolean = false,
    onCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Demo mode: auto-scan after 2 seconds
    if (demoMode) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            if (!scanned) {
                scanned = true
                onCodeScanned("cachet://verify?request_uri=demo&pack=childcare")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F0F)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ──
                Spacer(modifier = Modifier.height(48.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(
                        text = "Scan to verify",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = "Point your camera at their Cachet QR code",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Camera viewfinder ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .aspectRatio(311f / 380f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1A1A1A)
                    ) {
                        if (hasCameraPermission && !demoMode) {
                            CameraPreview(
                                torchEnabled = torchEnabled,
                                onCodeScanned = { code ->
                                    if (!scanned) {
                                        scanned = true
                                        onCodeScanned(code)
                                    }
                                }
                            )
                        } else {
                            // Placeholder for demo or no-permission
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (demoMode) "Scanning..." else "Camera permission required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // Viewfinder corner brackets overlay
                    ViewfinderOverlay(modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Instruction card ──
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = BrandPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDD12",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "You'll review before sharing",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White
                            )
                            Text(
                                text = "Nothing is shared without your consent",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Torch toggle ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = { torchEnabled = !torchEnabled },
                        containerColor = BrandPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.FlashlightOn,
                            contentDescription = "Toggle torch"
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Torch",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cornerLen = 24.dp.toPx()
        val cornerRadius = 8.dp.toPx()
        val strokeW = 3.dp.toPx()
        val pad = 24.dp.toPx()
        val color = BrandAccent

        val left = pad
        val top = pad
        val right = size.width - pad
        val bottom = size.height - pad

        // Top-left
        drawLine(color, Offset(left, top + cornerLen), Offset(left, top + cornerRadius), strokeW, StrokeCap.Round)
        drawLine(color, Offset(left + cornerRadius, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
        // Top-right
        drawLine(color, Offset(right - cornerLen, top), Offset(right - cornerRadius, top), strokeW, StrokeCap.Round)
        drawLine(color, Offset(right, top + cornerRadius), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
        // Bottom-left
        drawLine(color, Offset(left, bottom - cornerLen), Offset(left, bottom - cornerRadius), strokeW, StrokeCap.Round)
        drawLine(color, Offset(left + cornerRadius, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
        // Bottom-right
        drawLine(color, Offset(right - cornerLen, bottom), Offset(right - cornerRadius, bottom), strokeW, StrokeCap.Round)
        drawLine(color, Offset(right, bottom - cornerLen), Offset(right, bottom - cornerRadius), strokeW, StrokeCap.Round)

        // Scan line (center)
        val centerY = size.height / 2
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(left + 8.dp.toPx(), centerY),
            end = Offset(right - 8.dp.toPx(), centerY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun CameraPreview(
    torchEnabled: Boolean,
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val previewView = remember { PreviewView(context) }

    // Toggle torch when state changes
    LaunchedEffect(torchEnabled) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    // Bind camera using suspend API (CameraX 1.5+)
    LaunchedEffect(Unit) {
        try {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        QrCodeAnalyzer(onCodeScanned)
                    )
                }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )
        } catch (_: Exception) {
            // Camera binding failed
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private class QrCodeAnalyzer(
    private val onCodeFound: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        )
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val source = PlanarYUVLuminanceSource(
            bytes, imageProxy.width, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decodeWithState(binaryBitmap)
            onCodeFound(result.text)
        } catch (_: Exception) {
            // No QR code found in this frame
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}
