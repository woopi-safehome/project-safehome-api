package com.woopi.safehome.domain.auth.adapter.outbound.persistence

import com.woopi.safehome.domain.auth.application.port.outbound.UserDevicePersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.UserDeviceQueryPort
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * 푸시 대상 기기의 등록과 정리를 고정한다.
 *
 * 같은 토큰을 다시 등록해도 실패하지 않는다. 토큰에 유일 제약이 걸려 있어
 * 기기 주인이 바뀌면 소유자가 갱신돼야 한다 - 그러지 않으면 예전 주인에게
 * 알림이 간다.
 *
 * 배경: domain/auth/README.md 의 "관통 흐름 - 디바이스 등록"
 */
@SpringBootTest
class UserDevicePersistenceAdapterTest(
    @Autowired private val devices: UserDevicePersistencePort,
    @Autowired private val query: UserDeviceQueryPort,
    @Autowired private val tx: TransactionTemplate,
) : BehaviorSpec({

    Given("기기 토큰 등록") {

        When("처음 등록하면") {
            tx.execute { devices.upsert(7_710_001L, "token-new") }

            Then("그 사용자의 대상이 된다") {
                query.findTokensByUserId(7_710_001L) shouldContainExactly listOf("token-new")
            }
        }

        When("같은 토큰을 다른 사용자가 등록하면") {
            tx.execute { devices.upsert(7_710_002L, "token-moved") }
            tx.execute { devices.upsert(7_710_003L, "token-moved") }

            Then("소유자가 새 사용자로 옮겨간다") {
                // 기기를 물려주거나 계정을 바꾼 경우다. 옛 주인에게 알림이 가면 안 된다.
                query.findTokensByUserId(7_710_002L).isEmpty() shouldBe true
                query.findTokensByUserId(7_710_003L) shouldContainExactly listOf("token-moved")
            }
        }
    }

    Given("탈퇴 정리") {
        When("사용자의 기기를 모두 지우면") {
            tx.execute { devices.upsert(7_710_004L, "token-a") }
            tx.execute { devices.upsert(7_710_004L, "token-b") }
            tx.execute { devices.deleteByUserId(7_710_004L) }

            Then("발송 대상에서 사라진다") {
                // 남겨 두면 없는 사용자에게 발송을 시도한다
                query.findTokensByUserId(7_710_004L).isEmpty() shouldBe true
            }
        }
    }

    Given("만료된 토큰") {
        When("토큰 하나만 지우면") {
            tx.execute { devices.upsert(7_710_005L, "token-dead") }
            tx.execute { devices.upsert(7_710_005L, "token-live") }
            tx.execute { query.deleteByFcmToken("token-dead") }

            Then("그 토큰만 빠지고 나머지는 남는다") {
                query.findTokensByUserId(7_710_005L) shouldContainExactly listOf("token-live")
            }
        }
    }
})
