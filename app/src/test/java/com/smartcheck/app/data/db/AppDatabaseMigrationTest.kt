package com.smartcheck.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    @Test
    fun `migration 10 to 11 preserves existing tables and backfills upload fields`() {
        val database = mockk<SupportSQLiteDatabase>()
        val statements = mutableListOf<String>()
        every { database.execSQL(capture(statements)) } returns Unit

        AppDatabase.MIGRATION_10_11.migrate(database)

        assertTrue(statements.any { it.contains("ADD COLUMN recordUuid") })
        assertTrue(statements.any { it.contains("ADD COLUMN uploadStatus") })
        assertTrue(statements.any { it.contains("WHERE recordUuid = ''") })
        assertTrue(statements.any { it.contains("CASE WHEN isUploaded = 1 THEN 'UPLOADED' ELSE 'PENDING' END") })
        assertFalse(statements.any { it.contains("DROP TABLE", ignoreCase = true) })
        assertFalse(statements.any { it.contains("DELETE FROM", ignoreCase = true) })
    }
}
