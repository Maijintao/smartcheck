package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.smartcheck.app.data.repository.AdminAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.data.repository.RecordRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.smartcheck.app.data.upload.ConnectionTestResult
import com.smartcheck.app.data.upload.DeviceHeartbeatManager
import java.util.concurrent.TimeUnit

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val adminAuthRepository: AdminAuthRepository,
    private val recordRepository: RecordRepository,
    private val deviceHeartbeatManager: DeviceHeartbeatManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _account = MutableStateFlow("admin")
    val account: StateFlow<String> = _account.asStateFlow()

    init {
        viewModelScope.launch {
            adminAuthRepository.observeCurrentUsername().collect { value ->
                if (value != null) {
                    _account.value = value
                }
            }
        }
    }

    val voiceEnabled: StateFlow<Boolean> = settingsRepository.voiceEnabled
    val adminName: StateFlow<String> = settingsRepository.adminName
    
    val canteenName: StateFlow<String> = settingsRepository.canteenName
    val loginTitle: StateFlow<String> = settingsRepository.loginTitle
    val homeTitle: StateFlow<String> = settingsRepository.homeTitle
    val loginBackground: StateFlow<String> = settingsRepository.loginBackground
    val adminAvatar: StateFlow<String> = settingsRepository.adminAvatar
    val deviceSn: StateFlow<String> = settingsRepository.deviceSn
    val deviceId: StateFlow<String> = settingsRepository.deviceId
    val platformUrl: StateFlow<String> = settingsRepository.platformUrl
    val apiKey: StateFlow<String> = settingsRepository.apiKey
    val heartbeatInterval: StateFlow<Int> = settingsRepository.heartbeatInterval

    fun setVoiceEnabled(enabled: Boolean) {
        settingsRepository.setVoiceEnabled(enabled)
    }

    fun setAdminName(value: String) = settingsRepository.setAdminName(value)

    fun setCanteenName(value: String) = settingsRepository.setCanteenName(value)

    fun setLoginTitle(value: String) = settingsRepository.setLoginTitle(value)

    fun setHomeTitle(value: String) = settingsRepository.setHomeTitle(value)

    fun setLoginBackground(value: String) = settingsRepository.setLoginBackground(value)

    fun setAdminAvatar(value: String) = settingsRepository.setAdminAvatar(value)

    fun setDeviceSn(value: String) = settingsRepository.setDeviceSn(value)
    fun setDeviceId(value: String) = settingsRepository.setDeviceId(value)
    fun setPlatformUrl(value: String) = settingsRepository.setPlatformUrl(value)
    fun setApiKey(value: String) = settingsRepository.setApiKey(value)
    fun setHeartbeatInterval(value: Int) = settingsRepository.setHeartbeatInterval(value)

    fun setAccount(value: String) {
        settingsRepository.setAccount(value)
        adminAuthRepository.setAccount(value)
    }

    fun setPassword(value: String) = adminAuthRepository.setPassword(value)

    /**
     * 测试平台连接
     */
    suspend fun testPlatformConnection(): ConnectionTestResult {
        return deviceHeartbeatManager.testConnection()
    }

    fun clearRecordImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = FileUtil.getRecordsDir(appContext)
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            recordRepository.deleteAllRecords()
        }
    }

    /**
     * 低存储空间时清理旧记录（图片 + 数据库）
     * 删除指定天数前的所有记录，用于磁盘空间不足的紧急场景
     */
    fun clearOldRecordsLowSpace(days: Int = 30) {
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            FileUtil.clearOldRecords(appContext, days)
            recordRepository.deleteOldRecords(cutoff)
        }
    }
}
