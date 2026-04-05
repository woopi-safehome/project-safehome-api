package com.woopi.safehome.domain.deed.application.port.outbound

interface DeedJobCreationPort {
    fun createJob(jobId: String, fileName: String, fileSize: Long)
}
