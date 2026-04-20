package com.woopi.safehome.domain.deed.application.port.inbound

import org.springframework.web.multipart.MultipartFile

interface AnalysisExecutorPort {
    fun execute(jobId: String, file: MultipartFile, leaseType: String? = null)
}
