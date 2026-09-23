package com.woopi.safehome.global.jwt

import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * 배포 환경에서 토큰 서명 키가 주어지지 않았으면 **기동을 거부한다.**
 *
 * 서명 키에는 기본값이 있다 — 로컬에서 환경 변수 없이 바로 띄우기 위해서다. 그런데 저장소가 공개라
 * 그 기본값은 누구나 안다. 배포 환경이 키를 빠뜨리면 서버는 **에러 없이** 기본값으로 서명하고,
 * 누구나 아무 회원의 토큰을 만들어 낼 수 있게 된다. 실제로 개발 서버가 그 상태로 넉 달을 돌았다.
 * 조용히 뜨는 것보다 뜨지 않는 편이 낫다 — 배포는 헬스 체크를 기다리므로 실패가 드러난다.
 *
 * 로컬과 테스트(프로파일 없음)는 막지 않는다. 기본값이 있는 이유가 그것이다.
 */
@Component
class JwtSecretGuard(private val environment: Environment) : InitializingBean {

    override fun afterPropertiesSet() {
        check(environment, DEPLOYED_PROFILES)
    }

    companion object {
        /** 기본값이 허용되지 않는 프로파일. 새 배포 프로파일을 만들면 여기에 더한다. */
        val DEPLOYED_PROFILES = listOf("dev", "prd")

        /** 설정 파일이 서명 키를 읽는 환경 변수 이름. application-auth.yml 과 같아야 한다. */
        const val SECRET_ENV = "JWT_SECRET"

        fun check(environment: Environment, deployedProfiles: List<String>) {
            val deployed = environment.acceptsProfiles(Profiles.of(*deployedProfiles.toTypedArray()))
            val provided = !environment.getProperty(SECRET_ENV).isNullOrBlank()
            if (deployed && !provided) {
                throw IllegalStateException(
                    "$SECRET_ENV 가 없다. 배포 환경(${deployedProfiles.joinToString()})에서는 저장소에 공개된 기본 서명 키를 쓸 수 없다 — " +
                        "누구나 토큰을 위조할 수 있다. 서버의 API 환경 파일에 $SECRET_ENV 를 넣고 컨테이너를 새로 만든다. " +
                        "배경: global/README.md",
                )
            }
        }
    }
}
