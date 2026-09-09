package com.woopi.safehome.domain.deed.application.service

import com.woopi.safehome.domain.deed.domain.model.DeedSections
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * 캐시 키가 문서가 주장하는 성질을 실제로 갖는지 검사한다.
 *
 * 배경: docs/llm-cache-strategy.md
 */
class SectionCacheKeyTest : BehaviorSpec({

    fun sections(vararg pairs: Pair<String, List<String>>) = DeedSections(mapOf(*pairs))

    Given("내용은 같고 공백만 다른 두 등기부") {

        val 원본 = sections(
            "표제부" to listOf("서울특별시 마포구 망원동 123-4", "전유부분 면적: 59.91㎡"),
            "갑구" to listOf("순위1. 소유권보존 홍길동"),
        )
        // 문서 추출 라이브러리가 OS·버전에 따라 내는 차이를 흉내낸다
        val 공백만다름 = sections(
            "표제부" to listOf("  서울특별시 마포구 망원동 123-4  ", "전유부분 면적: 59.91㎡\r\n\r\n"),
            "갑구" to listOf("\r\n순위1. 소유권보존 홍길동\n   "),
        )

        When("키를 만들면") {
            Then("같은 키가 나온다") {
                SectionCacheKey.of(공백만다름, "전세") shouldBe SectionCacheKey.of(원본, "전세")
            }
        }
    }

    Given("섹션을 담은 순서만 다른 두 등기부") {
        val 순서A = sections("갑구" to listOf("가"), "표제부" to listOf("나"))
        val 순서B = sections("표제부" to listOf("나"), "갑구" to listOf("가"))

        When("키를 만들면") {
            Then("같은 키가 나온다") {
                SectionCacheKey.of(순서B, "전세") shouldBe SectionCacheKey.of(순서A, "전세")
            }
        }
    }

    Given("내용이 다른 두 등기부") {
        val a = sections("갑구" to listOf("순위1. 소유권보존 홍길동"))
        val b = sections("갑구" to listOf("순위1. 소유권보존 김철수"))

        When("키를 만들면") {
            Then("다른 키가 나온다") {
                SectionCacheKey.of(a, "전세") shouldNotBe SectionCacheKey.of(b, "전세")
            }
        }
    }

    Given("같은 등기부와 서로 다른 임대차 유형") {
        val 등기부 = sections("갑구" to listOf("순위1. 소유권보존 홍길동"))

        When("키를 만들면") {
            Then("유형마다 다른 키가 나온다") {
                // 같은 등기부라도 유형에 따라 분석 결과가 달라지므로 독립적으로 캐싱해야 한다
                SectionCacheKey.of(등기부, "전세") shouldNotBe SectionCacheKey.of(등기부, "월세")
            }

            Then("유형이 없으면 지정되지 않음을 뜻하는 값으로 채워진다") {
                SectionCacheKey.of(등기부, null) shouldBe SectionCacheKey.of(등기부, "미지정")
            }

            Then("유형이 없는 것과 전세는 다른 키다") {
                SectionCacheKey.of(등기부, null) shouldNotBe SectionCacheKey.of(등기부, "전세")
            }
        }
    }

    Given("고정된 입력") {

        When("키를 만들면") {
            Then("알려진 값이 나온다") {
                // 이 값이 바뀌면 캐시 키 계산이 바뀐 것이고, 기존 캐시가 통째로 미스가 된다.
                // 의도한 변경이라면 저장 시 붙는 버전 접두사를 함께 올린다 - docs/llm-cache-strategy.md
                val 등기부 = sections(
                    "표제부" to listOf("서울특별시 마포구 망원동 123-4"),
                    "갑구" to listOf("순위1. 소유권보존 홍길동"),
                )
                SectionCacheKey.of(등기부, "전세") shouldBe
                    "ce22fad052a2a8aa56bfa923cda92f39e0c34459a741b483d106e8080f66266b:전세"
            }
        }
    }
})
