package com.woopi.safehome.domain.analysisjob.application.service

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisSseNotifierPort
import com.woopi.safehome.domain.deed.domain.service.PdfValidationService
import com.woopi.safehome.domain.deed.domain.service.exception.InvalidPdfException
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class AnalysisAsyncProcessor(
    private val analysisProgressPort: AnalysisSseNotifierPort,
    private val pdfValidationService: PdfValidationService,
) {

    @Async
    fun process(jobId: String, file: MultipartFile) {

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.IN_PROGRESS,
            AnalysisStep.PDF_PARSING,
            "첨부된 파일을 분석중이에요"
        )

        try {
            pdfValidationService.validate(file)
        } catch (e: InvalidPdfException) {

            analysisProgressPort.notifyStep(
                jobId,
                JobStatus.FAILED,
                AnalysisStep.PDF_PARSING,
                e.message ?: "PDF 검증 실패"
            )
            return
        }

        Thread.sleep(1000)

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.IN_PROGRESS,
            AnalysisStep.LLM_ANALYSIS,
            "AI가 등본을 분석중이에요"
        )

        Thread.sleep(1000)

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.IN_PROGRESS,
            AnalysisStep.POST_PROCESSING,
            "분석한 내용을 정리중이에요"
        )

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.COMPLETED,
            AnalysisStep.POST_PROCESSING,
            "완료 됐습니다!"
        )
    }
}