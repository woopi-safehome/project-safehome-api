package com.woopi.safehome.domain.deed.application.port.outbound

interface AnalysisJobExecutorPort {
    fun execute(jobId: String, file: MultipartFile)
}