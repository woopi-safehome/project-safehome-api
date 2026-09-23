package com.woopi.safehome.domain.deed.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmAnalysisPort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmCachePort
import com.woopi.safehome.domain.deed.application.port.outbound.NotificationPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfParserPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfValidationPort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.application.port.outbound.UserDeviceQueryPort
import com.woopi.safehome.domain.deed.domain.exception.InvalidPdfException
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AnalysisAsyncProcessor(
    private val sseNotifierPort: SseNotifierPort,
    private val jobPersistencePort: JobPersistencePort,
    private val pdfValidationPort: PdfValidationPort,
    private val pdfParserPort: PdfParserPort,
    private val llmAnalysisPort: LlmAnalysisPort,
    private val llmCachePort: LlmCachePort,
    private val userDeviceQueryPort: UserDeviceQueryPort,
    private val notificationPort: NotificationPort,
    private val objectMapper: ObjectMapper,
) : AnalysisExecutorPort {

    private val log = LoggerFactory.getLogger(AnalysisAsyncProcessor::class.java)

    companion object {
        /** 사용자에게 그대로 보인다. 무엇이 문제인지와 무엇을 하면 되는지를 함께 말한다. */
        const val UNREADABLE_DEED =
            "등기부등본의 내용을 읽을 수 없어요. 인터넷등기소에서 받은 PDF 원본을 올려 주세요. (사진이나 스캔한 파일은 읽을 수 없어요)"
    }

    // 요청 스레드 밖에서 돈다. 예외가 새어 나가면 아무도 받지 않고, 작업은 진행 중으로 멈춘 채
    // 구독자는 끝을 받지 못한다. 그래서 결과를 만드는 단계의 예외는 전부 실패로 기록한다.
    @Async("analysisTaskExecutor")
    override fun execute(jobId: String, fileBytes: ByteArray, contentType: String?, leaseType: String?, userId: Long?) {
        var step = AnalysisStep.PDF_PARSING

        fun updateAndNotify(status: JobStatus, next: AnalysisStep, message: String) {
            step = next
            jobPersistencePort.updateStatus(jobId, status, next, message)
            sseNotifierPort.notifyStep(jobId, status, next, message)
        }

        // 1·2. 문서 해석과 분석 (캐시 우선)
        val analysisResult = try {
            updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "첨부된 파일을 분석중이에요")

            val sections = try {
                pdfValidationPort.validate(fileBytes, contentType)
                pdfParserPort.parse(fileBytes).also {
                    // 섹션이 하나도 없으면 등기부로 읽을 수 없는 문서다 — 다른 PDF 이거나, 글자가 없는 스캔본이다.
                    // 분석 서버는 빈 섹션을 거절하므로 보내 봐야 "분석 오류"로만 끝난다. 여기서 원인을 말해 준다.
                    if (it.isEmpty()) throw InvalidPdfException(UNREADABLE_DEED)
                }
            } catch (e: InvalidPdfException) {
                updateAndNotify(JobStatus.FAILED, AnalysisStep.PDF_PARSING, e.message ?: "PDF 검증 실패")
                return
            }
            log.info("[PDF_PARSING] jobId={}, sections={}", jobId, sections)

            updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.LLM_ANALYSIS, "AI가 등본을 분석중이에요")
            val sectionHash = SectionCacheKey.of(sections, leaseType)
            val cached = llmCachePort.get(sectionHash)
            if (cached != null) {
                log.info("[LLM_ANALYSIS] 캐시 히트. jobId={}, hash={}", jobId, sectionHash)
                cached
            } else {
                llmAnalysisPort.analyze(sections, leaseType).also { llmCachePort.put(sectionHash, it) }
            }
        } catch (e: Exception) {
            fail(jobId, step, e)
            return
        }

        // 3. 결과 저장 — 결과를 만드는 일이므로 실패로 다룬다
        try {
            updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.POST_PROCESSING, "분석한 내용을 정리중이에요")
            val (safetyLevel, address) = extractSummaryFields(analysisResult)
            jobPersistencePort.complete(jobId, analysisResult, safetyLevel, address)
        } catch (e: Exception) {
            fail(jobId, AnalysisStep.POST_PROCESSING, e)
            return
        }

        // 4. 완료 알림 — 결과는 이미 저장됐다. 알리는 일이 실패해도 결과를 되돌리지 않는다
        try {
            sseNotifierPort.notifyStep(jobId, JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "완료 됐습니다!")
            if (userId != null) {
                val tokens = userDeviceQueryPort.findTokensByUserId(userId)
                if (tokens.isNotEmpty()) {
                    notificationPort.sendPush(tokens, jobId)
                }
            }
        } catch (e: Exception) {
            log.warn("[NOTIFY] 완료 알림 실패 — 결과는 저장됐다. jobId={}", jobId, e)
        }
    }

    private fun fail(jobId: String, step: AnalysisStep, e: Exception) {
        val message = when (step) {
            AnalysisStep.PDF_PARSING -> "문서를 읽지 못했습니다"
            AnalysisStep.LLM_ANALYSIS -> "AI 분석 중 오류가 발생했습니다"
            AnalysisStep.POST_PROCESSING -> "결과 저장 중 오류가 발생했습니다"
        }
        log.error("[{}] 분석 실패. jobId={}", step, jobId, e)
        Sentry.withScope { scope ->
            scope.setTag("jobId", jobId)
            scope.setTag("step", step.name)
            Sentry.captureException(e)
        }
        try {
            jobPersistencePort.updateStatus(jobId, JobStatus.FAILED, step, message)
            sseNotifierPort.notifyStep(jobId, JobStatus.FAILED, step, message)
        } catch (recordFailure: Exception) {
            log.error("[{}] 실패 기록조차 실패했다. jobId={}", step, jobId, recordFailure)
        }
    }

    private fun extractSummaryFields(resultJson: String): Pair<SafetyLevel?, String?> {
        return try {
            val node = objectMapper.readTree(resultJson)
            val safetyLevel = node.get("safetyLevel")?.asText()
                ?.let { runCatching { SafetyLevel.valueOf(it) }.getOrNull() }
            val address = node.get("propertyInfo")?.get("address")?.asText()
                ?.takeIf { it.isNotBlank() }
            safetyLevel to address
        } catch (e: Exception) {
            log.warn("[POST_PROCESSING] 요약 필드 추출 실패. 무시하고 계속합니다.", e)
            null to null
        }
    }

}
