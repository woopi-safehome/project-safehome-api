package com.woopi.safehome.domain.deed.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmAnalysisPort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmCachePort
import com.woopi.safehome.domain.deed.application.port.outbound.NotificationPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfParserPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfValidationPort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.application.port.outbound.UserDeviceQueryPort
import com.woopi.safehome.domain.deed.domain.model.DeedSections
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException

/**
 * 분석 비동기 처리부가 작업의 끝을 반드시 남기는지 고정한다.
 *
 * 이 처리부는 요청 스레드 밖에서 돈다. 예외가 새어 나가면 아무도 받지 않고, 작업은 진행 중으로
 * 멈춘 채 구독자는 끝을 받지 못한다. 반대로 결과를 저장한 뒤의 알림 실패가 작업을 실패로
 * 뒤집어서도 안 된다.
 *
 * 배경: domain/deed/README.md 의 "실패를 어떻게 다루는가"
 */
class AnalysisAsyncProcessorTest : BehaviorSpec({

    class Ports {
        val sse = mockk<SseNotifierPort>(relaxed = true)
        val jobs = mockk<JobPersistencePort>(relaxed = true)
        val validation = mockk<PdfValidationPort>(relaxed = true)
        val parser = mockk<PdfParserPort>()
        val analysis = mockk<LlmAnalysisPort>()
        val cache = mockk<LlmCachePort>(relaxed = true)
        val devices = mockk<UserDeviceQueryPort>()
        val notification = mockk<NotificationPort>(relaxed = true)

        val processor = AnalysisAsyncProcessor(
            sse, jobs, validation, parser, analysis, cache, devices, notification, ObjectMapper(),
        )

        init {
            // 캐시가 비어 있는 경우. 느슨한 대역은 문자열에 빈 값을 돌려주므로 명시한다.
            every { cache.get(any()) } returns null
        }
    }

    Given("문서 파일 자체를 읽지 못하면") {
        val p = Ports()
        every { p.parser.parse(any()) } throws IOException("손상된 문서")

        When("분석을 실행하면") {
            p.processor.execute("job-1", byteArrayOf(1), "application/pdf", null, 7L)

            Then("작업을 실패로 기록하고 구독자에게 끝을 알린다") {
                verify { p.jobs.updateStatus("job-1", JobStatus.FAILED, AnalysisStep.PDF_PARSING, any()) }
                verify { p.sse.notifyStep("job-1", JobStatus.FAILED, AnalysisStep.PDF_PARSING, any()) }
            }
        }
    }

    Given("문서에서 등기부 섹션을 하나도 찾지 못하면") {
        // 다른 PDF 이거나 글자가 없는 스캔본이다. 분석 서버는 빈 섹션을 거절하므로 보내 봐야 "분석 오류"로만 끝난다.
        val p = Ports()
        every { p.parser.parse(any()) } returns DeedSections(emptyMap())

        When("분석을 실행하면") {
            p.processor.execute("job-4", byteArrayOf(1), "application/pdf", null, 7L)

            Then("분석 서버를 부르지 않고, 문서 단계의 실패로 원인과 해결 방법을 남긴다") {
                verify(exactly = 0) { p.analysis.analyze(any(), any()) }
                verify {
                    p.jobs.updateStatus(
                        "job-4", JobStatus.FAILED, AnalysisStep.PDF_PARSING, AnalysisAsyncProcessor.UNREADABLE_DEED,
                    )
                }
                verify {
                    p.sse.notifyStep(
                        "job-4", JobStatus.FAILED, AnalysisStep.PDF_PARSING, AnalysisAsyncProcessor.UNREADABLE_DEED,
                    )
                }
            }
        }
    }

    Given("분석 서버 호출이 실패하면") {
        val p = Ports()
        every { p.parser.parse(any()) } returns DeedSections(mapOf("갑구" to listOf("소유권보존")))
        every { p.analysis.analyze(any(), any()) } throws IllegalStateException("상류 오류")

        When("분석을 실행하면") {
            p.processor.execute("job-2", byteArrayOf(1), "application/pdf", null, 7L)

            Then("분석 단계의 실패로 기록한다") {
                verify { p.jobs.updateStatus("job-2", JobStatus.FAILED, AnalysisStep.LLM_ANALYSIS, any()) }
            }
        }
    }

    Given("결과를 저장한 뒤 알림 대상 조회가 실패하면") {
        val p = Ports()
        every { p.parser.parse(any()) } returns DeedSections(mapOf("갑구" to listOf("소유권보존")))
        every { p.analysis.analyze(any(), any()) } returns """{"safetyLevel":"SAFE","propertyInfo":{"address":"서울"}}"""
        every { p.devices.findTokensByUserId(any()) } throws IllegalStateException("조회 실패")

        When("분석을 실행하면") {
            p.processor.execute("job-3", byteArrayOf(1), "application/pdf", null, 7L)

            Then("결과는 완료로 남는다") {
                verify(exactly = 1) { p.jobs.complete("job-3", any(), SafetyLevel.SAFE, "서울") }
                verify(exactly = 0) { p.jobs.updateStatus("job-3", JobStatus.FAILED, any(), any()) }
            }
        }
    }
})
