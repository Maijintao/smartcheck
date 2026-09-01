package com.smartcheck.app.data.upload

import java.net.URI

internal object PlatformUrlResolver {
    private const val MORNING_CHECK_ENDPOINT = "/api/device/morning-check/upload"
    private const val LEGACY_EXECUTION_SUFFIX = "/execution"
    private const val API_SUFFIX = "/api"

    fun morningCheckUploadUrl(configuredUrl: String): String {
        val uri = runCatching { URI(configuredUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("平台地址格式无效", it) }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "平台地址必须以http://或https://开头"
        }
        require(!uri.rawAuthority.isNullOrBlank()) { "平台地址缺少主机名" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "平台地址不能包含查询参数或锚点" }

        val configuredPath = uri.rawPath.orEmpty().trimEnd('/')
        val basePath = when {
            configuredPath.endsWith(MORNING_CHECK_ENDPOINT, ignoreCase = true) ->
                configuredPath.dropLast(MORNING_CHECK_ENDPOINT.length)
            configuredPath.endsWith(LEGACY_EXECUTION_SUFFIX, ignoreCase = true) ->
                configuredPath.dropLast(LEGACY_EXECUTION_SUFFIX.length)
            configuredPath.endsWith(API_SUFFIX, ignoreCase = true) ->
                configuredPath.dropLast(API_SUFFIX.length)
            else -> configuredPath
        }.trimEnd('/')

        return URI(
            scheme,
            uri.rawAuthority,
            "$basePath$MORNING_CHECK_ENDPOINT",
            null,
            null,
        ).toASCIIString()
    }
}
