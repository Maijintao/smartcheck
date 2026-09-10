package com.smartcheck.app.api

object PlatformApiContract {
    const val HEARTBEAT_PATH = "/api/device/refresh"
    const val MORNING_CHECK_UPLOAD_PATH = "/api/device/morning-check/upload"
    const val MORNING_CHECK_SUMMARY_UPLOAD_PATH = "/api/device/morning-check/summary/upload"
    const val EMPLOYEE_CHANGES_PATH = "/api/device/employees/changes"
    const val EMPLOYEE_SNAPSHOT_PATH = "/api/device/employees/snapshot"

    fun employeeDetailPath(employeeId: String): String =
        "/api/device/employees/$employeeId"

    fun employeeImagePath(fileId: String): String =
        "/api/device/employees/images/$fileId"
}
