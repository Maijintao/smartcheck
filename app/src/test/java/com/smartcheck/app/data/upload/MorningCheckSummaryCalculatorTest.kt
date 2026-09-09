package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.db.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningCheckSummaryCalculatorTest {

    @Test
    fun `counts active employees once and uses their latest result`() {
        val users = listOf(
            user(id = 1, employeeId = "E001"),
            user(id = 2, employeeId = "E002"),
            user(id = 3, employeeId = "E003"),
            user(id = 4, employeeId = "E004", isActive = false)
        ).filter { it.isActive }
        val records = listOf(
            record(id = 1, userId = 1, employeeId = "E001", passed = false, checkTime = 100),
            record(id = 2, userId = 1, employeeId = " e001 ", passed = true, checkTime = 200),
            record(id = 3, userId = 2, employeeId = "E002", passed = false, checkTime = 150),
            record(id = 4, userId = 4, employeeId = "E004", passed = false, checkTime = 150),
            record(id = 5, userId = 99, employeeId = "UNKNOWN", passed = false, checkTime = 150)
        )

        val result = MorningCheckSummaryCalculator.calculate(users, records)

        assertEquals(3, result.expectedCount)
        assertEquals(2, result.checkedCount)
        assertEquals(1, result.unqualifiedCount)
    }

    @Test
    fun `document example produces expected 20 checked 18 and unqualified 1`() {
        val users = (1L..20L).map { user(id = it, employeeId = "E%03d".format(it)) }
        val records = (1L..18L).map {
            record(
                id = it,
                userId = it,
                employeeId = "E%03d".format(it),
                passed = it != 18L,
                checkTime = it * 100
            )
        }

        val result = MorningCheckSummaryCalculator.calculate(users, records)

        assertEquals(MorningCheckSummaryCounts(20, 18, 1), result)
    }

    @Test
    fun `falls back to local user id when employee id is blank`() {
        val result = MorningCheckSummaryCalculator.calculate(
            activeUsers = listOf(user(id = 7, employeeId = "")),
            records = listOf(record(id = 1, userId = 7, employeeId = "", passed = true))
        )

        assertEquals(MorningCheckSummaryCounts(1, 1, 0), result)
    }

    @Test
    fun `initial upload waits for completion or daily cutoff`() {
        val incomplete = MorningCheckSummaryCounts(
            expectedCount = 20,
            checkedCount = 18,
            unqualifiedCount = 1
        )
        val complete = MorningCheckSummaryCounts(
            expectedCount = 20,
            checkedCount = 20,
            unqualifiedCount = 1
        )

        assertEquals(
            false,
            MorningCheckSummaryUploadPolicy.shouldUpload(
                alreadyUploaded = false,
                counts = incomplete,
                currentHour = 17,
                dailyCutoffHour = 18
            )
        )
        assertEquals(
            true,
            MorningCheckSummaryUploadPolicy.shouldUpload(
                alreadyUploaded = false,
                counts = complete,
                currentHour = 9,
                dailyCutoffHour = 18
            )
        )
        assertEquals(
            true,
            MorningCheckSummaryUploadPolicy.shouldUpload(
                alreadyUploaded = false,
                counts = incomplete,
                currentHour = 18,
                dailyCutoffHour = 18
            )
        )
    }

    @Test
    fun `changed content can update after first successful upload`() {
        assertEquals(
            true,
            MorningCheckSummaryUploadPolicy.shouldUpload(
                alreadyUploaded = true,
                counts = MorningCheckSummaryCounts(20, 18, 1),
                currentHour = 10,
                dailyCutoffHour = 18
            )
        )
    }

    private fun user(
        id: Long,
        employeeId: String,
        isActive: Boolean = true
    ) = UserEntity(
        id = id,
        name = "User $id",
        employeeId = employeeId,
        isActive = isActive
    )

    private fun record(
        id: Long,
        userId: Long,
        employeeId: String,
        passed: Boolean,
        checkTime: Long = 100
    ) = RecordEntity(
        id = id,
        userId = userId,
        userName = "User $userId",
        employeeId = employeeId,
        temperature = 36.5f,
        isTempNormal = passed,
        isHandNormal = passed,
        isPassed = passed,
        checkTime = checkTime
    )
}
