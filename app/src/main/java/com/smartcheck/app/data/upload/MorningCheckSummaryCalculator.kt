package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.db.UserEntity
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class MorningCheckSummaryCounts(
    val expectedCount: Int,
    val checkedCount: Int,
    val unqualifiedCount: Int
) {
    init {
        require(expectedCount >= 0)
        require(checkedCount in 0..expectedCount)
        require(unqualifiedCount in 0..checkedCount)
    }
}

object MorningCheckSummaryCalculator {
    val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun currentDate(clock: Clock = Clock.system(BEIJING_ZONE)): LocalDate =
        LocalDate.now(clock.withZone(BEIJING_ZONE))

    fun dayRange(date: LocalDate): LongRange {
        val start = date.atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
        val endExclusive = date.plusDays(1).atStartOfDay(BEIJING_ZONE).toInstant().toEpochMilli()
        return start until endExclusive
    }

    fun calculate(
        activeUsers: List<UserEntity>,
        records: List<RecordEntity>
    ): MorningCheckSummaryCounts {
        val expectedKeys = activeUsers.map(::userKey).toSet()
        val latestRecords = records
            .mapNotNull { record ->
                val key = recordKey(record)
                if (key in expectedKeys) key to record else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, employeeRecords) -> employeeRecords.maxBy { it.checkTime } }

        return MorningCheckSummaryCounts(
            expectedCount = expectedKeys.size,
            checkedCount = latestRecords.size,
            unqualifiedCount = latestRecords.values.count { !it.isPassed }
        )
    }

    private fun userKey(user: UserEntity): String =
        user.employeeId.trim().takeIf { it.isNotBlank() }
            ?.let { "employee:${it.lowercase()}" }
            ?: "user:${user.id}"

    private fun recordKey(record: RecordEntity): String =
        record.employeeId.trim().takeIf { it.isNotBlank() }
            ?.let { "employee:${it.lowercase()}" }
            ?: "user:${record.userId}"
}

object MorningCheckSummaryUploadPolicy {
    fun shouldUpload(
        alreadyUploaded: Boolean,
        counts: MorningCheckSummaryCounts,
        currentHour: Int,
        dailyCutoffHour: Int
    ): Boolean =
        alreadyUploaded ||
            counts.checkedCount == counts.expectedCount ||
            currentHour >= dailyCutoffHour
}
