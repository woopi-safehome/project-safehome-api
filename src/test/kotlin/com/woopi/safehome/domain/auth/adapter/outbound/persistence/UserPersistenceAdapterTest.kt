package com.woopi.safehome.domain.auth.adapter.outbound.persistence

import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserRepository
import com.woopi.safehome.domain.auth.domain.model.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 삭제가 소프트 딜리트임을 고정한다.
 *
 * 행이 남으므로 조회는 항상 삭제 여부를 걸러야 한다. 거르지 않으면 탈퇴한
 * 사용자가 되살아난 것처럼 보인다. 유스케이스 테스트는 이 어댑터를 대역으로
 * 바꾸므로 여기가 망가져도 거기서는 드러나지 않는다.
 *
 * 배경: domain/auth/README.md 의 "불변식"
 */
@SpringBootTest
class UserPersistenceAdapterTest(
    @Autowired private val adapter: UserPersistenceAdapter,
    @Autowired private val repository: UserRepository,
    @Autowired private val tx: TransactionTemplate,
) : BehaviorSpec({

    Given("가입한 사용자") {
        val saved = adapter.save(User.Create(kakaoId = 9_910_001L, nickname = "탈퇴대상"))

        When("아직 살아 있을 때") {
            Then("소셜 식별자로도 내부 식별자로도 찾힌다") {
                adapter.findByKakaoId(9_910_001L).shouldNotBeNull()
                adapter.findById(saved.id).shouldNotBeNull()
            }
        }

        When("삭제하면") {
            // 소프트 딜리트는 엔티티를 고치는 것이라 트랜잭션 안에서만 반영된다.
            // 운영에서는 유스케이스가 트랜잭션을 연다.
            tx.execute { adapter.deleteById(saved.id) }

            Then("조회에서 사라진다") {
                adapter.findByKakaoId(9_910_001L).shouldBeNull()
                adapter.findById(saved.id).shouldBeNull()
            }

            Then("행 자체는 남아 있다") {
                // 지워진 것이 아니라 표시만 된 것이다. 그래서 조회가 걸러 주지 않으면
                // 탈퇴한 사용자가 되살아난 것처럼 보인다.
                val row = repository.findById(saved.id)
                row.isPresent shouldBe true
                row.get().isDeleted shouldBe true
            }
        }
    }
})
