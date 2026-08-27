package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.remote.CloudApiUrl
import com.smartcheck.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val adminAuthRepository: AdminAuthRepository,
    private val recordRepository: RecordRepository,
    private val httpClient: HttpClient,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    data class CloudConnectionTestState(
        val isTesting: Boolean = false,
        val message: String? = null,
        val isSuccess: Boolean = false
    )

    private val _account = MutableStateFlow("admin")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _cloudConnectionTestState = MutableStateFlow(CloudConnectionTestState())
    val cloudConnectionTestState: StateFlow<CloudConnectionTestState> =
        _cloudConnectionTestState.asStateFlow()

    val defaultCloudBaseUrl: String = CloudApiUrl.DEFAULT_BASE_URL

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
    val cloudBaseUrl: StateFlow<String> = settingsRepository.cloudBaseUrl

    fun setVoiceEnabled(enabled: Boolean) {
        settingsRepository.setVoiceEnabled(enabled)
    }

    fun setAdminName(value: String) = settingsRepository.setAdminName(value)

    fun setCanteenName(value: String) = settingsRepository.setCanteenName(value)

    fun setLoginTitle(value: String) = settingsRepository.setLoginTitle(value)

    fun setLoginBackground(value: String) = settingsRepository.setLoginBackground(value)

    fun setAdminAvatar(value: String) = settingsRepository.setAdminAvatar(value)

    fun setDeviceSn(value: String) = settingsRepository.setDeviceSn(value)

    fun setCloudBaseUrl(value: String): Result<String> {
        clearCloudConnectionTestResult()
        return settingsRepository.setCloudBaseUrl(value)
    }

    fun resetCloudBaseUrl() {
        settingsRepository.resetCloudBaseUrl()
        clearCloudConnectionTestResult()
    }

    fun testCloudConnection(value: String) {
        val normalizedUrl = CloudApiUrl.normalizeBaseUrl(value).getOrElse { error ->
            _cloudConnectionTestState.value = CloudConnectionTestState(
                message = error.message ?: "服务器地址格式错误"
            )
            return
        }

        _cloudConnectionTestState.value = CloudConnectionTestState(isTesting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val testUrl = CloudApiUrl.buildUrl(normalizedUrl, CloudApiUrl.EMPLOYEE_ENDPOINT)
                val response = httpClient.post(testUrl) {
                    header("yg_sn", settingsRepository.getDeviceSn())
                    contentType(ContentType.Application.Json)
                    setBody(TextContent("""{"pageIndex":0,"pageSize":1}""", ContentType.Application.Json))
                }
                val body = response.bodyAsText()
                val message = when {
                    body.contains("设备未绑定") -> "服务器可达，当前设备未绑定"
                    response.status.value in 200..299 -> "连接成功（HTTP ${response.status.value}）"
                    else -> "服务器可达，接口返回 HTTP ${response.status.value}"
                }
                _cloudConnectionTestState.value = CloudConnectionTestState(
                    message = message,
                    isSuccess = true
                )
            } catch (e: Exception) {
                _cloudConnectionTestState.value = CloudConnectionTestState(
                    message = describeConnectionError(e)
                )
            }
        }
    }

    fun clearCloudConnectionTestResult() {
        _cloudConnectionTestState.value = CloudConnectionTestState()
    }

    fun setAccount(value: String) {
        settingsRepository.setAccount(value)
        adminAuthRepository.setAccount(value)
    }

    fun setPassword(value: String) = adminAuthRepository.setPassword(value)

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

    private fun describeConnectionError(error: Throwable): String {
        val details = buildString {
            var current: Throwable? = error
            while (current != null) {
                append(current::class.java.simpleName)
                append(':')
                append(current.message.orEmpty())
                append(' ')
                current = current.cause
            }
        }.lowercase()

        return when {
            "unknownhost" in details || "unresolved" in details ->
                "DNS 解析失败，请检查域名"
            "timeout" in details ->
                "连接超时，请检查网络或服务器"
            "ssl" in details || "certificate" in details || "certpath" in details ->
                "TLS 证书验证失败"
            else ->
                "连接失败：${error.message ?: "未知错误"}"
        }
    }
}
