package com.woopi.safehome.domain.deed.application.port.outbound

interface UserDeviceQueryPort {
    fun findTokensByUserId(userId: Long): List<String>
}
