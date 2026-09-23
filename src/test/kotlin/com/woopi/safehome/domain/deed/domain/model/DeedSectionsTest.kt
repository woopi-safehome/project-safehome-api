package com.woopi.safehome.domain.deed.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * 등기부 섹션의 문자열 표현이 본문을 내놓지 않는지 고정한다.
 *
 * 이 객체는 로그에 쉽게 실린다. data class 의 기본 문자열은 필드를 통째로 찍어,
 * 소유자 이름과 주소가 그대로 로그로 나갔다.
 *
 * 배경: domain/deed/README.md 의 "불변식"
 */
class DeedSectionsTest : BehaviorSpec({

    Given("소유자 이름과 주소가 든 등기부 섹션") {
        val sections = DeedSections(
            mapOf(
                "표제부" to listOf("서울특별시 마포구 망원동 123-4"),
                "갑구" to listOf("순위1. 소유권보존 홍길동"),
            ),
        )

        When("문자열로 바꾸면") {
            val text = sections.toString()

            Then("본문은 빠지고 섹션 이름과 분량만 남는다") {
                text shouldNotContain "홍길동"
                text shouldNotContain "망원동"
                text shouldContain "표제부"
                text shouldContain "갑구"
            }
        }
    }
})
