package com.smartcheck.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartcheck.app.ui.components.CameraPreview
import com.smartcheck.app.ui.components.HandOverlay
import com.smartcheck.app.ui.theme.SmartCheckTheme
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.sdk.HandDetector
import com.smartcheck.sdk.HandInfo
import com.smartcheck.sdk.HandSdkAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class HandSdkDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartCheckTheme {
                HandSdkDemoScreen()
            }
        }
    }

    override fun onDestroy() {
        HandDetector.release()
        super.onDestroy()
    }
}

@Composable
private fun HandSdkDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detecting = remember { AtomicBoolean(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var initCode by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var initMessage by remember { mutableStateOf("等待初始化") }
    var handInfos by remember { mutableStateOf<List<HandInfo>>(emptyList()) }
    var frameWidth by remember { mutableIntStateOf(0) }
    var frameHeight by remember { mutableIntStateOf(0) }
    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }
    var isFrontCamera by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return@LaunchedEffect
        }

        HandSdkAuth.configure(
            serverUrl = DeviceAuth.SERVER_URL,
            required = true,
        )

        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val product = Build.PRODUCT.lowercase()
        val isRockchip = hardware.contains("rk") || board.contains("rk") || product.contains("rk")
        if (!isRockchip) {
            initMessage = "当前设备非 Rockchip，Demo 仅做流程展示"
        }

        val ret = HandDetector.init(context)
        initCode = ret
        initMessage = when (ret) {
            HandDetector.INIT_OK -> "HandDetector 初始化成功"
            HandDetector.INIT_AUTH_FAILED -> "授权失败（请检查白名单与服务端）"
            else -> "初始化失败 code=$ret"
        }

        if (ret != HandDetector.INIT_OK) {
            Toast.makeText(context, initMessage, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC111827))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Hand SDK Demo",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = initMessage,
                    color = Color(0xFFD1D5DB),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "检测到手数量: ${handInfos.size}",
                    color = Color(0xFF86EFAC),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(onClick = {
                handInfos = emptyList()
                val ret = HandDetector.init(context)
                initCode = ret
                initMessage = if (ret == HandDetector.INIT_OK) "HandDetector 初始化成功" else "初始化失败 code=$ret"
            }) {
                Text("重新初始化")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (hasCameraPermission && initCode == HandDetector.INIT_OK) {
                CameraPreview(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    onFrameAnalyzed = { bitmap: Bitmap ->
                        frameWidth = bitmap.width
                        frameHeight = bitmap.height
                        if (!detecting.compareAndSet(false, true)) {
                            return@CameraPreview
                        }
                        scope.launch(Dispatchers.Default) {
                            try {
                                val result = HandDetector.detect(bitmap)
                                launch(Dispatchers.Main) {
                                    handInfos = result
                                }
                            } catch (t: Throwable) {
                                Timber.e(t, "Hand demo detect failed")
                            } finally {
                                detecting.set(false)
                            }
                        }
                    },
                    onCameraFacingChanged = { front ->
                        isFrontCamera = front
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .onSizeChanged {
                            viewWidth = it.width
                            viewHeight = it.height
                        },
                ) {
                    HandOverlay(
                        handInfos = handInfos,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight,
                        contentScale = ContentScale.Fit,
                        mirrorX = isFrontCamera,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (!hasCameraPermission) {
                            "需要相机权限后才能运行 Hand SDK Demo"
                        } else {
                            initMessage
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!hasCameraPermission) {
                        Button(
                            modifier = Modifier.padding(top = 12.dp),
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        ) {
                            Text("授予相机权限")
                        }
                    }
                }
            }
        }
    }
}
