package com.woopi.safehome.domain.auth.application.port.outbound

interface UserDevicePersistencePort {
    fun upsert(userId: Long, fcmToken: String)
    fun deleteByUserId(userId: Long)
}
