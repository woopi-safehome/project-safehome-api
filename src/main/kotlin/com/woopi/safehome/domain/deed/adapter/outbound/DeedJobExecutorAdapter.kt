package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.analysisjob.application.port.inbound.AnalysisJobExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.DeedJobExecutorPort
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class DeedJobExecutorAdapter(
    private val analysisJobExecutorPort: AnalysisJobExecutorPort
) : DeedJobExecutorPort {

    override fun execute(jobId: String, file: MultipartFile) =
        analysisJobExecutorPort.execute(jobId, file)
}
