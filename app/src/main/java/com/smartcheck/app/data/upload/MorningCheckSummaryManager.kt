package com.smartcheck.app.data.upload

import android.content.Context
import com.smartcheck.app.api.model.MorningCheckSummaryUploadRequest
import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.utils.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningCheckSummaryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val recordDao: RecordDao,
    private val api: MorningCheckSummaryApi,
    private val store: MorningCheckSummaryStore,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope
) {
    private val isProcessing = AtomicBoolean(false)

    fun trigger() {
        appScope.launch(Dispatchers.IO) {
            process()
        }
    }

    suspend fun process(clock: Clock = Clock.system(MorningCheckSummaryCalculator.BEIJING_ZONE)) {
        if (!isProcessing.compareAndSet(false, true)) return
        try {
            val now = clock.millis()
            val platformUrl = settingsRepository.platformUrl.value.trimEnd('/')
            val apiKey = settingsRepository.apiKey.value
            if (platformUrl.isBlank() || apiKey.isBlank()) return
            val configSignature = "$platformUrl|${apiKey.hashCode()}"

            val pending = store.pendingReady(now)
            if (pending != null) {
                upload(pending, configSignature, now)
                return
            }

            val date = MorningCheckSummaryCalculator.currentDate(clock)
            val range = MorningCheckSummaryCalculator.dayRange(date)
            val counts = withContext(Dispatchers.IO) {
                MorningCheckSummaryCalculator.calculate(
                    activeUsers = userDao.getAllActiveUsersSync(),
                    records = recordDao.getRecordsByTimeRangeSync(range.first, range.last)
                )
            }
            if (counts.expectedCount == 0) {
                Timber.w("Morning check summary skipped: active employee list is empty")
                return
            }

            val alreadyUploaded = store.hasSuccessfulUpload(date.toString())
            val currentHour = ZonedDateTime.now(
                clock.withZone(MorningCheckSummaryCalculator.BEIJING_ZONE)
            ).hour
            if (!MorningCheckSummaryUploadPolicy.shouldUpload(
                    alreadyUploaded = alreadyUploaded,
                    counts = counts,
                    currentHour = currentHour,
                    dailyCutoffHour = DAILY_CUTOFF_HOUR
                )
            ) return

            val deviceId = settingsRepository.deviceId.value.takeIf { it.isNotBlank() }
                ?: DeviceAuth.getCurrentDeviceMac()
                ?: DeviceInfo.getDeviceId(context)
            val draft = MorningCheckSummaryUploadRequest(
                deviceId = deviceId,
                checkDate = date.toString(),
                version = 1,
                expectedCount = counts.expectedCount,
                checkedCount = counts.checkedCount,
                unqualifiedCount = counts.unqualifiedCount
            )
            val request = store.prepare(
                requestWithoutVersion = draft,
                configSignature = requestSignature(draft, configSignature),
                now = now
            ) ?: return
            upload(request, configSignature, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Morning check summary processing failed")
        } finally {
            isProcessing.set(false)
        }
    }

    private suspend fun upload(
        request: MorningCheckSummaryUploadRequest,
        configSignature: String,
        now: Long
    ) {
        Timber.i(
            "Uploading morning check summary: date=${request.checkDate}, version=${request.version}, " +
                "expected=${request.expectedCount}, checked=${request.checkedCount}, " +
                "unqualified=${request.unqualifiedCount}"
        )
        api.upload(request)
            .onSuccess { response ->
                store.markSuccess(request, response)
                Timber.i(
                    "Morning check summary uploaded: date=${request.checkDate}, " +
                        "version=${request.version}, result=${response.data?.result}"
                )
            }
            .onFailure { error ->
                when (error) {
                    is RetryableSummaryUploadException -> {
                        val retryIndex = store.retryCount().coerceAtMost(RETRY_DELAYS_SECONDS.lastIndex)
                        val retrySeconds = error.retryAfterSeconds
                            ?.coerceAtLeast(RETRY_DELAYS_SECONDS[retryIndex])
                            ?: RETRY_DELAYS_SECONDS[retryIndex]
                        store.markRetry(request, now + retrySeconds * 1_000)
                        appScope.launch {
                            delay(retrySeconds * 1_000)
                            process()
                        }
                        Timber.w(error, "Morning check summary retry scheduled in ${retrySeconds}s")
                    }
                    is PermanentSummaryUploadException -> {
                        if (error.httpStatus == 409 || error.code == 40900) {
                            store.markConflict(request)
                            Timber.w(error, "Morning check summary version conflict; next attempt will use a higher version")
                        } else {
                            store.markPermanentFailure(
                                request,
                                requestSignature(request, configSignature)
                            )
                            Timber.e(error, "Morning check summary rejected; waiting for data or configuration change")
                        }
                    }
                    else -> {
                        store.markPermanentFailure(
                            request,
                            requestSignature(request, configSignature)
                        )
                        Timber.e(error, "Morning check summary failed permanently")
                    }
                }
            }
    }

    private fun requestSignature(
        request: MorningCheckSummaryUploadRequest,
        configSignature: String
    ): String = listOf(
        configSignature,
        request.deviceId,
        request.checkDate,
        request.expectedCount,
        request.checkedCount,
        request.unqualifiedCount
    ).joinToString("|")

    companion object {
        const val DAILY_CUTOFF_HOUR = 18
        private val RETRY_DELAYS_SECONDS = longArrayOf(5, 15, 30, 60)
    }
}
