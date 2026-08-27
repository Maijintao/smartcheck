package com.smartcheck.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class User(
    val id: Long = 0,
    val name: String,
    val employeeId: String,
    val idCardNumber: String = "",
    val phone: String = "",
    val position: String = "",
    val department: String = "",
    val healthCertCode: String = "",
    val faceImagePath: String? = null,
    val faceEmbedding: ByteArray? = null,
    val healthCertImagePath: String = "",
    val healthCertStartDate: Long? = null,
    val healthCertEndDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),

    // === 平台同步字段 ===
    val platformVersion: Long = 0,
    val faceImageFileId: String? = null,
    val faceImageSha256: String? = null,
    val healthCertImageFileId: String? = null,
    val healthCertImageSha256: String? = null,
    val syncStatus: String = "SYNCED"
) {
    fun getHealthCertStatus(): HealthCertStatus {
        val daysRemaining = getHealthCertDaysRemaining() ?: return HealthCertStatus.NOT_PROVIDED

        return when {
            daysRemaining < 0 -> HealthCertStatus.EXPIRED
            daysRemaining <= 7 -> HealthCertStatus.EXPIRING_SOON
            else -> HealthCertStatus.VALID
        }
    }

    fun getHealthCertDaysRemaining(): Long? {
        val endDate = healthCertEndDate ?: return null
        val zoneId = ZoneId.systemDefault()
        val endDateLocal = Instant.ofEpochMilli(endDate).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(zoneId), endDateLocal)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (name != other.name) return false
        if (employeeId != other.employeeId) return false
        if (platformVersion != other.platformVersion) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + employeeId.hashCode()
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        return result
    }
}
