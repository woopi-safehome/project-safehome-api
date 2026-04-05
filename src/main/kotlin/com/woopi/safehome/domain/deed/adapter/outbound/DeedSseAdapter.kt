package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisSseNotifierPort
import com.woopi.safehome.domain.deed.application.port.outbound.DeedSsePort
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class DeedSseAdapter(
    private val analysisSseNotifierPort: AnalysisSseNotifierPort
) : DeedSsePort {

    override fun createEmitter(jobId: String): SseEmitter =
        analysisSseNotifierPort.createEmitter(jobId)

    override fun notifyPending(jobId: String, message: String) =
        analysisSseNotifierPort.notifyStep(jobId, JobStatus.PENDING, null, message)
}
