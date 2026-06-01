package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.ProvincePlatformRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.domain.usecase.ProvincePlatformSyncUseCase
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
import timber.log.Timber

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val adminAuthRepository: AdminAuthRepository,
    private val recordRepository: RecordRepository,
    private val provincePlatformRepository: ProvincePlatformRepository,
    private val provincePlatformSyncUseCase: ProvincePlatformSyncUseCase,
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
    val loginBackground: StateFlow<String> = settingsRepository.loginBackground
    val adminAvatar: StateFlow<String> = settingsRepository.adminAvatar
    val deviceSn: StateFlow<String> = settingsRepository.deviceSn
    val deviceId: StateFlow<String> = settingsRepository.deviceId
    val platformUrl: StateFlow<String> = settingsRepository.platformUrl
    val apiKey: StateFlow<String> = settingsRepository.apiKey

    // ========== 省平台配置 ==========
    val provincePlatformUrl: StateFlow<String> = settingsRepository.provincePlatformUrl
    val provincePlatformUserId: StateFlow<String> = settingsRepository.provincePlatformUserId
    val provincePlatformPassword: StateFlow<String> = settingsRepository.provincePlatformPassword
    val provincePlatformInstrumentNumber: StateFlow<String> = settingsRepository.provincePlatformInstrumentNumber
    val provincePlatformSmKey: StateFlow<String> = settingsRepository.provincePlatformSmKey
    val provincePlatformSmIv: StateFlow<String> = settingsRepository.provincePlatformSmIv
    val provincePlatformSmKeysHeader: StateFlow<String> = settingsRepository.provincePlatformSmKeysHeader
    val provincePlatformToken: StateFlow<String> = settingsRepository.provincePlatformToken
    val provincePlatformOrgId: StateFlow<Int> = settingsRepository.provincePlatformOrgId
    val provincePlatformTestApisix: StateFlow<String> = settingsRepository.provincePlatformTestApisix

    private val _provincePlatformLoginStatus = MutableStateFlow("")
    val provincePlatformLoginStatus: StateFlow<String> = _provincePlatformLoginStatus.asStateFlow()

    private val _provincePlatformSyncStatus = MutableStateFlow("")
    val provincePlatformSyncStatus: StateFlow<String> = _provincePlatformSyncStatus.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) {
        settingsRepository.setVoiceEnabled(enabled)
    }

    fun setAdminName(value: String) = settingsRepository.setAdminName(value)

    fun setCanteenName(value: String) = settingsRepository.setCanteenName(value)

    fun setLoginTitle(value: String) = settingsRepository.setLoginTitle(value)

    fun setLoginBackground(value: String) = settingsRepository.setLoginBackground(value)

    fun setAdminAvatar(value: String) = settingsRepository.setAdminAvatar(value)

    fun setDeviceSn(value: String) = settingsRepository.setDeviceSn(value)
    fun setDeviceId(value: String) = settingsRepository.setDeviceId(value)
    fun setPlatformUrl(value: String) = settingsRepository.setPlatformUrl(value)
    fun setApiKey(value: String) = settingsRepository.setApiKey(value)

    fun setAccount(value: String) {
        settingsRepository.setAccount(value)
        adminAuthRepository.setAccount(value)
    }

    fun setPassword(value: String) = adminAuthRepository.setPassword(value)

    // ========== 省平台配置 Setter ==========
    fun setProvincePlatformUrl(value: String) = settingsRepository.setProvincePlatformUrl(value)
    fun setProvincePlatformUserId(value: String) = settingsRepository.setProvincePlatformUserId(value)
    fun setProvincePlatformPassword(value: String) = settingsRepository.setProvincePlatformPassword(value)
    fun setProvincePlatformInstrumentNumber(value: String) = settingsRepository.setProvincePlatformInstrumentNumber(value)
    fun setProvincePlatformSmKey(value: String) = settingsRepository.setProvincePlatformSmKey(value)
    fun setProvincePlatformSmIv(value: String) = settingsRepository.setProvincePlatformSmIv(value)
    fun setProvincePlatformSmKeysHeader(value: String) = settingsRepository.setProvincePlatformSmKeysHeader(value)
    fun setProvincePlatformTestApisix(value: String) = settingsRepository.setProvincePlatformTestApisix(value)

    /**
     * 省平台登录测试
     */
    fun loginTest() {
        viewModelScope.launch(Dispatchers.IO) {
            _provincePlatformLoginStatus.value = "登录中..."
            val result = provincePlatformRepository.login()
            _provincePlatformLoginStatus.value = if (result.isSuccess) {
                "登录成功 (orgId=${result.getOrNull()?.orgId})"
            } else {
                "登录失败: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    /**
     * 同步省平台排班人员
     */
    fun syncPersonnel() {
        viewModelScope.launch(Dispatchers.IO) {
            _provincePlatformSyncStatus.value = "同步中..."
            val result = provincePlatformSyncUseCase.syncTodayPersonnel()
            _provincePlatformSyncStatus.value = if (result.isSuccess) {
                val syncResult = result.getOrNull()!!
                "同步完成: ${syncResult.updated}人更新, ${syncResult.new}人新增"
            } else {
                "同步失败: ${result.exceptionOrNull()?.message}"
            }
        }
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
}
