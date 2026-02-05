package com.woopi.safehome.domain.analysisjob.application.port.outbound

import com.woopi.safehome.global.enums.AnalysisStep

interface AnalysisJobStatusPort {
    fun markFailed(jobId: String, step: AnalysisStep, reason: String)
    fun markInProgress(jobId: String, step: AnalysisStep)
    fun markCompleted(jobId: String)
}