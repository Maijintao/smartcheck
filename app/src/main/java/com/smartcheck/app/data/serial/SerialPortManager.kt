package com.smartcheck.app.data.serial

import android.serialport.SerialPort
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 串口管理器
 *
 * 负责与硬件设备通信：
 * - 红外测温模块（/dev/ttyS7, 115200）
 * - 蜂鸣器
 * - 继电器（开门）
 *
 * 协议说明：
 * - 上电后模块每 300ms 左右发送一次人体温度数据
 * - 输出格式：{36.53}（以"{"开头，"}"结尾）
 * - 温度以字符形式显示
 *
 * 实现说明：
 * 使用 android-serialport 库通过 JNI/termios 正确配置串口，
 * 避免 Android SELinux 限制导致 stty 命令失效的问题。
 */
@Singleton
class SerialPortManager @Inject constructor() {

    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isOpen = false

    private var ledSerialPort: SerialPort? = null
    private var ledOutputStream: OutputStream? = null
    private var isLedOpen = false
    private var lastLedWriteAt: Long = 0L

    companion object {
        const val TAG = "SerialPortManager"
        const val DEFAULT_DEVICE_PATH = "/dev/ttyS7"
        const val DEFAULT_BAUD_RATE = 115200

        const val LED_DEVICE_PATH = "/dev/ttyS6"
        const val LED_BAUD_RATE = 9600
        private const val LED_CMD_INTERVAL_MS = 80L

        private val CMD_LED_ALL_ON = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x31, 0x41, 0x30
        )
        private val CMD_LED_ALL_OFF = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x30, 0x41, 0x30
        )
        private val CMD_LED_FACE_ON = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x33, 0x41, 0x30
        )
        private val CMD_LED_FACE_OFF = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x32, 0x41, 0x30
        )
        private val CMD_LED_HAND_ON = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x35, 0x41, 0x30
        )
        private val CMD_LED_HAND_OFF = byteArrayOf(
            0x41, 0x30, 0x30, 0x31, 0x30, 0x34, 0x41, 0x30
        )
    }

    private var currentDevicePath = DEFAULT_DEVICE_PATH
    private var currentBaudRate = DEFAULT_BAUD_RATE

    fun configure(path: String, baudRate: Int) {
        currentDevicePath = path
        currentBaudRate = baudRate
        Timber.tag(TAG).d("Serial port configured: $path @ $baudRate")
    }

    /**
     * 打开串口
     * 使用 SerialPort 库通过 JNI 正确配置 termios（波特率、8N1 等），
     * 避免 Android 上 stty 命令因 SELinux 限制失效的问题。
     */
    fun open(portPath: String = currentDevicePath, baudRate: Int = currentBaudRate): Boolean {
        if (isOpen) {
            Log.d(TAG, "Serial port already open")
            return true
        }

        return try {
            Log.d(TAG, "=== Opening serial port ===")
            Log.d(TAG, "Path: $portPath, BaudRate: $baudRate")

            val deviceFile = File(portPath)
            if (!deviceFile.exists()) {
                Log.e(TAG, "!!! Serial port device NOT found: $portPath")
                return false
            }

            // SerialPort 构造函数内部通过 JNI 调用 open() + termios ioctl，
            // 正确设置波特率和 8N1 帧格式，这是 Android 上串口通信的标准做法
            serialPort = SerialPort(deviceFile, baudRate)
            inputStream = serialPort!!.inputStream
            outputStream = serialPort!!.outputStream

            isOpen = true
            Log.i(TAG, "=== Serial port opened successfully: $portPath @ $baudRate ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "!!! Failed to open serial port: ${e.message}")
            close()
            false
        }
    }

    fun close() {
        Timber.tag(TAG).d("Closing serial port")
        try {
            isOpen = false
            inputStream = null
            outputStream = null
            serialPort?.tryClose()
            serialPort = null

            closeLedPort()

            Timber.tag(TAG).i("Serial port closed")
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error closing serial port: ${e.message}")
        }
    }

    @Synchronized
    private fun openLedPort(): Boolean {
        if (isLedOpen && ledOutputStream != null) return true

        return try {
            val deviceFile = File(LED_DEVICE_PATH)
            if (!deviceFile.exists()) {
                Timber.tag(TAG).e("LED serial port device NOT found: $LED_DEVICE_PATH")
                return false
            }

            ledSerialPort = SerialPort(deviceFile, LED_BAUD_RATE)
            ledOutputStream = ledSerialPort?.outputStream
            isLedOpen = ledOutputStream != null
            if (isLedOpen) {
                Timber.tag(TAG).i("LED serial port opened: $LED_DEVICE_PATH @ $LED_BAUD_RATE")
            }
            isLedOpen
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open LED serial port")
            closeLedPort()
            false
        }
    }

    @Synchronized
    private fun closeLedPort() {
        try {
            isLedOpen = false
            ledOutputStream = null
            ledSerialPort?.tryClose()
            ledSerialPort = null
            Timber.tag(TAG).d("LED serial port closed")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error closing LED serial port")
        }
    }

    @Synchronized
    private fun sendLedCommand(command: ByteArray, label: String): Boolean {
        if (tryWriteLedCommand(command, label)) return true

        // 某些设备在串口短暂异常后需重开端口再发一次。
        closeLedPort()
        if (tryWriteLedCommand(command, "$label(retry)")) return true

        Timber.tag(TAG).e("LED command failed after retry: $label")
        return false
    }

    private fun tryWriteLedCommand(command: ByteArray, label: String): Boolean {
        if (!openLedPort()) {
            Timber.tag(TAG).w("Skip LED command, LED port not ready: $label")
            return false
        }

        return try {
            val now = System.currentTimeMillis()
            val waitMs = LED_CMD_INTERVAL_MS - (now - lastLedWriteAt)
            if (waitMs > 0) {
                Thread.sleep(waitMs)
            }
            ledOutputStream?.write(command)
            ledOutputStream?.flush()
            lastLedWriteAt = System.currentTimeMillis()
            val hex = command.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            Timber.tag(TAG).d("LED command sent: $label")
            Timber.tag(TAG).d("LED command bytes: $hex")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send LED command: $label")
            false
        }
    }

    fun ledAllOn(): Boolean = sendLedCommand(CMD_LED_ALL_ON, "ALL_ON")

    fun ledAllOff(): Boolean = sendLedCommand(CMD_LED_ALL_OFF, "ALL_OFF")

    fun ledFaceOn(): Boolean {
        if (sendLedCommand(CMD_LED_FACE_ON, "FACE_ON")) return true
        Timber.tag(TAG).w("FACE_ON failed, fallback to ALL_ON")
        return sendLedCommand(CMD_LED_ALL_ON, "ALL_ON_FALLBACK_FACE")
    }

    fun ledFaceOff(): Boolean = sendLedCommand(CMD_LED_FACE_OFF, "FACE_OFF")

    fun ledHandOn(): Boolean {
        if (sendLedCommand(CMD_LED_HAND_ON, "HAND_ON")) return true
        Timber.tag(TAG).w("HAND_ON failed, fallback to ALL_ON")
        return sendLedCommand(CMD_LED_ALL_ON, "ALL_ON_FALLBACK_HAND")
    }

    fun ledHandOff(): Boolean = sendLedCommand(CMD_LED_HAND_OFF, "HAND_OFF")

    fun getAvailableDevices(): List<String> {
        return try {
            val devices = mutableListOf<String>()
            val devDir = File("/dev")
            devDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("tty") && (file.name.contains("S") || file.name.contains("USB"))) {
                    devices.add(file.absolutePath)
                }
            }
            devices
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * 读取温度数据流
     * @return 温度值 Flow（单位：摄氏度）
     *
     * 协议格式：{36.53}
     * - 以"{"开头，"}"结尾，内容固定 5 个 ASCII 字符
     * - 使用滚动缓冲区处理跨包拆包问题（同 Demo 实现）
     */
    fun readTemperature(): Flow<Float> = flow {
        Log.d(TAG, "=== Starting readTemperature ===")

        if (!isOpen || inputStream == null) {
            Log.w(TAG, "!!! Serial port NOT open, cannot read temperature")
            return@flow
        }

        val buffer = ByteArray(1024)
        // 滚动缓冲区：跨多次 read() 累积数据，正确处理跨包拆包
        val receiveBuffer = StringBuilder()
        var lastEmitTime = 0L
        var lastLogTime = 0L
        val emitIntervalMs = 500L   // emit 节流：最高 2 次/秒，上层 take(N) 足够
        val logIntervalMs = 5000L   // 日志节流：每 5 秒最多一条有效温度日志

        while (isOpen && inputStream != null) {
            try {
                val available = inputStream?.available() ?: 0

                if (available > 0) {
                    val bytesRead = inputStream?.read(buffer) ?: 0
                    if (bytesRead > 0) {
                        // 与 Demo 一致，用 ASCII 解码（温度协议为纯 ASCII）
                        val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        receiveBuffer.append(chunk)

                        // 解析所有完整的 {XX.XX} token
                        var startIdx = 0
                        while (startIdx < receiveBuffer.length) {
                            val open = receiveBuffer.indexOf('{', startIdx)
                            if (open == -1) break
                            val close = receiveBuffer.indexOf('}', open)
                            if (close == -1) break  // 尚未收到完整 token，等下次 read

                            // 完整 token 固定 7 字符：{XX.XX}
                            if (close - open + 1 == 7) {
                                val valueStr = receiveBuffer.substring(open + 1, close) // 5 chars
                                try {
                                    val temp = valueStr.toFloat()
                                    if (temp in 32.0f..45.0f) {
                                        val now = System.currentTimeMillis()
                                        // emit 节流：500ms 内不重复发射
                                        if (now - lastEmitTime >= emitIntervalMs) {
                                            lastEmitTime = now
                                            emit(temp)
                                        }
                                        // 日志节流：5 秒内不重复打印
                                        if (now - lastLogTime >= logIntervalMs) {
                                            lastLogTime = now
                                            Log.i(TAG, "Temperature: $temp°C")
                                        }
                                    } else {
                                        Log.w(TAG, "Temperature out of range: $temp")
                                    }
                                } catch (e: NumberFormatException) {
                                    Log.w(TAG, "Invalid temperature format: '$valueStr'")
                                }
                            } else {
                                Log.w(TAG, "Unexpected token length: '${receiveBuffer.substring(open, close + 1)}'")
                            }
                            startIdx = close + 1
                        }

                        // 保留最后一个 '}' 之后的未处理尾部（处理拆包）
                        val lastClose = receiveBuffer.lastIndexOf('}')
                        if (lastClose >= 0) {
                            receiveBuffer.delete(0, lastClose + 1)
                        }
                        // 缓冲区过长说明数据异常，清空防止内存增长
                        if (receiveBuffer.length > 256) {
                            Log.w(TAG, "Receive buffer too large without valid data, clearing")
                            receiveBuffer.clear()
                        }
                    }
                } else {
                    kotlinx.coroutines.delay(50)
                }
            } catch (e: Exception) {
                Log.e(TAG, "!!! Error reading from serial port: ${e.message}")
                kotlinx.coroutines.delay(500)
            }
        }

        Log.d(TAG, "=== readTemperature ended ===")
    }.flowOn(Dispatchers.IO)

    fun beep(durationMs: Int = 200) {
        if (!isOpen || outputStream == null) {
            Timber.tag(TAG).w("Serial port not open, cannot beep")
            return
        }
        Timber.tag(TAG).d("Beep: ${durationMs}ms")
    }

    fun controlDoor(open: Boolean) {
        if (!isOpen || outputStream == null) {
            Timber.tag(TAG).w("Serial port not open, cannot control door")
            return
        }
        Timber.tag(TAG).d("Door: ${if (open) "OPEN" else "CLOSE"}")
    }

    fun isOpened(): Boolean = isOpen
}
