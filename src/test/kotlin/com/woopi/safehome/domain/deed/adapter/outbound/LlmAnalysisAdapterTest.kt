package com.woopi.safehome.domain.deed.adapter.outbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.woopi.safehome.domain.deed.domain.model.DeedSections
import com.woopi.safehome.global.config.OutboundHttp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * 분석 서버 응답에서 무엇을 꺼내는지 고정한다.
 *
 * 상류는 분석 결과를 봉투에 담아 보낸다. 이 어댑터는 그 안쪽만 꺼내 저장한다.
 * 봉투째 저장하면 이력 목록의 요약 추출이 조용히 빈 값을 집는다 - 요약은
 * 안쪽 필드를 보기 때문이다.
 *
 * 유스케이스 테스트는 이 어댑터를 대역으로 바꾸므로 여기가 망가져도
 * 거기서는 드러나지 않는다.
 */
class LlmAnalysisAdapterTest : BehaviorSpec({

    val 섹션 = DeedSections(mapOf("갑구" to listOf("순위1. 소유권보존")))

    fun fixture(): Pair<LlmAnalysisAdapter, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl("http://ai-test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val http = mockk<OutboundHttp>()
        every { http.restClient(any(), any(), any()) } returns builder.build()
        return LlmAnalysisAdapter(ObjectMapper(), "http://ai-test", http) to server
    }

    Given("분석 서버가 정상 응답할 때") {

        When("결과를 받으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("http://ai-test/api/deed/analyze"))
                .andExpect(jsonPath("$.sections.갑구[0]").value("순위1. 소유권보존"))
                .andExpect(jsonPath("$.leaseType").value("전세"))
                .andRespond(
                    withSuccess(
                        """{"analysis":{"safetyLevel":"DANGER","propertyInfo":{"address":"서울"}},"usage":{"total_tokens":10}}""",
                        MediaType.APPLICATION_JSON,
                    )
                )

            val result = adapter.analyze(섹션, "전세")

            Then("봉투가 아니라 안쪽만 돌려준다") {
                result shouldContain "\"safetyLevel\":\"DANGER\""
                result.contains("usage") shouldBe false
                result.trimStart().startsWith("{\"safetyLevel\"") shouldBe true
            }
        }

        When("임대차 유형이 없으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("http://ai-test/api/deed/analyze"))
                .andExpect(jsonPath("$.leaseType").doesNotExist())
                .andRespond(withSuccess("""{"analysis":{}}""", MediaType.APPLICATION_JSON))

            adapter.analyze(섹션, null)

            Then("아예 보내지 않는다") {
                // 상류가 없으면 "미지정"으로 채운다. 빈 문자열을 보내면 그 분기가 어긋난다.
                server.verify()
            }
        }
    }

    Given("분석 서버가 봉투를 잘못 보낼 때") {
        When("안쪽이 없으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("http://ai-test/api/deed/analyze"))
                .andRespond(withSuccess("""{"usage":{}}""", MediaType.APPLICATION_JSON))

            Then("조용히 넘기지 않고 실패로 다룬다") {
                shouldThrow<IllegalStateException> { adapter.analyze(섹션, "전세") }
            }
        }
    }

    Given("분석 서버가 오류를 낼 때") {
        When("500 을 받으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("http://ai-test/api/deed/analyze"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("""{"error":"boom"}"""))

            Then("예외로 올린다") {
                // 작업을 실패로 기록해야 사용자가 원인을 안다. 빈 결과로 때우지 않는다.
                shouldThrow<RestClientResponseException> { adapter.analyze(섹션, "전세") }
            }
        }
    }
})
