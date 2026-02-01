package com.woopi.safehome.domain.analysisjob.application.service

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisSseNotifierPort
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AnalysisAsyncProcessor(
    private val analysisProgressPort: AnalysisSseNotifierPort
) {

    @Async
    fun process(jobId: String) {

        analysisProgressPort.notifyStep(
            jobId,
            JobStatus.IN_PROGRESS,
            AnalysisStep.PDF_PARSING,
            "첨부된 파일을 분석중이에요"
        )

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