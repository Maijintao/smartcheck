package com.smartcheck.app.data.repository

import com.smartcheck.app.data.serial.SerialPortManager
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.repository.ITemperatureService
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemperatureServiceImpl @Inject constructor(
    private val serialPortManager: SerialPortManager
) : ITemperatureService {

    @Volatile
    private var initialized = false

    companion object {
        // 测温模块配置
        const val TEMP_DEVICE_PATH = "/dev/ttyS7"
        const val TEMP_BAUD_RATE = 115200
    }

    override suspend fun initialize(): Result<Unit> {
        Timber.d("=== TemperatureServiceImpl.initialize() ===")
        Timber.d("Current initialized: $initialized")

        return try {
            Timber.d("Configuring serial port: $TEMP_DEVICE_PATH, $TEMP_BAUD_RATE")
            serialPortManager.configure(TEMP_DEVICE_PATH, TEMP_BAUD_RATE)

            Timber.d("Opening serial port...")
            val opened = serialPortManager.open()

            if (opened) {
                initialized = true
                Timber.i("=== TemperatureService initialized successfully ===")
                Result.success(Unit)
            } else {
                Timber.e("!!! Failed to open serial port")
                initialized = false
                Result.failure(AppError.HardwareError("serial", "Failed to open serial port"))
            }
        } catch (e: Exception) {
            Timber.e("!!! TemperatureService initialize exception: ${e.message}")
            initialized = false
            Result.failure(AppError.HardwareError("serial", e.message ?: "unknown"))
        }
    }

    override suspend fun release(): Result<Unit> {
        Timber.d("=== TemperatureServiceImpl.release() ===")
        return try {
            serialPortManager.close()
            initialized = false
            Timber.i("TemperatureService released")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e("!!! TemperatureService release exception: ${e.message}")
            Result.failure(AppError.HardwareError("serial", e.message ?: "unknown"))
        }
    }

    override fun isInitialized(): Boolean = initialized

    override fun observeTemperature(): Flow<Float> {
        Timber.d("observeTemperature called, initialized: $initialized")
        return serialPortManager.readTemperature()
    }
}
