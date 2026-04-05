package com.woopi.safehome.domain.deed.application.port.outbound

import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus

interface JobPersistencePort {

    fun create(create: AnalysisJob.Create): AnalysisJob.Data

    fun updateStatus(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep? = null,
        description: String? = null
    ): AnalysisJob.Data

    fun complete(jobId: String, result: String): AnalysisJob.Data
}
