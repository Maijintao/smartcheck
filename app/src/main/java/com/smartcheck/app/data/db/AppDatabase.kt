package com.smartcheck.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

    @Database(
        entities = [
            UserEntity::class,
            RecordEntity::class,
            ApiTokenEntity::class,
            ApiAccessLogEntity::class,
            SystemUserEntity::class,
            SyncOutboxEntity::class,
            SyncStateEntity::class,
            DeletedEmployeeVersionEntity::class
        ],
        version = 10,
        exportSchema = false
    )
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun recordDao(): RecordDao
    abstract fun apiTokenDao(): ApiTokenDao
    abstract fun apiAccessLogDao(): ApiAccessLogDao
    abstract fun systemUserDao(): SystemUserDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun deletedEmployeeVersionDao(): DeletedEmployeeVersionDao

    companion object {
        val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE users ADD COLUMN idCardNumber TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertImagePath TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertStartDate INTEGER")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertEndDate INTEGER")
        }

        val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) { database ->
            database.execSQL("ALTER TABLE check_records ADD COLUMN handStatus TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE check_records ADD COLUMN healthCertStatus TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE check_records ADD COLUMN symptomFlags TEXT NOT NULL DEFAULT ''")
        }

        val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) { database ->
            database.execSQL("ALTER TABLE check_records ADD COLUMN faceImagePath TEXT")
            database.execSQL("ALTER TABLE check_records ADD COLUMN handPalmPath TEXT")
            database.execSQL("ALTER TABLE check_records ADD COLUMN handBackPath TEXT")
        }

        val MIGRATION_4_5 = androidx.room.migration.Migration(4, 5) { database ->
            database.execSQL("ALTER TABLE users ADD COLUMN faceImagePath TEXT")
        }

        val MIGRATION_5_6 = androidx.room.migration.Migration(5, 6) { database ->
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS api_tokens (
                    token TEXT PRIMARY KEY NOT NULL,
                    userId INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    expiresAt INTEGER NOT NULL,
                    isRevoked INTEGER NOT NULL DEFAULT 0,
                    lastUsedAt INTEGER
                )
            """)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS api_access_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    endpoint TEXT NOT NULL,
                    method TEXT NOT NULL,
                    requestParams TEXT,
                    responseCode INTEGER NOT NULL,
                    responseMessage TEXT,
                    userId INTEGER,
                    username TEXT,
                    ipAddress TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        val MIGRATION_6_7 = androidx.room.migration.Migration(6, 7) { database ->
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS system_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    username TEXT NOT NULL UNIQUE,
                    passwordHash TEXT NOT NULL,
                    passwordType TEXT NOT NULL DEFAULT 'plain',
                    employeeId TEXT,
                    role TEXT NOT NULL DEFAULT 'employee',
                    status TEXT NOT NULL DEFAULT 'active',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        val MIGRATION_7_8 = androidx.room.migration.Migration(7, 8) { database ->
            database.execSQL("ALTER TABLE users ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
            try {
                database.execSQL("ALTER TABLE users ADD COLUMN position TEXT NOT NULL DEFAULT ''")
            } catch (e: Exception) { /* column may exist */ }
            try {
                database.execSQL("ALTER TABLE users ADD COLUMN department TEXT NOT NULL DEFAULT ''")
            } catch (e: Exception) { /* column may exist */ }
            try {
                database.execSQL("ALTER TABLE users ADD COLUMN healthCertCode TEXT NOT NULL DEFAULT ''")
            } catch (e: Exception) { /* column may exist */ }
        }

        val MIGRATION_8_9 = androidx.room.migration.Migration(8, 9) { database ->
            database.execSQL("ALTER TABLE check_records ADD COLUMN isUploaded INTEGER NOT NULL DEFAULT 0")
        }

        val MIGRATION_9_10 = androidx.room.migration.Migration(9, 10) { database ->
            // 1. users 表新增同步字段
            database.execSQL("ALTER TABLE users ADD COLUMN platformVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE users ADD COLUMN faceImageFileId TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN faceImageSha256 TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertImageFileId TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertImageSha256 TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")

            // 2. 创建 sync_outbox 表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    operation_id TEXT PRIMARY KEY NOT NULL,
                    operation_type TEXT NOT NULL,
                    employee_id TEXT NOT NULL,
                    expected_version INTEGER,
                    payload_json TEXT,
                    face_image_action TEXT,
                    face_image_local_path TEXT,
                    face_image_sha256 TEXT,
                    health_cert_image_action TEXT,
                    health_cert_image_local_path TEXT,
                    health_cert_image_sha256 TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_status ON sync_outbox(status)")

            // 3. 创建 sync_state 表（单行）
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_state (
                    id INTEGER PRIMARY KEY NOT NULL CHECK (id = 1),
                    last_cursor INTEGER NOT NULL DEFAULT 0,
                    sync_status TEXT NOT NULL DEFAULT 'IDLE',
                    last_sync_time INTEGER,
                    error_message TEXT
                )
            """)
            database.execSQL("INSERT OR IGNORE INTO sync_state (id, last_cursor, sync_status) VALUES (1, 0, 'IDLE')")

            // 4. 创建 deleted_employee_versions 表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_employee_versions (
                    employee_id TEXT PRIMARY KEY NOT NULL,
                    platform_version INTEGER NOT NULL,
                    deleted_at INTEGER NOT NULL
                )
            """)
        }
    }
}
