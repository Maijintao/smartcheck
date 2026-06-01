package com.smartcheck.app.domain.usecase

import com.smartcheck.app.api.model.ProvinceInspectionItem
import com.smartcheck.app.api.model.ProvincePersonSchedule
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.ProvincePlatformRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.api.ProvincePlatformService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 省平台人员同步 UseCase
 *
 * 负责：
 * - 同步当天排班人员
 * - 同步晨检管理项
 */
@Singleton
class ProvincePlatformSyncUseCase @Inject constructor(
    private val service: ProvincePlatformService,
    private val repository: ProvincePlatformRepository,
    private val settingsRepository: SettingsRepository,
    private val userDao: UserDao
) {

    /**
     * 同步当天排班人员
     *
     * 从省平台获取排班人员列表，与本地人员关联：
     * - 通过身份证号（idCard）匹配本地已有人员
     * - 匹配成功：更新本地人员信息（照片 URL、健康证等）
     * - 匹配失败：不入库（晨检时通过 flowId/idCard 匹配）
     *
     * @return 同步结果，包含更新人数和新增人数
     */
    suspend fun syncTodayPersonnel(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            // 1. 确保已登录
            val loginResult = repository.ensureLogin()
            if (loginResult.isFailure) {
                val error = loginResult.exceptionOrNull()
                Timber.e("Province platform sync personnel failed: not logged in: ${error?.message}")
                return@withContext Result.failure(
                    error ?: Exception("省平台未登录")
                )
            }

            val orgId = settingsRepository.provincePlatformOrgId.value
            if (orgId == 0) {
                return@withContext Result.failure(Exception("组织ID未获取"))
            }

            // 2. 获取排班人员
            val responseResult = service.getTodayPersonSchedule(orgId)
            if (responseResult.isFailure) {
                val e = responseResult.exceptionOrNull()
                Timber.e("Failed to get today personnel schedule: ${e?.message}")
                return@withContext Result.failure(e ?: Exception("获取排班人员失败"))
            }

            val response = responseResult.getOrNull()!!
            if (response.statuCode != 200) {
                val msg = response.info
                Timber.e("Get today personnel schedule failed: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val personList = response.data ?: emptyList()
            Timber.d("Got ${personList.size} personnel from province platform")

            // 3. 与本地人员关联/更新
            var updatedCount = 0
            var newCount = 0

            for (person in personList) {
                val existingUser = userDao.getUserByIdCardNumber(person.idCard)
                if (existingUser != null) {
                    // 更新已有人员信息
                    val updatedUser = updateUserFromProvincePlatform(existingUser, person)
                    userDao.updateUser(updatedUser)
                    updatedCount++
                    Timber.d("Updated user ${person.name} (${person.idCard})")
                } else {
                    // 新人员：不入库（晨检时通过 idCard 匹配）
                    newCount++
                    Timber.d("New user ${person.name} (${person.idCard}) - not persisted")
                }
            }

            Timber.i("Province platform sync completed: $updatedCount updated, $newCount new")
            Result.success(SyncResult(updated = updatedCount, new = newCount))
        } catch (e: Exception) {
            Timber.e(e, "Province platform sync personnel exception")
            Result.failure(e)
        }
    }

    /**
     * 同步晨检管理项
     *
     * @return 晨检管理项列表
     */
    suspend fun syncInspectionItems(): Result<List<ProvinceInspectionItem>> = withContext(Dispatchers.IO) {
        try {
            // 1. 确保已登录
            val loginResult = repository.ensureLogin()
            if (loginResult.isFailure) {
                val error = loginResult.exceptionOrNull()
                Timber.e("Province platform sync inspection items failed: not logged in: ${error?.message}")
                return@withContext Result.failure(
                    error ?: Exception("省平台未登录")
                )
            }

            val orgId = settingsRepository.provincePlatformOrgId.value

            // 2. 获取晨检管理项
            val responseResult = service.getInspectionContent(orgId)
            if (responseResult.isFailure) {
                val e = responseResult.exceptionOrNull()
                Timber.e("Failed to get inspection content: ${e?.message}")
                return@withContext Result.failure(e ?: Exception("获取晨检管理项失败"))
            }

            val response = responseResult.getOrNull()!!
            if (response.statuCode != 200) {
                val msg = response.info
                Timber.e("Get inspection content failed: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val items = response.data ?: emptyList()
            Timber.i("Got ${items.size} inspection items from province platform")
            Result.success(items)
        } catch (e: Exception) {
            Timber.e(e, "Province platform sync inspection items exception")
            Result.failure(e)
        }
    }

    /**
     * 使用省平台数据更新本地用户实体
     */
    private fun updateUserFromProvincePlatform(
        existing: UserEntity,
        person: ProvincePersonSchedule
    ): UserEntity {
        return existing.copy(
            name = person.name.takeIf { it.isNotBlank() } ?: existing.name,
            phone = person.tel?.takeIf { it.isNotBlank() } ?: existing.phone,
            position = person.position?.takeIf { it.isNotBlank() } ?: existing.position,
            // 注意：portraitPhoto 和 healthUrl 是 URL，本地存储的是文件路径
            // 如果需要下载图片到本地，需要额外的下载逻辑
            // 这里仅更新文本字段
        )
    }

    /**
     * 同步结果
     */
    data class SyncResult(
        val updated: Int,
        val new: Int
    )
}
