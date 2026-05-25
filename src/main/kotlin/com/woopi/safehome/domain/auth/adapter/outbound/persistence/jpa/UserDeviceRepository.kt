package com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface UserDeviceRepository : JpaRepository<UserDeviceEntity, Long> {
    fun findByFcmToken(fcmToken: String): UserDeviceEntity?
    fun findAllByUserId(userId: Long): List<UserDeviceEntity>
    fun deleteAllByUserId(userId: Long)
}
