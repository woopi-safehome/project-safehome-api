package com.woopi.safehome.domain.analysisjob.application.service

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisJobPersistencePort
import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisSseNotifierPort
import com.woopi.safehome.domain.analysisjob.application.port.outbound.PdfAnalysisException
import com.woopi.safehome.domain.analysisjob.application.port.outbound.PdfAnalysisPort
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class AnalysisAsyncProcessor(
    private val analysisProgressPort: AnalysisSseNotifierPort,
    private val analysisJobPersistencePort: AnalysisJobPersistencePort,
    private val pdfAnalysisPort: PdfAnalysisPort
) {

    @Async
    fun process(jobId: String, file: MultipartFile) {

        fun updateAndNotify(
            status: JobStatus,
            step: AnalysisStep,
            message: String
        ) {
            analysisJobPersistencePort.updateStatus(
                jobId = jobId,
                status = status,
                step = step,
                description = message
            )
            analysisProgressPort.notifyStep(
                jobId,
                status,
                step,
                message
            )
        }

        updateAndNotify(
            JobStatus.IN_PROGRESS,
            AnalysisStep.PDF_PARSING,
            "첨부된 파일을 분석중이에요"
        )

        val pdfContent = try {
            pdfAnalysisPort.process(file)
        } catch (e: PdfAnalysisException) {
            updateAndNotify(
                JobStatus.FAILED,
                AnalysisStep.PDF_PARSING,
                e.message ?: "PDF 검증 실패"
            )
            return
        }

        Thread.sleep(1000)

        updateAndNotify(
            JobStatus.IN_PROGRESS,
            AnalysisStep.LLM_ANALYSIS,
            "AI가 등본을 분석중이에요"
        )

        Thread.sleep(1000)

        updateAndNotify(
            JobStatus.IN_PROGRESS,
            AnalysisStep.POST_PROCESSING,
            "분석한 내용을 정리중이에요"
        )

        analysisJobPersistencePort.complete(
            jobId = jobId,
            result = "분석 성공"
        )

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.COMPLETED,
            AnalysisStep.POST_PROCESSING,
            "완료 됐습니다!"
        )
    }
}