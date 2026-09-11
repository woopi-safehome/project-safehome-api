package com.woopi.safehome.domain.auth.adapter.inbound.web

import com.woopi.safehome.domain.auth.application.port.inbound.AuthUseCase
import com.woopi.safehome.domain.auth.domain.model.AuthResult
import com.woopi.safehome.domain.auth.domain.model.TokenPair
import com.woopi.safehome.global.auth.CurrentUser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 인증·사용자 요청을 유스케이스로 옮기는 과정을 고정한다.
 *
 * 이 계층은 형식 변환만 한다. 잘못 옮겨도 컴파일은 되고, 유스케이스 테스트는
 * 값을 직접 만들어 넣으므로 변환 실수가 드러나지 않는다.
 */
class AuthWebAdapterTest : BehaviorSpec({

    val 고정사용자 = object : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUser::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?,
        ) = 55L
    }

    Given("소셜 로그인 요청") {
        val useCase = mockk<AuthUseCase>()
        val mvc = MockMvcBuilders.standaloneSetup(AuthInboundWebAdapter(useCase)).build()

        When("소셜 토큰을 보내면") {
            val token = slot<String>()
            every { useCase.kakaoLogin(capture(token)) } returns
                AuthResult("access", "refresh", 3600, isNewUser = true)

            mvc.perform(
                post("/api/auth/kakao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kakaoAccessToken":"social-token"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.isNewUser").value(true))

            Then("본문의 토큰을 그대로 넘긴다") {
                token.captured shouldBe "social-token"
            }
        }
    }

    Given("토큰 갱신 요청") {
        val useCase = mockk<AuthUseCase>()
        val mvc = MockMvcBuilders.standaloneSetup(AuthInboundWebAdapter(useCase)).build()

        When("갱신 토큰을 보내면") {
            every { useCase.refresh("refresh-token") } returns TokenPair("new-a", "new-r", 3600)

            mvc.perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"refresh-token"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("new-a"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-r"))

            Then("접근 토큰만이 아니라 쌍을 그대로 내보낸다") {
                verify(exactly = 1) { useCase.refresh("refresh-token") }
            }
        }
    }

    Given("사용자 요청") {
        val useCase = mockk<AuthUseCase>(relaxed = true)
        val mvc = MockMvcBuilders
            .standaloneSetup(UserInboundWebAdapter(useCase))
            .setCustomArgumentResolvers(고정사용자)
            .build()

        When("디바이스를 등록하면") {
            mvc.perform(
                post("/api/users/devices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fcmToken":"fcm-1"}""")
            ).andExpect(status().isOk)

            Then("인증된 사용자와 본문의 토큰을 함께 넘긴다") {
                // 사용자를 본문에서 받으면 남의 기기를 등록할 수 있다
                verify(exactly = 1) { useCase.registerDevice(55L, "fcm-1") }
            }
        }

        When("탈퇴를 요청하면") {
            mvc.perform(delete("/api/users/me")).andExpect(status().isOk)

            Then("인증된 사용자로 처리한다") {
                verify(exactly = 1) { useCase.withdraw(55L) }
            }
        }
    }
})
