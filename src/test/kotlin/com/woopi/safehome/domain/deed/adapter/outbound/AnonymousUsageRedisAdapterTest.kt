package com.woopi.safehome.domain.deed.adapter.outbound

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * 비회원 사용량을 되돌릴 때 음수 키가 남지 않는지 고정한다.
 *
 * 자정 전에 세고 자정 뒤에 되돌리면 오늘 키가 없다. 그대로 줄이면 만료 없는 음수 키가 남아
 * 다음 사람이 한도를 넘어 쓰게 된다.
 *
 * 배경: domain/deed/README.md 의 "불변식"
 */
class AnonymousUsageRedisAdapterTest : BehaviorSpec({

    Given("오늘 키가 없는 상태에서 되돌리면") {
        val ops = mockk<ValueOperations<String, String>>()
        val redis = mockk<StringRedisTemplate>(relaxed = true)
        every { redis.opsForValue() } returns ops
        every { ops.decrement(any()) } returns -1L

        When("주소와 함께 되돌리면") {
            AnonymousUsageRedisAdapter(redis).refund("1.2.3.4")

            Then("음수가 된 키를 지운다 — 주소 키와 전체 키 모두") {
                verify(exactly = 2) { redis.delete(any<String>()) }
            }
        }
    }

    Given("센 기록이 있는 상태에서 되돌리면") {
        val ops = mockk<ValueOperations<String, String>>()
        val redis = mockk<StringRedisTemplate>(relaxed = true)
        every { redis.opsForValue() } returns ops
        every { ops.decrement(any()) } returns 2L

        When("주소 없이 되돌리면") {
            AnonymousUsageRedisAdapter(redis).refund(null)

            Then("전체 키만 줄이고 지우지 않는다") {
                verify(exactly = 1) { ops.decrement(match { it.endsWith(":total") }) }
                verify(exactly = 0) { redis.delete(any<String>()) }
            }
        }
    }

    Given("저장소에 닿지 못하면") {
        val redis = mockk<StringRedisTemplate>()
        every { redis.opsForValue() } throws IllegalStateException("연결 실패")

        When("되돌리면") {
            Then("예외를 내지 않는다 — 이미 기록된 실패를 흔들지 않는다") {
                AnonymousUsageRedisAdapter(redis).refund("1.2.3.4")
            }
        }
    }
})
