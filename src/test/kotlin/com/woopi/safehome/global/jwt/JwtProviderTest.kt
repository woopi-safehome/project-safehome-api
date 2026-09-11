package com.woopi.safehome.global.jwt

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * 토큰 검증의 실패 방식을 고정한다.
 *
 * 검증 함수는 실패해도 던지지 않고 빈 값을 돌려준다. 호출한 쪽이 그 값을 확인하지
 * 않으면 인증 실패가 성공처럼 흘러간다. 유스케이스 테스트는 이 클래스를 대역으로
 * 바꾸므로 여기가 망가져도 거기서는 드러나지 않는다.
 *
 * 배경: global/README.md 의 "토큰 검증 실패는 예외가 아니라 빈 값이다"
 */
class JwtProviderTest : BehaviorSpec({

    val provider = JwtProvider(
        secret = "test-secret-must-be-at-least-32-bytes-long",
        accessTokenExpiry = 3600,
        refreshTokenExpiry = 1209600,
    )

    Given("정상적으로 발급한 토큰") {
        val access = provider.generateAccessToken(42L)
        val refresh = provider.generateRefreshToken(42L)

        When("같은 종류로 검증하면") {
            Then("사용자 식별자가 나온다") {
                provider.validateAccessToken(access) shouldBe 42L
                provider.validateRefreshToken(refresh) shouldBe 42L
            }
        }

        When("종류를 바꿔서 검증하면") {
            Then("예외가 아니라 빈 값이 나온다") {
                // 토큰 안에 종류가 들어 있어 서로 바꿔 쓸 수 없다.
                // 던지지 않으므로 호출한 쪽이 확인하지 않으면 그대로 흘러간다.
                provider.validateRefreshToken(access).shouldBeNull()
                provider.validateAccessToken(refresh).shouldBeNull()
            }
        }
    }

    Given("믿을 수 없는 토큰") {

        When("다른 비밀값으로 서명된 토큰을 검증하면") {
            val forged = JwtProvider(
                secret = "another-secret-also-at-least-32-bytes-ok",
                accessTokenExpiry = 3600,
                refreshTokenExpiry = 1209600,
            ).generateAccessToken(42L)

            Then("빈 값이 나온다") {
                provider.validateAccessToken(forged).shouldBeNull()
            }
        }

        When("토큰 형식이 아닌 문자열을 검증하면") {
            Then("예외를 던지지 않고 빈 값이 나온다") {
                listOf("", "not-a-token", "a.b.c").forEach {
                    provider.validateAccessToken(it).shouldBeNull()
                    provider.validateRefreshToken(it).shouldBeNull()
                }
            }
        }
    }
})
