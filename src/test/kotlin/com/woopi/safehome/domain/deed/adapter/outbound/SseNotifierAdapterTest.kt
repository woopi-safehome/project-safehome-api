package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 진행 알림의 종료와 실패 처리를 고정한다.
 *
 * 분석이 끝나면 서버가 스트림을 닫는다. 클라이언트가 먼저 끊는 것은 정상이라
 * 에러로 다루지 않는다 - 에러로 처리하면 로그가 오염된다.
 *
 * 실제 emitter 는 서블릿 밖에서 완료 콜백이 뜨지 않아 검증할 수 없다.
 * 그래서 어댑터가 emitter 를 만드는 자리를 대역으로 바꿔 호출을 직접 본다.
 *
 * 배경: domain/deed/README.md 의 "실패를 어떻게 다루는가"
 */
class SseNotifierAdapterTest : BehaviorSpec({

    fun fixture(): Pair<SseNotifierAdapter, SseEmitter> {
        val emitter = mockk<SseEmitter>(relaxed = true)
        val adapter = SseNotifierAdapter(
            timeoutMillis = 1_000L,
            emitterFactory = { emitter },
        )
        return adapter to emitter
    }

    Given("구독 중인 클라이언트") {

        When("진행 중 단계를 알리면") {
            val (adapter, emitter) = fixture()
            adapter.createEmitter("job-1")
            adapter.notifyStep("job-1", JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "파싱 중")

            Then("이벤트를 보내고 스트림은 열어 둔다") {
                verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 0) { emitter.complete() }
            }
        }

        When("완료를 알리면") {
            val (adapter, emitter) = fixture()
            adapter.createEmitter("job-1")
            adapter.notifyStep("job-1", JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "완료")

            Then("보낸 뒤 스트림을 닫는다") {
                verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 1) { emitter.complete() }
            }
        }

        When("실패를 알리면") {
            val (adapter, emitter) = fixture()
            adapter.createEmitter("job-1")
            adapter.notifyStep("job-1", JobStatus.FAILED, AnalysisStep.LLM_ANALYSIS, "실패")

            Then("역시 스트림을 닫는다") {
                verify(exactly = 1) { emitter.complete() }
            }
        }

        When("닫은 뒤에 또 알리면") {
            val (adapter, emitter) = fixture()
            adapter.createEmitter("job-1")
            adapter.notifyStep("job-1", JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "완료")
            adapter.notifyStep("job-1", JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "또 완료")

            Then("이미 닫힌 스트림에는 보내지 않는다") {
                verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }
    }

    Given("구독자가 없는 작업") {
        When("알리면") {
            val (adapter, _) = fixture()
            Then("아무 일도 일어나지 않는다") {
                // 구독 전에 끝난 분석이 있을 수 있다. 예외를 던지면 비동기 처리가 멈춘다.
                adapter.notifyStep("없는-작업", JobStatus.COMPLETED, null, "완료")
            }
        }
    }

    Given("클라이언트가 먼저 끊은 경우") {
        When("알리려 하면") {
            val emitter = mockk<SseEmitter>(relaxed = true)
            every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws
                AsyncRequestNotUsableException("client gone")
            val adapter = SseNotifierAdapter(1_000L) { emitter }
            adapter.createEmitter("job-1")

            adapter.notifyStep("job-1", JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "파싱 중")

            Then("정상으로 보고 오류로 닫지 않는다") {
                // 클라이언트가 먼저 끊는 경우가 흔하다. 에러로 처리하면 로그가 오염된다.
                verify(exactly = 0) { emitter.completeWithError(any()) }
            }
        }
    }

    Given("보내는 도중 뜻밖의 오류가 난 경우") {
        When("알리려 하면") {
            val emitter = mockk<SseEmitter>(relaxed = true)
            every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws
                RuntimeException("broken pipe")
            val adapter = SseNotifierAdapter(1_000L) { emitter }
            adapter.createEmitter("job-1")

            adapter.notifyStep("job-1", JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "파싱 중")

            Then("오류로 스트림을 닫는다") {
                verify(exactly = 1) { emitter.completeWithError(any()) }
            }
        }
    }
})
