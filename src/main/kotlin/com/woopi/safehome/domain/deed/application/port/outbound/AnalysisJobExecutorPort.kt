package com.woopi.safehome.domain.deed.application.port.outbound

import org.springframework.web.multipart.MultipartFile

interface AnalysisJobExecutorPort {
    fun execute(jobId: String, file: MultipartFile)
}