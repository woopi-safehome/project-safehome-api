package com.woopi.safehome.domain.auth.adapter.outbound

import com.woopi.safehome.global.config.OutboundHttp
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

/**
 * 소셜 제공자 응답을 어떻게 읽는지 고정한다.
 *
 * 응답 형태는 우리가 정하지 않는다. 중첩이 깊고 없을 수 있는 자리가 많아
 * 빠뜨리면 사용자 정보가 조용히 비어 들어온다.
 *
 * 유스케이스 테스트는 이 어댑터를 대역으로 바꾸므로 여기가 망가져도
 * 거기서는 드러나지 않는다.
 */
class KakaoApiAdapterTest : BehaviorSpec({

    fun fixture(): Pair<KakaoApiAdapter, MockRestServiceServer> {
        val template = RestTemplate()
        val server = MockRestServiceServer.bindTo(template).build()
        val http = mockk<OutboundHttp>()
        every { http.restTemplate(any(), any()) } returns template
        return KakaoApiAdapter("admin-key", http) to server
    }

    Given("소셜 사용자 정보 조회") {

        When("프로필이 모두 있으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer social-token"))
                .andRespond(
                    withSuccess(
                        """{"id":12345,"kakao_account":{"profile":{"nickname":"홍길동","profile_image_url":"http://img"}}}""",
                        MediaType.APPLICATION_JSON,
                    )
                )

            val info = adapter.getUserInfo("social-token")

            Then("식별자와 프로필을 그대로 읽는다") {
                info.kakaoId shouldBe 12345L
                info.nickname shouldBe "홍길동"
                info.profileImageUrl shouldBe "http://img"
            }
        }

        When("프로필이 비어 있으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("""{"id":999}""", MediaType.APPLICATION_JSON))

            val info = adapter.getUserInfo("social-token")

            Then("식별자는 살리고 나머지는 기본값으로 채운다") {
                // 프로필 제공 동의는 선택이다. 없다고 로그인을 막지 않는다.
                info.kakaoId shouldBe 999L
                info.nickname shouldBe "사용자"
                info.profileImageUrl.shouldBeNull()
            }
        }

        When("식별자가 없으면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("""{"kakao_account":{}}""", MediaType.APPLICATION_JSON))

            Then("소셜 오류로 끊는다") {
                // 식별자가 없으면 사용자를 특정할 수 없다. 기본값으로 때우면 안 된다.
                shouldThrow<BusinessException> { adapter.getUserInfo("social-token") }
                    .errorCode shouldBe ErrorCode.KAKAO_API_ERROR
            }
        }

        When("소셜 쪽이 거절하면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            Then("소셜 오류로 바꿔서 올린다") {
                // 상류의 상태 코드를 그대로 흘리지 않는다. 우리 에러 코드로 바꾼다.
                shouldThrow<BusinessException> { adapter.getUserInfo("bad-token") }
                    .errorCode shouldBe ErrorCode.KAKAO_API_ERROR
            }
        }
    }

    Given("연결 해제") {
        When("요청하면") {
            val (adapter, server) = fixture()
            server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andExpect(header("Authorization", "KakaoAK admin-key"))
                .andRespond(withSuccess("""{"id":12345}""", MediaType.APPLICATION_JSON))

            adapter.unlinkUser(12345L)

            Then("관리자 키로 부른다") {
                // 사용자 토큰이 아니라 관리자 키를 쓴다. 탈퇴 시점에 사용자 토큰이 없을 수 있다.
                server.verify()
            }
        }
    }
})
