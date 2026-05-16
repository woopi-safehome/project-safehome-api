package com.woopi.safehome.domain.deed.application.port.outbound

import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface JobPersistencePort {

    fun create(create: AnalysisJob.Create): AnalysisJob.Data

    fun findByJobId(jobId: String): AnalysisJob.Data?

    fun findByUserId(userId: Long, pageable: Pageable): Page<AnalysisJob.Data>

    fun updateStatus(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep? = null,
        description: String? = null
    ): AnalysisJob.Data

    fun complete(
        jobId: String,
        result: String,
        safetyLevel: SafetyLevel? = null,
        address: String? = null,
    ): AnalysisJob.Data
}
