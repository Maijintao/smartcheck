package com.smartcheck.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _adminName = MutableStateFlow(prefs.getString(KEY_ADMIN_NAME, "") ?: "")
    val adminName: StateFlow<String> = _adminName.asStateFlow()

    private val _account = MutableStateFlow(prefs.getString(KEY_ACCOUNT, "") ?: "")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _canteenName = MutableStateFlow(prefs.getString(KEY_CANTEEN_NAME, "") ?: "")
    val canteenName: StateFlow<String> = _canteenName.asStateFlow()

    private val _loginTitle = MutableStateFlow(prefs.getString(KEY_LOGIN_TITLE, "") ?: "")
    val loginTitle: StateFlow<String> = _loginTitle.asStateFlow()

    private val _loginBackground = MutableStateFlow(prefs.getString(KEY_LOGIN_BG, "") ?: "")
    val loginBackground: StateFlow<String> = _loginBackground.asStateFlow()

    private val _adminAvatar = MutableStateFlow(prefs.getString(KEY_ADMIN_AVATAR, "") ?: "")
    val adminAvatar: StateFlow<String> = _adminAvatar.asStateFlow()

    private val _deviceSn = MutableStateFlow(prefs.getString(KEY_DEVICE_SN, "") ?: "")
    val deviceSn: StateFlow<String> = _deviceSn.asStateFlow()

    private val _platformUrl = MutableStateFlow(prefs.getString(KEY_PLATFORM_URL, "") ?: "")
    val platformUrl: StateFlow<String> = _platformUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _deviceId = MutableStateFlow(prefs.getString(KEY_DEVICE_ID, "") ?: "")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // ========== 省平台配置 ==========

    private val _provincePlatformUrl = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_URL, "") ?: "")
    val provincePlatformUrl: StateFlow<String> = _provincePlatformUrl.asStateFlow()

    private val _provincePlatformUserId = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_USER_ID, "") ?: "")
    val provincePlatformUserId: StateFlow<String> = _provincePlatformUserId.asStateFlow()

    private val _provincePlatformPassword = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_PASSWORD, "") ?: "")
    val provincePlatformPassword: StateFlow<String> = _provincePlatformPassword.asStateFlow()

    private val _provincePlatformInstrumentNumber = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_INSTRUMENT_NUMBER, "") ?: "")
    val provincePlatformInstrumentNumber: StateFlow<String> = _provincePlatformInstrumentNumber.asStateFlow()

    private val _provincePlatformSmKey = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_SM_KEY, "") ?: "")
    val provincePlatformSmKey: StateFlow<String> = _provincePlatformSmKey.asStateFlow()

    private val _provincePlatformSmIv = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_SM_IV, "") ?: "")
    val provincePlatformSmIv: StateFlow<String> = _provincePlatformSmIv.asStateFlow()

    private val _provincePlatformSmKeysHeader = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_SM_KEYS_HEADER, "") ?: "")
    val provincePlatformSmKeysHeader: StateFlow<String> = _provincePlatformSmKeysHeader.asStateFlow()

    private val _provincePlatformToken = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_TOKEN, "") ?: "")
    val provincePlatformToken: StateFlow<String> = _provincePlatformToken.asStateFlow()

    private val _provincePlatformOrgId = MutableStateFlow(prefs.getInt(KEY_PROVINCE_PLATFORM_ORG_ID, 0))
    val provincePlatformOrgId: StateFlow<Int> = _provincePlatformOrgId.asStateFlow()

    private val _provincePlatformTestApisix = MutableStateFlow(prefs.getString(KEY_PROVINCE_PLATFORM_TEST_APISIX, "") ?: "")
    val provincePlatformTestApisix: StateFlow<String> = _provincePlatformTestApisix.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
        _voiceEnabled.value = enabled
    }

    fun setAdminName(value: String) {
        prefs.edit().putString(KEY_ADMIN_NAME, value).apply()
        _adminName.value = value
    }

    fun setAccount(value: String) {
        prefs.edit().putString(KEY_ACCOUNT, value).apply()
        _account.value = value
    }

    fun setCanteenName(value: String) {
        prefs.edit().putString(KEY_CANTEEN_NAME, value).apply()
        _canteenName.value = value
    }

    fun setLoginTitle(value: String) {
        prefs.edit().putString(KEY_LOGIN_TITLE, value).apply()
        _loginTitle.value = value
    }

    fun setLoginBackground(value: String) {
        prefs.edit().putString(KEY_LOGIN_BG, value).apply()
        _loginBackground.value = value
    }

    fun setAdminAvatar(value: String) {
        prefs.edit().putString(KEY_ADMIN_AVATAR, value).apply()
        _adminAvatar.value = value
    }

    fun setDeviceSn(value: String) {
        prefs.edit().putString(KEY_DEVICE_SN, value).apply()
        _deviceSn.value = value
    }

    fun setPlatformUrl(value: String) {
        prefs.edit().putString(KEY_PLATFORM_URL, value).apply()
        _platformUrl.value = value
    }

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
        _apiKey.value = value
    }

    fun setDeviceId(value: String) {
        prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        _deviceId.value = value
    }

    // ========== 省平台配置 Setter ==========

    fun setProvincePlatformUrl(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_URL, value).apply()
        _provincePlatformUrl.value = value
    }

    fun setProvincePlatformUserId(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_USER_ID, value).apply()
        _provincePlatformUserId.value = value
    }

    fun setProvincePlatformPassword(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_PASSWORD, value).apply()
        _provincePlatformPassword.value = value
    }

    fun setProvincePlatformInstrumentNumber(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_INSTRUMENT_NUMBER, value).apply()
        _provincePlatformInstrumentNumber.value = value
    }

    fun setProvincePlatformSmKey(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_SM_KEY, value).apply()
        _provincePlatformSmKey.value = value
    }

    fun setProvincePlatformSmIv(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_SM_IV, value).apply()
        _provincePlatformSmIv.value = value
    }

    fun setProvincePlatformSmKeysHeader(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_SM_KEYS_HEADER, value).apply()
        _provincePlatformSmKeysHeader.value = value
    }

    fun setProvincePlatformToken(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_TOKEN, value).apply()
        _provincePlatformToken.value = value
    }

    fun setProvincePlatformOrgId(value: Int) {
        prefs.edit().putInt(KEY_PROVINCE_PLATFORM_ORG_ID, value).apply()
        _provincePlatformOrgId.value = value
    }

    fun setProvincePlatformTestApisix(value: String) {
        prefs.edit().putString(KEY_PROVINCE_PLATFORM_TEST_APISIX, value).apply()
        _provincePlatformTestApisix.value = value
    }

    fun addDeviceSnHistory(sn: String) {
        if (sn.isBlank()) return
        val current = getDeviceSnHistory().toMutableSet()
        current.add(sn)
        prefs.edit().putString(KEY_DEVICE_SN_HISTORY, current.joinToString(",")).apply()
    }

    fun getDeviceSnHistory(): List<String> {
        val raw = prefs.getString(KEY_DEVICE_SN_HISTORY, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun removeDeviceSnHistory(sn: String) {
        val current = getDeviceSnHistory().toMutableSet()
        current.remove(sn)
        prefs.edit().putString(KEY_DEVICE_SN_HISTORY, current.joinToString(",")).apply()
    }

    fun isVoiceEnabled(): Boolean = _voiceEnabled.value

    fun getDeviceSn(): String = _deviceSn.value

    companion object {
        private const val PREF_NAME = "smartcheck_settings"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_ADMIN_NAME = "admin_name"
        private const val KEY_ACCOUNT = "admin_account"
        private const val KEY_CANTEEN_NAME = "canteen_name"
        private const val KEY_LOGIN_TITLE = "login_title"
        private const val KEY_LOGIN_BG = "login_background"
        private const val KEY_ADMIN_AVATAR = "admin_avatar"
        private const val KEY_DEVICE_SN = "device_sn"
        private const val KEY_DEVICE_SN_HISTORY = "device_sn_history"
        private const val KEY_PLATFORM_URL = "platform_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_ID = "device_id"

        // 省平台配置 Key
        private const val KEY_PROVINCE_PLATFORM_URL = "province_platform_url"
        private const val KEY_PROVINCE_PLATFORM_USER_ID = "province_platform_user_id"
        private const val KEY_PROVINCE_PLATFORM_PASSWORD = "province_platform_password"
        private const val KEY_PROVINCE_PLATFORM_INSTRUMENT_NUMBER = "province_platform_instrument_number"
        private const val KEY_PROVINCE_PLATFORM_SM_KEY = "province_platform_sm_key"
        private const val KEY_PROVINCE_PLATFORM_SM_IV = "province_platform_sm_iv"
        private const val KEY_PROVINCE_PLATFORM_SM_KEYS_HEADER = "province_platform_sm_keys_header"
        private const val KEY_PROVINCE_PLATFORM_TOKEN = "province_platform_token"
        private const val KEY_PROVINCE_PLATFORM_ORG_ID = "province_platform_org_id"
        private const val KEY_PROVINCE_PLATFORM_TEST_APISIX = "province_platform_test_apisix"
    }
}
