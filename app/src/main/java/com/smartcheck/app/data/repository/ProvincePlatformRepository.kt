package com.smartcheck.app.data.repository

import com.smartcheck.app.api.ProvincePlatformService
import com.smartcheck.app.api.model.ProvinceLoginData
import com.smartcheck.app.api.model.ProvinceLoginRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 省平台 Token 管理与登录封装
 *
 * 负责：
 * - 封装登录逻辑，自动缓存 token 和 orgId
 * - 判断省平台是否已配置
 * - 确保已登录（Token 有效）
 */
@Singleton
class ProvincePlatformRepository @Inject constructor(
    private val service: ProvincePlatformService,
    private val settingsRepository: SettingsRepository
) {

    /**
     * 判断省平台是否已配置（基础参数齐全）
     */
    val isConfigured: Boolean
        get() = settingsRepository.provincePlatformUrl.value.isNotBlank()
                && settingsRepository.provincePlatformUserId.value.isNotBlank()
                && settingsRepository.provincePlatformPassword.value.isNotBlank()
                && settingsRepository.provincePlatformSmKey.value.isNotBlank()
                && settingsRepository.provincePlatformSmIv.value.isNotBlank()
                && settingsRepository.provincePlatformSmKeysHeader.value.isNotBlank()

    /**
     * 判断当前是否已登录（Token 非空）
     */
    val isLoggedIn: Boolean
        get() = settingsRepository.provincePlatformToken.value.isNotBlank()

    /**
     * 执行登录，成功后缓存 token 和 orgId
     *
     * @return 登录成功数据（token + orgId）
     */
    suspend fun login(): Result<ProvinceLoginData> {
        if (!isConfigured) {
            return Result.failure(Exception("省平台未配置，请先完善配置信息"))
        }

        val request = ProvinceLoginRequest(
            userId = settingsRepository.provincePlatformUserId.value,
            password = settingsRepository.provincePlatformPassword.value,
            instrumentNumber = settingsRepository.provincePlatformInstrumentNumber.value
        )

        Timber.d("ProvincePlatform login, userId=${request.userId}, instrumentNumber=${request.instrumentNumber}")

        return try {
            val response = service.login(request)

            response.fold(
                onSuccess = { apiResponse ->
                    if (apiResponse.statuCode == 200 && apiResponse.data != null) {
                        // 缓存 Token 和 OrgId
                        settingsRepository.setProvincePlatformToken(apiResponse.data.token)
                        settingsRepository.setProvincePlatformOrgId(apiResponse.data.orgId)
                        Timber.i("ProvincePlatform login success, orgId=${apiResponse.data.orgId}")
                        Result.success(apiResponse.data)
                    } else {
                        val msg = apiResponse.info
                        Timber.e("ProvincePlatform login failed: $msg")
                        Result.failure(Exception(msg))
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "ProvincePlatform login request failed")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "ProvincePlatform login exception")
            Result.failure(e)
        }
    }

    /**
     * 确保已登录（Token 有效）
     *
     * 如果当前未登录，自动执行登录流程
     *
     * @return 登录状态
     */
    suspend fun ensureLogin(): Result<Unit> {
        return if (isLoggedIn) {
            Timber.d("ProvincePlatform already logged in")
            Result.success(Unit)
        } else {
            Timber.d("ProvincePlatform not logged in, attempting login...")
            login().map { }
        }
    }

    /**
     * 清除登录状态（Token 和 OrgId）
     */
    fun clearLoginState() {
        settingsRepository.setProvincePlatformToken("")
        settingsRepository.setProvincePlatformOrgId(0)
        Timber.d("ProvincePlatform login state cleared")
    }
}
