package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisJobPersistencePort
import com.woopi.safehome.domain.analysisjob.model.AnalysisJob
import com.woopi.safehome.domain.deed.application.port.outbound.DeedJobCreationPort
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.stereotype.Component

@Component
class DeedJobCreationAdapter(
    private val analysisJobPersistencePort: AnalysisJobPersistencePort
) : DeedJobCreationPort {

    override fun createJob(jobId: String, fileName: String, fileSize: Long) {
        analysisJobPersistencePort.create(
            AnalysisJob.Create(
                jobId = jobId,
                fileName = fileName,
                fileSize = fileSize,
                status = JobStatus.PENDING,
            )
        )
    }
}
