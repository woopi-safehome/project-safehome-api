package com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 감사 정보가 저장 시점에 자동으로 채워지는지 검사한다.
 *
 * 생성·수정 주체와 시각은 BaseEntity 의 리스너가 채운다. 배선이 끊겨도
 * 저장 자체는 성공하므로, 값이 비어 있는 것을 나중에야 알게 된다.
 *
 * 배경: global/README.md 의 "무엇이 여기 있나 - 감사"
 */
@SpringBootTest
class UserEntityAuditTest(
    @Autowired
    private val userRepository: UserRepository,
) : BehaviorSpec({

    Given("사용자 엔티티를 저장할 때") {

        When("처음 저장하면") {
            val saved = userRepository.save(
                UserEntity(
                    kakaoId = 9_900_001L,
                    nickname = "audit-test",
                )
            )

            Then("생성·수정 감사 정보가 자동으로 채워진다") {
                saved.id.shouldNotBeNull()

                saved.createdId shouldBe 1L
                saved.createdAt.shouldNotBeNull()

                saved.updatedId shouldBe 1L
                saved.updatedAt.shouldNotBeNull()
            }

            When("값을 수정해서 다시 저장하면") {
                val beforeUpdatedAt = saved.updatedAt

                saved.nickname = "audit-test-modified"
                val updated = userRepository.save(saved)

                Then("수정 감사 정보가 갱신된다") {
                    updated.updatedId shouldBe 1L
                    updated.updatedAt.shouldNotBeNull()
                    updated.updatedAt!! shouldBeGreaterThan beforeUpdatedAt!!
                }
            }
        }
    }
})
