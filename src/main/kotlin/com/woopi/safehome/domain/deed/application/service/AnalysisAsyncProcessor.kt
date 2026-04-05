package com.woopi.safehome.domain.deed.application.service

import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfParserPort
import com.woopi.safehome.domain.deed.application.port.outbound.PdfValidationPort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.domain.exception.InvalidPdfException
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class AnalysisAsyncProcessor(
    private val sseNotifierPort: SseNotifierPort,
    private val jobPersistencePort: JobPersistencePort,
    private val pdfValidationPort: PdfValidationPort,
    private val pdfParserPort: PdfParserPort,
) : AnalysisExecutorPort {

    @Async
    override fun execute(jobId: String, file: MultipartFile) {

        fun updateAndNotify(status: JobStatus, step: AnalysisStep, message: String) {

            jobPersistencePort.updateStatus(jobId, status, step, message)
            sseNotifierPort.notifyStep(jobId, status, step, message)
        }

        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.PDF_PARSING, "첨부된 파일을 분석중이에요")

        val sections = try {
            val content = file.bytes
            pdfValidationPort.validate(content, file.contentType)
            pdfParserPort.parse(content)
        } catch (e: InvalidPdfException) {
            updateAndNotify(JobStatus.FAILED, AnalysisStep.PDF_PARSING, e.message ?: "PDF 검증 실패")
            return
        }

        Thread.sleep(1000)

        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.LLM_ANALYSIS, "AI가 등본을 분석중이에요")

        Thread.sleep(1000)

        updateAndNotify(JobStatus.IN_PROGRESS, AnalysisStep.POST_PROCESSING, "분석한 내용을 정리중이에요")

        jobPersistencePort.complete(jobId, sections.toString())

        sseNotifierPort.notifyStep(jobId, JobStatus.COMPLETED, AnalysisStep.POST_PROCESSING, "완료 됐습니다!")
    }
}
