package com.woopi.safehome.domain.analysisjob.application.port.inbound

import org.springframework.web.multipart.MultipartFile

interface AnalysisJobExecutorPort {
    fun execute(jobId: String, file: MultipartFile)
}
