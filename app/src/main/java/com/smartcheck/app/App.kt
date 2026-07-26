package com.smartcheck.app

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.smartcheck.app.api.KtorServerManager
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import com.smartcheck.app.data.sync.SyncScheduler
import com.smartcheck.app.data.upload.DeviceHeartbeatManager
import com.smartcheck.app.data.upload.NetworkMonitor
import com.smartcheck.app.data.upload.PendingUploadManager
import com.smartcheck.app.data.upload.RecordUploadRetryScheduler
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.utils.UsbCameraHelper
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.HiltAndroidApp
import com.smartcheck.app.utils.FileLoggingTree
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class App : Application(), CameraXConfig.Provider {

    @Inject
    lateinit var ktorServerManager: KtorServerManager

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var pendingUploadManager: PendingUploadManager

    @Inject
    lateinit var deviceHeartbeatManager: DeviceHeartbeatManager

    @Inject
    lateinit var recordUploadRetryScheduler: RecordUploadRetryScheduler

    @Inject
    lateinit var employeeSyncEngine: EmployeeSyncEngine

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        // 先初始化 Timber（确保日志可用）
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 同时记录到文件（带日志轮转和自动清理）
        Timber.plant(FileLoggingTree(this))

        Timber.d("[App] Application onCreate 开始")

        // 初始化设备授权（MAC 白名单验证）
        DeviceAuth.init(this, "http://112.74.39.40:8080")

        Timber.d("SmartCheck Application Started")

        installCrashHandler()

        initHandDetector()

        // 启动 Ktor API 服务器
        startKtorServer()

        // 启动网络监听，联网时自动上传离线记录
        startNetworkMonitor()

        // 应用启动时检查并上传积压的离线记录
        startPendingUploadQueue()

        // 启动设备心跳管理器（平台保活）
        startDeviceHeartbeat()

        // 启动晨检记录重试调度器（平台断联恢复后自动重发）
        startRecordUploadRetry()

        // 启动员工同步引擎
        startEmployeeSync()
    }

    private fun startKtorServer() {
        try {
            // 延迟启动，确保依赖注入完成
            android.os.Handler(mainLooper).postDelayed({
                if (::ktorServerManager.isInitialized) {
                    ktorServerManager.start()
                    Timber.i("Ktor server auto-started on app launch")
                } else {
                    Timber.w("KtorServerManager not initialized yet, will retry...")
                    // 再延迟 2 秒重试
                    android.os.Handler(mainLooper).postDelayed({
                        if (::ktorServerManager.isInitialized) {
                            ktorServerManager.start()
                            Timber.i("Ktor server started on retry")
                        } else {
                            Timber.e("KtorServerManager failed to initialize")
                        }
                    }, 2000)
                }
            }, 1000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Ktor server")
        }
    }

    private fun startNetworkMonitor() {
        try {
            android.os.Handler(mainLooper).postDelayed({
                if (::networkMonitor.isInitialized) {
                    networkMonitor.start()
                    Timber.i("NetworkMonitor started")
                } else {
                    Timber.w("NetworkMonitor not initialized yet, will retry...")
                    android.os.Handler(mainLooper).postDelayed({
                        if (::networkMonitor.isInitialized) {
                            networkMonitor.start()
                            Timber.i("NetworkMonitor started on retry")
                        }
                    }, 2000)
                }
            }, 1500)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start NetworkMonitor")
        }
    }

    private fun startPendingUploadQueue() {
        try {
            android.os.Handler(mainLooper).postDelayed({
                if (::pendingUploadManager.isInitialized) {
                    pendingUploadManager.enqueue(0L)
                    Timber.i("Pending upload queue triggered on app launch")
                } else {
                    Timber.w("PendingUploadManager not initialized yet, will retry...")
                    android.os.Handler(mainLooper).postDelayed({
                        if (::pendingUploadManager.isInitialized) {
                            pendingUploadManager.enqueue(0L)
                            Timber.i("Pending upload queue triggered on retry")
                        }
                    }, 3000)
                }
            }, 3000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start pending upload queue")
        }
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logsDir = File(filesDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(System.currentTimeMillis())
                val file = File(logsDir, "crash_$stamp.txt")
                
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                
                pw.println("=== SmartCheck Crash Report ===")
                pw.println("Time: $stamp")
                pw.println("Thread: ${thread.name} (id: ${thread.id})")
                pw.println("Priority: ${thread.priority}")
                pw.println()
                
                pw.println("--- Device Info ---")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                pw.println("Hardware: ${Build.HARDWARE}")
                pw.println("Board: ${Build.BOARD}")
                pw.println()
                
                pw.println("--- Memory Info ---")
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                pw.println("Total: ${totalMemory / (1024*1024)} MB")
                pw.println("Free: ${freeMemory / (1024*1024)} MB")
                pw.println("Max: ${maxMemory / (1024*1024)} MB")
                pw.println("Used: ${(totalMemory - freeMemory) / (1024*1024)} MB")
                pw.println()
                
                pw.println("--- Stack Trace ---")
                throwable.printStackTrace(pw)
                pw.flush()
                
                file.writeText(sw.toString())
                
                Timber.tag("Crash")
                    .e("CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
                Timber.tag("Crash")
                    .e("Crash log saved: ${file.absolutePath}")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to write crash log")
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private fun startDeviceHeartbeat() {
        try {
            android.os.Handler(mainLooper).postDelayed({
                if (::deviceHeartbeatManager.isInitialized) {
                    deviceHeartbeatManager.start()
                    Timber.i("DeviceHeartbeatManager started")
                } else {
                    Timber.w("DeviceHeartbeatManager not initialized yet, will retry...")
                    android.os.Handler(mainLooper).postDelayed({
                        if (::deviceHeartbeatManager.isInitialized) {
                            deviceHeartbeatManager.start()
                            Timber.i("DeviceHeartbeatManager started on retry")
                        }
                    }, 3000)
                }
            }, 5000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start DeviceHeartbeatManager")
        }
    }

    private fun startRecordUploadRetry() {
        try {
            android.os.Handler(mainLooper).postDelayed({
                if (::recordUploadRetryScheduler.isInitialized) {
                    recordUploadRetryScheduler.start()
                    Timber.i("RecordUploadRetryScheduler started")
                } else {
                    Timber.w("RecordUploadRetryScheduler not initialized yet, will retry...")
                    android.os.Handler(mainLooper).postDelayed({
                        if (::recordUploadRetryScheduler.isInitialized) {
                            recordUploadRetryScheduler.start()
                            Timber.i("RecordUploadRetryScheduler started on retry")
                        } else {
                            Timber.e("RecordUploadRetryScheduler failed to initialize")
                        }
                    }, 3000)
                }
            }, 8000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start RecordUploadRetryScheduler")
        }
    }

    private fun startEmployeeSync() {
        try {
            // 启动时触发首次同步（延迟 6s，等 Ktor 启动和网络稳定）
            android.os.Handler(mainLooper).postDelayed({
                if (::employeeSyncEngine.isInitialized) {
                    appScope.launch {
                        try {
                            employeeSyncEngine.triggerSync()
                            Timber.i("Employee sync triggered on app launch")
                        } catch (e: Exception) {
                            Timber.w(e, "Employee sync failed on app launch")
                        }
                    }
                } else {
                    Timber.w("EmployeeSyncEngine not initialized yet")
                }
            }, 6000)

            // 启动 30 秒定时同步
            if (::syncScheduler.isInitialized) {
                syncScheduler.startPeriodicSync(appScope, intervalMs = 30_000L)
                Timber.i("SyncScheduler started (30s interval)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start employee sync")
        }
    }

    private fun initHandDetector() {
        try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val product = Build.PRODUCT.lowercase()
            val isRockchip = hardware.contains("rk") || board.contains("rk") || product.contains("rk")
            if (!isRockchip) {
                Timber.w("Skipping HandDetector init on non-RK device. hardware=$hardware board=$board product=$product")
                return
            }

            val result = HandDetector.init(this)
            if (result == 0) {
                Timber.i("HandDetector initialized successfully")
            } else {
                Timber.e("HandDetector initialization failed with code: $result")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize HandDetector")
        }
    }


    override fun getCameraXConfig(): CameraXConfig {
        val defaultConfig = Camera2Config.defaultConfig()
        val builder = CameraXConfig.Builder.fromConfig(defaultConfig)

        // 硬编码 PID/VID 绑定摄像头
        val preferredIds = mutableSetOf<String>()

        val faceParsed = UsbCameraHelper.parseVidPid("0BDA:271A")
        if (faceParsed != null) {
            val found = UsbCameraHelper.findCameraIdByVidPid(this, faceParsed.first, faceParsed.second)
            if (found != null) {
                preferredIds.add(found)
                Timber.i("Face camera bound by VID/PID: 0BDA:271A -> $found")
            }
        }
        val handParsed = UsbCameraHelper.parseVidPid("0BDA:D567")
        if (handParsed != null) {
            val found = UsbCameraHelper.findCameraIdByVidPid(this, handParsed.first, handParsed.second)
            if (found != null) {
                preferredIds.add(found)
                Timber.i("Hand camera bound by VID/PID: 0BDA:D567 -> $found")
            }
        }

        // 若 PID/VID 查找失败，回退到默认 camera ID
        if (preferredIds.isEmpty()) {
            preferredIds.addAll(setOf("109", "111"))
            Timber.w("PID/VID lookup failed, falling back to default camera IDs")
        }

        builder.setAvailableCamerasLimiter(
            CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter {
                        runCatching { Camera2CameraInfo.from(it).cameraId in preferredIds }
                            .getOrDefault(false)
                    }
                }
                .build()
        )
        return builder.build()
    }
}
