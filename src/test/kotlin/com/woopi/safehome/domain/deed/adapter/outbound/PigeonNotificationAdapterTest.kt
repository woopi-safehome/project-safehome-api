package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.UserDeviceQueryPort
import com.woopi.safehome.global.config.OutboundHttp
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * 알림 발송의 실패 처리를 고정한다.
 *
 * 알림은 결과 저장이 끝난 뒤에 보낸다. 발송 실패가 분석을 되돌리면 안 되므로
 * 예외를 밖으로 내보내지 않는다. 다만 기기가 사라진 경우에는 토큰을 지운다 -
 * 남겨 두면 없는 대상에게 계속 보낸다.
 *
 * 배경: domain/deed/README.md 의 "실패를 어떻게 다루는가"
 */
class PigeonNotificationAdapterTest : BehaviorSpec({

    fun fixture(): Triple<PigeonNotificationAdapter, MockRestServiceServer, UserDeviceQueryPort> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val http = mockk<OutboundHttp>()
        every { http.restClient(any(), any(), any()) } returns builder.build()
        val devices = mockk<UserDeviceQueryPort>(relaxed = true)
        return Triple(PigeonNotificationAdapter("http://pigeon", devices, http), server, devices)
    }

    Given("여러 기기에 보낼 때") {
        When("모두 정상이면") {
            val (adapter, server, devices) = fixture()
            server.expect(ExpectedCount.twice(), anything())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

            adapter.sendPush(listOf("token-a", "token-b"), "job-1")

            Then("기기마다 한 번씩 보낸다") {
                server.verify()
            }
            Then("토큰을 지우지 않는다") {
                verify(exactly = 0) { devices.deleteByFcmToken(any()) }
            }
        }
    }

    Given("발송이 실패할 때") {

        When("서버가 오류를 내면") {
            val (adapter, server, devices) = fixture()
            server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            Then("예외를 밖으로 내보내지 않는다") {
                // 결과 저장은 이미 끝났다. 알림 실패로 분석을 되돌리면 안 된다.
                adapter.sendPush(listOf("token-a"), "job-1")
                verify(exactly = 0) { devices.deleteByFcmToken(any()) }
            }
        }

        When("기기가 더는 없다고 하면") {
            val (adapter, server, devices) = fixture()
            every { devices.deleteByFcmToken(any()) } just Runs
            server.expect(anything()).andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"TOKEN_UNREGISTERED"}""")
            )

            adapter.sendPush(listOf("죽은-토큰"), "job-1")

            Then("그 토큰을 지운다") {
                // 남겨 두면 없는 대상에게 계속 보낸다
                verify(exactly = 1) { devices.deleteByFcmToken("죽은-토큰") }
            }
        }

        When("다른 이유로 거절당하면") {
            val (adapter, server, devices) = fixture()
            server.expect(anything()).andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"RATE_LIMITED"}""")
            )

            adapter.sendPush(listOf("멀쩡한-토큰"), "job-1")

            Then("토큰은 건드리지 않는다") {
                // 일시적인 거절로 기기를 지우면 다시 등록될 때까지 알림이 끊긴다
                verify(exactly = 0) { devices.deleteByFcmToken(any()) }
            }
        }
    }
})
