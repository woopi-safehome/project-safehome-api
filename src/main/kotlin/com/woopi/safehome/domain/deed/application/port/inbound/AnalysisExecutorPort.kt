package com.woopi.safehome.domain.deed.application.port.inbound

interface AnalysisExecutorPort {
    fun execute(jobId: String, fileBytes: ByteArray, contentType: String?, leaseType: String? = null, userId: Long? = null)
}
