package com.woopi.safehome.domain.auth.adapter.outbound.persistence

import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserDeviceEntity
import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserDeviceRepository
import com.woopi.safehome.domain.auth.application.port.outbound.UserDevicePersistencePort
import org.springframework.stereotype.Component

@Component
class UserDevicePersistenceAdapter(
    private val userDeviceRepository: UserDeviceRepository,
) : UserDevicePersistencePort {

    override fun upsert(userId: Long, fcmToken: String) {
        val existing = userDeviceRepository.findByFcmToken(fcmToken)
        if (existing != null) {
            existing.userId = userId
        } else {
            userDeviceRepository.save(UserDeviceEntity(userId = userId, fcmToken = fcmToken))
        }
    }

    override fun deleteByUserId(userId: Long) {
        userDeviceRepository.deleteAllByUserId(userId)
    }
}
