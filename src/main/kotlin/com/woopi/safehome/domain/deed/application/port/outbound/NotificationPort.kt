package com.woopi.safehome.domain.deed.application.port.outbound

interface NotificationPort {
    fun sendPush(fcmTokens: List<String>, jobId: String)
}
