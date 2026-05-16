package com.woopi.safehome.domain.deed.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmAnalysisPort
import com.woopi.safehome.domain.deed.application.port.outbound.LlmCachePort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfParserPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfValidationPort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.domain.exception.InvalidPdfException
import com.woopi.safehome.domain.deed.domain.model.DeedSections
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest

@Service
class AnalysisAsyncProcessor(
    private val sseNotifierPort: SseNotifierPort,
    private val jobPersistencePort: JobPersistencePort,
    private val pdfValidationPort: PdfValidationPort,
    private val pdfParserPort: PdfParserPort,
    private val llmAnalysisPort: LlmAnalysisPort,
    private val llmCachePort: LlmCachePort,
    private val objectMapper: ObjectMapper,
) : AnalysisExecutorPort {

    private val log = LoggerFactory.getLogger(AnalysisAsyncProcessor::class.java)

    @Async
    override fun execute(jobId: String, file: MultipartFile, leaseType: String?) {

        fun updateAndNotify(status: JobStatus, step: AnalysisStep, message: String) {
            jobPersistencePort.updateStatus(jobId, status, step, message)
            sseNotifierPort.notifyStep(jobId, status, step, message)
        }

        // 1. PDF 파싱
        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "첨부된 파일을 분석중이에요")

        val sections = try {
            val content = file.bytes
            pdfValidationPort.validate(content, file.contentType)
            pdfParserPort.parse(content)
        } catch (e: InvalidPdfException) {
            updateAndNotify(JobStatus.FAILED, AnalysisStep.PDF_PARSING, e.message ?: "PDF 검증 실패")
            return
        }

        log.info("[PDF_PARSING] jobId={}, sections={}", jobId, sections)

        // 2. LLM 분석 (캐시 우선)
        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.LLM_ANALYSIS, "AI가 등본을 분석중이에요")

        val sectionHash = sections.toSha256Hash()

        val analysisResult = try {
            val cached = llmCachePort.get(sectionHash)
            if (cached != null) {
                log.info("[LLM_ANALYSIS] 캐시 히트. jobId={}, hash={}", jobId, sectionHash)
                cached
            } else {
                val result = llmAnalysisPort.analyze(sections, leaseType)
                llmCachePort.put(sectionHash, result)
                result
            }
        } catch (e: Exception) {
            log.error("[LLM_ANALYSIS] 분석 실패. jobId={}", jobId, e)
            Sentry.withScope { scope ->
                scope.setTag("jobId", jobId)
                scope.setTag("step", AnalysisStep.LLM_ANALYSIS.name)
                Sentry.captureException(e)
            }
            updateAndNotify(JobStatus.FAILED, AnalysisStep.LLM_ANALYSIS, "AI 분석 중 오류가 발생했습니다")
            return
        }

        // 3. 후처리
        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.POST_PROCESSING, "분석한 내용을 정리중이에요")

        try {
            val (safetyLevel, address) = extractSummaryFields(analysisResult)
            jobPersistencePort.complete(jobId, analysisResult, safetyLevel, address)
            sseNotifierPort.notifyStep(jobId, JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "완료 됐습니다!")
        } catch (e: Exception) {
            log.error("[POST_PROCESSING] 완료 처리 실패. jobId={}", jobId, e)
            Sentry.withScope { scope ->
                scope.setTag("jobId", jobId)
                scope.setTag("step", AnalysisStep.POST_PROCESSING.name)
                Sentry.captureException(e)
            }
            updateAndNotify(JobStatus.FAILED, AnalysisStep.POST_PROCESSING, "결과 저장 중 오류가 발생했습니다")
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

    private fun DeedSections.toSha256Hash(): String {
        val content = sections.entries
            .sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k:${v.joinToString("\n")}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
