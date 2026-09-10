package com.smartcheck.app.data.upload

import android.content.Context
import com.smartcheck.app.api.model.MorningCheckSummaryUploadRequest
import com.smartcheck.app.api.model.MorningCheckSummaryUploadResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningCheckSummaryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun pendingReady(now: Long): MorningCheckSummaryUploadRequest? {
        if (prefs.getLong(KEY_PENDING_NEXT_ATTEMPT, 0L) > now) return null
        return prefs.getString(KEY_PENDING_REQUEST, null)?.let(::decodeRequest)
    }

    @Synchronized
    fun prepare(
        requestWithoutVersion: MorningCheckSummaryUploadRequest,
        configSignature: String,
        now: Long
    ): MorningCheckSummaryUploadRequest? {
        prefs.getString(KEY_PENDING_REQUEST, null)?.let(::decodeRequest)?.let { return null }

        val date = requestWithoutVersion.checkDate
        val successful = prefs.getString(successKey(date), null)?.let(::decodeRequest)
        if (successful?.hasSameContent(requestWithoutVersion) == true) return null
        if (prefs.getString(blockedKey(date), null) == configSignature) return null

        val nextVersion = prefs.getInt(versionKey(date), 0) + 1
        val request = requestWithoutVersion.copy(version = nextVersion)
        check(
            prefs.edit()
                .putInt(versionKey(date), nextVersion)
                .putString(KEY_PENDING_REQUEST, json.encodeToString(request))
                .putInt(KEY_PENDING_RETRY_COUNT, 0)
                .putLong(KEY_PENDING_NEXT_ATTEMPT, now)
                .commit()
        ) { "无法持久化晨检汇总待上传请求" }
        return request
    }

    @Synchronized
    fun markSuccess(
        request: MorningCheckSummaryUploadRequest,
        response: MorningCheckSummaryUploadResponse
    ) {
        val data = requireNotNull(response.data)
        val editor = prefs.edit()
            .putInt(versionKey(request.checkDate), maxOf(request.version, data.currentVersion))
            .putString(recordIdKey(request.checkDate), data.recordId)
            .remove(blockedKey(request.checkDate))
        if (data.result != "STALE") {
            editor.putString(successKey(request.checkDate), json.encodeToString(request))
        }
        clearPending(editor).commit()
    }

    @Synchronized
    fun markRetry(request: MorningCheckSummaryUploadRequest, nextAttemptAt: Long) {
        val pending = prefs.getString(KEY_PENDING_REQUEST, null)?.let(::decodeRequest)
        if (pending != request) return
        prefs.edit()
            .putInt(KEY_PENDING_RETRY_COUNT, prefs.getInt(KEY_PENDING_RETRY_COUNT, 0) + 1)
            .putLong(KEY_PENDING_NEXT_ATTEMPT, nextAttemptAt)
            .commit()
    }

    @Synchronized
    fun retryCount(): Int = prefs.getInt(KEY_PENDING_RETRY_COUNT, 0)

    @Synchronized
    fun markConflict(request: MorningCheckSummaryUploadRequest) {
        prefs.edit()
            .putInt(versionKey(request.checkDate), request.version)
            .let(::clearPending)
            .commit()
    }

    @Synchronized
    fun markPermanentFailure(
        request: MorningCheckSummaryUploadRequest,
        configSignature: String
    ) {
        prefs.edit()
            .putString(blockedKey(request.checkDate), configSignature)
            .let(::clearPending)
            .commit()
    }

    @Synchronized
    fun hasSuccessfulUpload(checkDate: String): Boolean =
        prefs.contains(successKey(checkDate))

    private fun decodeRequest(raw: String): MorningCheckSummaryUploadRequest? =
        runCatching { json.decodeFromString<MorningCheckSummaryUploadRequest>(raw) }.getOrNull()

    private fun clearPending(editor: android.content.SharedPreferences.Editor) = editor
        .remove(KEY_PENDING_REQUEST)
        .remove(KEY_PENDING_RETRY_COUNT)
        .remove(KEY_PENDING_NEXT_ATTEMPT)

    private fun versionKey(date: String) = "version_$date"
    private fun successKey(date: String) = "success_$date"
    private fun blockedKey(date: String) = "blocked_$date"
    private fun recordIdKey(date: String) = "record_id_$date"

    companion object {
        private const val PREF_NAME = "morning_check_summary_upload"
        private const val KEY_PENDING_REQUEST = "pending_request"
        private const val KEY_PENDING_RETRY_COUNT = "pending_retry_count"
        private const val KEY_PENDING_NEXT_ATTEMPT = "pending_next_attempt"
    }
}
