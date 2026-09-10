package com.smartcheck.app.api

object DeviceApiContract {
    const val HEALTH = "/health"
    const val LOGIN = "/api/auth/login"
    const val USER_SYNC = "/api/users/sync"

    const val RECORDS = "/api/records"
    const val RECORD_SYNC = "/api/records/sync"
    const val RECORD_STATISTICS = "/api/records/statistics"
    const val RECORD_DETAIL = "/api/records/{id}"
    const val RECORD_EXPORT = "/api/records/export"

    const val EMPLOYEES = "/api/employees"
    const val EMPLOYEE_SYNC = "/api/employees/sync"
    const val EMPLOYEE_IMPORT = "/api/employees/import"
    const val EMPLOYEE_UPLOAD_PHOTO = "/api/employees/upload-photo"
    const val EMPLOYEE_UPLOAD_CERT_PHOTO = "/api/employees/upload-cert-photo"
    const val EMPLOYEE_CLEAR_ALL = "/api/employees/clear-all"
    const val EMPLOYEE_DELETE = "/api/employees/{employeeId}"

    const val RECORD_IMAGE = "/api/images/{filename}"
    const val EMPLOYEE_IMAGE = "/api/employee-images/{filename}"
    const val DOWNLOAD = "/api/downloads/{filename}"

    fun recordDetailPath(id: Long): String = "/api/records/$id"
    fun employeeDeletePath(employeeId: String): String = "/api/employees/$employeeId"
    fun recordImagePath(filename: String): String = "/api/images/$filename"
    fun employeeImagePath(filename: String): String = "/api/employee-images/$filename"
    fun downloadPath(filename: String): String = "/api/downloads/$filename"
}
