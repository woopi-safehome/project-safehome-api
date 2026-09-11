package com.woopi.safehome.domain.deed.adapter.outbound

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * 캐시 실패를 어떻게 다루는지 고정한다.
 *
 * 캐시는 비용 절감 수단이지 가용성의 전제가 아니다. 저장소가 죽어 있을 때
 * 분석까지 실패하면 안 된다. 유스케이스 테스트는 이 어댑터를 대역으로 바꾸므로
 * 여기가 망가져도 거기서는 드러나지 않는다.
 *
 * 배경: docs/llm-cache-strategy.md 의 "히트와 미스"
 */
class LlmCacheAdapterTest : BehaviorSpec({

    fun fixture(): Pair<LlmCacheAdapter, ValueOperations<String, String>> {
        val ops = mockk<ValueOperations<String, String>>()
        val template = mockk<StringRedisTemplate>()
        every { template.opsForValue() } returns ops
        return LlmCacheAdapter(template) to ops
    }

    Given("캐시 저장소가 살아 있을 때") {

        When("저장된 값이 있으면") {
            val (adapter, ops) = fixture()
            val key = slot<String>()
            every { ops.get(capture(key)) } returns "{\"analysis\":1}"

            val found = adapter.get("hash:전세")

            Then("그대로 돌려준다") {
                found shouldBe "{\"analysis\":1}"
            }
            Then("키에 버전 접두사가 붙는다") {
                // 접두사를 올리는 것이 캐시를 통째로 비우는 유일한 방법이다
                key.captured shouldStartWith "llm:"
                key.captured.endsWith("hash:전세") shouldBe true
            }
        }

        When("저장된 값이 없으면") {
            val (adapter, ops) = fixture()
            every { ops.get(any()) } returns null

            Then("빈 값을 돌려준다") {
                adapter.get("hash:전세").shouldBeNull()
            }
        }

        When("결과를 저장하면") {
            val (adapter, ops) = fixture()
            every { ops.set(any(), any(), any<java.time.Duration>()) } returns Unit

            adapter.put("hash:전세", "{}")

            Then("보관 기간을 함께 지정한다") {
                // 옛 키는 기간이 지나면 저절로 사라진다
                verify(exactly = 1) { ops.set(any(), "{}", any<java.time.Duration>()) }
            }
        }
    }

    Given("캐시 저장소가 죽어 있을 때") {

        When("조회하면") {
            val (adapter, ops) = fixture()
            every { ops.get(any()) } throws RuntimeException("connection refused")

            Then("예외를 밖으로 내보내지 않고 미스처럼 다룬다") {
                adapter.get("hash:전세").shouldBeNull()
            }
        }

        When("저장하면") {
            val (adapter, ops) = fixture()
            every { ops.set(any(), any(), any<java.time.Duration>()) } throws
                RuntimeException("connection refused")

            Then("예외를 밖으로 내보내지 않는다") {
                // 저장에 실패해도 분석 결과는 이미 만들어졌다. 되돌릴 이유가 없다.
                adapter.put("hash:전세", "{}")
            }
        }
    }
})
