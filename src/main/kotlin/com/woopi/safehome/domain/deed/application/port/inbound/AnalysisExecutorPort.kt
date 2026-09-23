package com.woopi.safehome.domain.deed.application.port.inbound

interface AnalysisExecutorPort {
    /**
     * @param clientAddress 비회원 사용량을 센 주소. 회원이면 쓰지 않는다 — 실패했을 때 되돌릴 대상을 알기 위한 것이다.
     */
    fun execute(
        jobId: String,
        fileBytes: ByteArray,
        contentType: String?,
        leaseType: String? = null,
        userId: Long? = null,
        clientAddress: String? = null,
    )
}
