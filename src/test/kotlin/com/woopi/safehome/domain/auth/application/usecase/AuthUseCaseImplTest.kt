package com.woopi.safehome.domain.auth.application.usecase

import com.woopi.safehome.domain.auth.application.port.outbound.KakaoApiPort
import com.woopi.safehome.domain.auth.application.port.outbound.UserDevicePersistencePort
import com.woopi.safehome.domain.auth.application.port.outbound.UserPersistencePort
import com.woopi.safehome.domain.auth.domain.model.KakaoUserInfo
import com.woopi.safehome.domain.auth.domain.model.User
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import com.woopi.safehome.global.jwt.JwtProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * 인증 유스케이스의 관통 흐름을 포트 대역으로 검증한다.
 *
 * 바깥으로 나가는 길이 전부 포트 인터페이스라 DB 도 카카오도 없이 검증할 수 있다.
 * 배경: domain/README.md 의 "이 구조를 택한 이유 - 테스트"
 */
class AuthUseCaseImplTest : BehaviorSpec({

    fun fixture(): Triple<AuthUseCaseImpl, Map<String, Any>, Unit> {
        val users = mockk<UserPersistencePort>()
        val devices = mockk<UserDevicePersistencePort>()
        val kakao = mockk<KakaoApiPort>()
        val jwt = mockk<JwtProvider>()

        every { jwt.generateAccessToken(any()) } returns "access-token"
        every { jwt.generateRefreshToken(any()) } returns "refresh-token"
        every { jwt.accessTokenExpiry } returns 3600L

        return Triple(
            AuthUseCaseImpl(users, devices, kakao, jwt),
            mapOf("users" to users, "devices" to devices, "kakao" to kakao, "jwt" to jwt),
            Unit,
        )
    }

    Given("소셜 로그인") {

        When("이미 가입한 사용자면") {
            val (useCase, ports, _) = fixture()
            val users = ports["users"] as UserPersistencePort
            val kakao = ports["kakao"] as KakaoApiPort

            every { kakao.getUserInfo("social-token") } returns
                KakaoUserInfo(kakaoId = 7L, nickname = "기존", profileImageUrl = null)
            every { users.findByKakaoId(7L) } returns
                User.Data(id = 1L, kakaoId = 7L, nickname = "기존")

            val result = useCase.kakaoLogin("social-token")

            Then("신규가 아니라고 알린다") {
                result.isNewUser shouldBe false
            }
            Then("사용자를 새로 만들지 않는다") {
                verify(exactly = 0) { users.save(any()) }
            }
        }

        When("처음 오는 사용자면") {
            val (useCase, ports, _) = fixture()
            val users = ports["users"] as UserPersistencePort
            val kakao = ports["kakao"] as KakaoApiPort

            every { kakao.getUserInfo("social-token") } returns
                KakaoUserInfo(kakaoId = 8L, nickname = "신규", profileImageUrl = "http://img")
            every { users.findByKakaoId(8L) } returns null
            every { users.save(any()) } returns
                User.Data(id = 2L, kakaoId = 8L, nickname = "신규")

            val result = useCase.kakaoLogin("social-token")

            Then("사용자를 만들고 신규라고 알린다") {
                result.isNewUser shouldBe true
                verify(exactly = 1) { users.save(User.Create(8L, "신규", "http://img")) }
            }
        }
    }

    Given("토큰 갱신") {

        When("갱신 토큰이 유효하면") {
            val (useCase, ports, _) = fixture()
            val jwt = ports["jwt"] as JwtProvider
            every { jwt.validateRefreshToken("good") } returns 5L

            val pair = useCase.refresh("good")

            Then("접근 토큰만이 아니라 토큰 쌍을 새로 발급한다") {
                pair.accessToken shouldNotBe null
                pair.refreshToken shouldNotBe null
                verify(exactly = 1) { jwt.generateAccessToken(5L) }
                verify(exactly = 1) { jwt.generateRefreshToken(5L) }
            }
        }

        When("갱신 토큰이 유효하지 않으면") {
            val (useCase, ports, _) = fixture()
            val jwt = ports["jwt"] as JwtProvider
            // 검증 함수는 던지지 않고 빈 값을 돌려준다 - 확인하지 않으면 그냥 흘러간다
            every { jwt.validateRefreshToken("bad") } returns null

            Then("인증 실패로 끊는다") {
                shouldThrow<BusinessException> { useCase.refresh("bad") }
                    .errorCode shouldBe ErrorCode.UNAUTHORIZED
            }
        }
    }

    Given("회원 탈퇴") {

        When("탈퇴를 처리하면") {
            val (useCase, ports, _) = fixture()
            val users = ports["users"] as UserPersistencePort
            val devices = ports["devices"] as UserDevicePersistencePort
            val kakao = ports["kakao"] as KakaoApiPort

            every { users.findById(3L) } returns User.Data(id = 3L, kakaoId = 9L, nickname = "탈퇴")
            every { kakao.unlinkUser(9L) } just Runs
            every { devices.deleteByUserId(3L) } just Runs
            every { users.deleteById(3L) } just Runs

            useCase.withdraw(3L)

            Then("소셜 연결을 먼저 끊고 그다음 사용자를 지운다") {
                // 순서가 뒤바뀌면 연결 해제에 필요한 식별자를 잃어 외부 연결이 남는다
                verifyOrder {
                    kakao.unlinkUser(9L)
                    users.deleteById(3L)
                }
            }

            Then("푸시 디바이스도 함께 정리한다") {
                // 남겨 두면 없는 사용자에게 발송을 시도한다
                verify(exactly = 1) { devices.deleteByUserId(3L) }
            }
        }

        When("없는 사용자면") {
            val (useCase, ports, _) = fixture()
            val users = ports["users"] as UserPersistencePort
            val kakao = ports["kakao"] as KakaoApiPort
            every { users.findById(404L) } returns null

            Then("찾을 수 없다고 끊고 외부 연결에 손대지 않는다") {
                shouldThrow<BusinessException> { useCase.withdraw(404L) }
                    .errorCode shouldBe ErrorCode.NOT_FOUND
                verify(exactly = 0) { kakao.unlinkUser(any()) }
            }
        }
    }
})
