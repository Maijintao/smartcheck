package com.smartcheck.handdemo

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartcheck.handdemo.databinding.ActivityMainBinding
import com.smartcheck.sdk.HandDetector
import com.smartcheck.sdk.HandSdkAuth
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

        appendLog("Demo started")
        appendLog("Tap Init SDK first, then Run Detect")

        binding.btnInit.setOnClickListener {
            worker.execute {
                val resultText = runCatching {
                    HandSdkAuth.configure(serverUrl = "http://112.74.39.40:8080", required = false)
                    val code = HandDetector.init(applicationContext)
                    "Init result: $code (0=ok, -1=failed, -2=auth failed)"
                }.getOrElse { throwable ->
                    "Init exception: ${throwable.message}"
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
                        "Detect result: empty (device may not support RKNN runtime)"
                    } else {
                        buildString {
                            appendLine("Detect result: hands=${result.size}")
                            result.forEachIndexed { index, hand ->
                                appendLine("#$index label=${hand.label}, score=${"%.2f".format(hand.score)}, foreign=${hand.foreignObjects.size}")
                            }
                        }.trimEnd()
                    }
                }.getOrElse { throwable ->
                    "Detect exception: ${throwable.message}"
                }
                runOnUiThread { appendLog(resultText) }
            }
        }

        binding.btnRelease.setOnClickListener {
            worker.execute {
                val resultText = runCatching {
                    HandDetector.release()
                    "Release done"
                }.getOrElse { throwable ->
                    "Release exception: ${throwable.message}"
                }
                runOnUiThread { appendLog(resultText) }
            }
        }
    }

    override fun onDestroy() {
        worker.execute { runCatching { HandDetector.release() } }
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