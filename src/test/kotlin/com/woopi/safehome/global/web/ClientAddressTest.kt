package com.woopi.safehome.global.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

/**
 * 비회원 사용량을 세는 단서가 되는 주소를 어떻게 정하는지 고정한다.
 *
 * 연결 주소를 그대로 쓰면 프록시 뒤에서 모든 비회원이 한 주소로 보여 첫 한 건 뒤로 전부 막힌다.
 * 반대로 전달 헤더를 무턱대고 믿으면 누구나 붙여서 주소별 제한을 비껴간다.
 *
 * 배경: domain/deed/README.md 의 "불변식"
 */
class ClientAddressTest : BehaviorSpec({

    fun request(peer: String, forwardedFor: String? = null) = MockHttpServletRequest().apply {
        remoteAddr = peer
        forwardedFor?.let { addHeader("X-Forwarded-For", it) }
    }

    Given("앞단 프록시를 거친 요청") {

        When("연결 상대가 사설망이고 전달 헤더가 있으면") {
            Then("헤더의 주소를 쓴다") {
                // 이걸 안 하면 웹 프론트를 거친 모든 사용자가 한 주소로 묶인다
                ClientAddress.of(request("10.0.0.5", "203.0.113.7")) shouldBe "203.0.113.7"
            }
        }

        When("프록시를 여러 번 거쳐 주소가 이어져 있으면") {
            Then("맨 앞의 원래 요청자를 쓴다") {
                ClientAddress.of(request("127.0.0.1", "203.0.113.7, 10.0.0.1, 10.0.0.2")) shouldBe "203.0.113.7"
            }
        }

        When("헤더가 비어 있으면") {
            Then("연결 주소로 돌아간다") {
                ClientAddress.of(request("192.168.0.9", "   ")) shouldBe "192.168.0.9"
            }
        }
    }

    Given("인터넷에서 직접 온 요청") {

        When("전달 헤더를 붙여 보내면") {
            Then("믿지 않고 연결 주소를 쓴다") {
                // 헤더는 누구나 붙일 수 있다. 믿으면 주소별 제한이 무의미해진다.
                ClientAddress.of(request("198.51.100.4", "1.1.1.1")) shouldBe "198.51.100.4"
            }
        }

        When("헤더가 없으면") {
            Then("연결 주소를 쓴다") {
                ClientAddress.of(request("198.51.100.4")) shouldBe "198.51.100.4"
            }
        }
    }
})
