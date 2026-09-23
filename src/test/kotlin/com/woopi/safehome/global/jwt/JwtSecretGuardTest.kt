package com.woopi.safehome.global.jwt

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.mock.env.MockEnvironment

/**
 * 배포 환경이 서명 키를 빠뜨리면 기동을 거부하는지 고정한다.
 *
 * 기본값은 공개 저장소에 적혀 있다. 빠뜨려도 에러 없이 떠서, 누구나 토큰을 위조할 수 있는 상태가
 * 조용히 이어졌다.
 *
 * 배경: global/README.md
 */
class JwtSecretGuardTest : BehaviorSpec({

    fun env(vararg profiles: String, secret: String? = null) = MockEnvironment().apply {
        setActiveProfiles(*profiles)
        if (secret != null) setProperty(JwtSecretGuard.SECRET_ENV, secret)
    }

    val deployed = JwtSecretGuard.DEPLOYED_PROFILES

    Given("배포 환경에서 서명 키가 없으면") {
        Then("dev 는 기동을 거부한다") {
            val e = shouldThrow<IllegalStateException> { JwtSecretGuard.check(env("dev"), deployed) }
            e.message shouldContain "JWT_SECRET"
        }
        Then("prd 도 기동을 거부한다") {
            shouldThrow<IllegalStateException> { JwtSecretGuard.check(env("prd"), deployed) }
        }
        Then("빈 값도 없는 것으로 본다") {
            shouldThrow<IllegalStateException> { JwtSecretGuard.check(env("dev", secret = "  "), deployed) }
        }
    }

    Given("배포 환경에서 서명 키가 있으면") {
        Then("그대로 뜬다") {
            shouldNotThrowAny { JwtSecretGuard.check(env("dev", secret = "a".repeat(64)), deployed) }
        }
    }

    Given("로컬이나 테스트에서는") {
        Then("키가 없어도 기본값으로 뜬다 — 기본값이 있는 이유다") {
            shouldNotThrowAny { JwtSecretGuard.check(env("local"), deployed) }
            shouldNotThrowAny { JwtSecretGuard.check(env(), deployed) }
        }
    }
})
