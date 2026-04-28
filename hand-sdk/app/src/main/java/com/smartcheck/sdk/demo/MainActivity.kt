package com.smartcheck.sdk.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartcheck.sdk.HandDetector
import com.smartcheck.sdk.HandSdkAuth
import com.smartcheck.sdk.demo.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val worker = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appendLog("Demo 已启动")
        appendLog("请先点击“初始化 SDK”，再点击“执行检测”")

        binding.btnInit.setOnClickListener {
            worker.execute {
                val resultText = runCatching {
                    // Demo 默认关闭服务端鉴权，避免因网络导致初始化失败。
                    HandSdkAuth.configure(serverUrl = "http://112.74.39.40:8080", required = false)
                    val code = HandDetector.init(applicationContext)
                    "初始化返回码: $code (0=成功, -1=失败, -2=鉴权失败)"
                }.getOrElse { throwable ->
                    "初始化抛出异常: ${throwable.message}"
                }
                runOnUiThread { appendLog(resultText) }
            }
        }

        binding.btnDetect.setOnClickListener {
            worker.execute {
                val resultText = runCatching {
                    val bitmap = createDemoBitmap()
                    val result = HandDetector.detect(bitmap)
                    if (result.isEmpty()) {
                        "检测完成: 未检测到目标（或设备不支持 RKNN 推理）"
                    } else {
                        buildString {
                            appendLine("检测完成: 手部数量=${result.size}")
                            result.forEachIndexed { index, hand ->
                                appendLine(
                                    "#$index label=${hand.label}, score=${"%.2f".format(hand.score)}, foreignObjects=${hand.foreignObjects.size}"
                                )
                            }
                        }.trimEnd()
                    }
                }.getOrElse { throwable ->
                    "检测抛出异常: ${throwable.message}"
                }
                runOnUiThread { appendLog(resultText) }
            }
        }

        binding.btnRelease.setOnClickListener {
            worker.execute {
                val resultText = runCatching {
                    HandDetector.release()
                    "资源已释放"
                }.getOrElse { throwable ->
                    "释放抛出异常: ${throwable.message}"
                }
                runOnUiThread { appendLog(resultText) }
            }
        }
    }

    override fun onDestroy() {
        worker.execute {
            runCatching { HandDetector.release() }
        }
        worker.shutdown()
        super.onDestroy()
    }

    private fun createDemoBitmap(): Bitmap {
        val width = 640
        val height = 480
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
    }

    private fun appendLog(message: String) {
        val now = dateFormat.format(Date())
        val line = "[$now] $message"
        val current = binding.tvLog.text?.toString().orEmpty()
        binding.tvLog.text = if (current.isBlank()) line else "$line\n$current"
    }
}
