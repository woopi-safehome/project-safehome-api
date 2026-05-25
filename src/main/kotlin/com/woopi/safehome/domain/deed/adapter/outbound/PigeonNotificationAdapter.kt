package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.NotificationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class PigeonNotificationAdapter(
    @Value("\${safehome.notification.pigeon-url}") private val pigeonUrl: String,
) : NotificationPort {

    private val log = LoggerFactory.getLogger(PigeonNotificationAdapter::class.java)
    private val restClient = RestClient.create()

    override fun sendPush(fcmTokens: List<String>, jobId: String) {
        fcmTokens.forEach { token ->
            try {
                restClient.post()
                    .uri("$pigeonUrl/api/messages/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(token, jobId))
                    .retrieve()
                    .toBodilessEntity()
                log.info("[Notification] FCM 발송 성공. jobId={}, token={}", jobId, token.take(20))
            } catch (e: Exception) {
                log.warn("[Notification] pigeon 호출 실패 (무시). jobId={}, error={}", jobId, e.message)
            }
        }
    }

    private fun buildPayload(fcmToken: String, jobId: String) = mapOf(
        "channel" to "FCM",
        "to" to fcmToken,
        "title" to "분석 완료",
        "body" to "등기부등본 분석이 완료되었습니다.",
        "data" to mapOf(
            "jobId" to jobId,
            "route" to "/result/$jobId",
        ),
    )
}
