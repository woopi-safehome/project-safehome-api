package com.woopi.safehome.domain.analysisjob.application.port.outbound

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface AnalysisSseNotifierPort {

    fun createEmitter(jobId: String): SseEmitter

    fun notifyStep(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep?,
        message: String
    )
}