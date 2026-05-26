package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserDeviceRepository
import com.woopi.safehome.domain.deed.application.port.outbound.UserDeviceQueryPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserDeviceQueryAdapter(
    private val userDeviceRepository: UserDeviceRepository,
) : UserDeviceQueryPort {

    override fun findTokensByUserId(userId: Long): List<String> =
        userDeviceRepository.findAllByUserId(userId).map { it.fcmToken }

    @Transactional
    override fun deleteByFcmToken(fcmToken: String) =
        userDeviceRepository.deleteByFcmToken(fcmToken)
}
