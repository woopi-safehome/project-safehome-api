package com.woopi.safehome.constraint

import com.woopi.safehome.global.auth.CurrentUserArgumentResolver
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 보호된 엔드포인트가 인증 인자를 실제로 받는지 검사한다.
 *
 * 이 서버에는 보안 필터도 인터셉터도 없다. 인자 리졸버가 인증 헤더를 해석하는 것이
 * 유일한 인증 지점이므로, 컨트롤러가 그 인자를 선언하지 않으면 엔드포인트는
 * 아무 검사 없이 열린다. 컴파일도 되고 다른 테스트도 통과한다.
 *
 * 배경: domain/auth/README.md 의 "인증이 성립하는 방식"
 */
@SpringBootTest
class EndpointAuthenticationConstraintTest(
    // springdoc·actuator 도 자기 매핑을 등록하므로 MVC 본체를 이름으로 지정한다.
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private val handlerMapping: RequestMappingHandlerMapping,
    @Autowired private val currentUserResolver: CurrentUserArgumentResolver,
) : BehaviorSpec({

    // 인증 없이 열려 있어도 되는 경로.
    // 새 엔드포인트의 기본값은 '보호'다. 공개하려면 여기에 의식적으로 추가해야 한다.
    val publicPatterns = setOf(
        "/api/auth/kakao",
        "/api/auth/refresh",
    )

    Given("이 애플리케이션이 등록한 모든 엔드포인트") {

        val ourHandlers = handlerMapping.handlerMethods
            .filterValues { it.beanType.packageName.startsWith("com.woopi.safehome") }

        When("공개 목록에 없는 엔드포인트를 검사하면") {

            val unprotected = ourHandlers
                .filter { (info, handler) ->
                    if (info.patternValues.any { it in publicPatterns }) return@filter false

                    // 조건을 여기에 복제하지 않는다. 실제 리졸버에게 묻는다.
                    // 리졸버가 받아들이지 않으면 그 인자는 인증에 쓰이지 않는다.
                    (0 until handler.method.parameterCount).none { i ->
                        currentUserResolver.supportsParameter(MethodParameter(handler.method, i))
                    }
                }
                .map { (info, handler) ->
                    "${info.patternValues.joinToString()} → ${handler.beanType.simpleName}.${handler.method.name}"
                }
                .sorted()

            Then("인증 인자를 받지 않는 엔드포인트가 하나도 없어야 한다") {
                withClue(
                    "보안 필터가 없어 인증 인자가 유일한 인증 지점이다. 선언하지 않으면 아무 검사 없이 열린다. " +
                        "공개할 엔드포인트라면 이 테스트의 공개 목록에 올린다 — domain/auth/README.md"
                ) {
                    unprotected shouldBe emptyList()
                }
            }
        }
    }
})
