package com.smartcheck.app.data.remote

import java.net.URI

object CloudApiUrl {
    const val DEFAULT_BASE_URL = "https://psxyg.iyouxin.com/api"
    const val EMPLOYEE_ENDPOINT = "/wosapi/YGCJRobotOpenApi/PageStaff"
    const val RECORD_UPLOAD_ENDPOINT = "/kitchen/morningCheck/saveData"

    fun normalizeBaseUrl(rawValue: String): Result<String> = runCatching {
        val value = rawValue.trim()
        require(value.isNotEmpty()) { "服务器地址不能为空" }

        val uri = URI(value)
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "服务器地址必须以 http:// 或 https:// 开头"
        }
        require(!uri.host.isNullOrBlank()) { "服务器地址缺少有效主机名或 IP" }
        require(uri.rawUserInfo == null) { "服务器地址不能包含用户名或密码" }
        require(uri.rawQuery == null) { "服务器地址不能包含查询参数" }
        require(uri.rawFragment == null) { "服务器地址不能包含锚点" }

        value.trimEnd('/')
    }

    fun buildUrl(baseUrl: String, endpoint: String): String {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl).getOrThrow()
        return "$normalizedBaseUrl/${endpoint.trimStart('/')}"
    }

    fun resolveUrl(baseUrl: String, value: String): String {
        val trimmedValue = value.trim()
        val uri = runCatching { URI(trimmedValue) }.getOrNull()
        if (uri?.scheme.equals("http", ignoreCase = true) || uri?.scheme.equals("https", ignoreCase = true)) {
            return trimmedValue
        }
        return buildUrl(baseUrl, trimmedValue)
    }
}
