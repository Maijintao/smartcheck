package com.smartcheck.app.domain.model

enum class HealthCertStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    NOT_PROVIDED,
    REVOKED,
    NOT_CHECKED,
    UNKNOWN
}
