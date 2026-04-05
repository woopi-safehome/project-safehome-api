package com.woopi.safehome.domain.deed.application.port.outbound

import org.springframework.web.multipart.MultipartFile

interface DeedJobExecutorPort {
    fun execute(jobId: String, file: MultipartFile)
}
