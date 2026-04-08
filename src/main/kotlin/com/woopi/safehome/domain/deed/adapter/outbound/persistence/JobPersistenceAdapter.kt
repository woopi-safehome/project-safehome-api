package com.woopi.safehome.domain.deed.adapter.outbound.persistence

import com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa.AnalysisJobEntityMapper
import com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa.AnalysisJobRepository
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class JobPersistenceAdapter(
    private val analysisJobRepository: AnalysisJobRepository
) : JobPersistencePort {

    override fun create(create: AnalysisJob.Create): AnalysisJob.Data {
        return analysisJobRepository.save(
            AnalysisJobEntityMapper.toEntity(create)
        ).let { AnalysisJobEntityMapper.toModel(it) }
    }

    override fun findByJobId(jobId: String): AnalysisJob.Data? {
        return analysisJobRepository.findByJobId(jobId)
            ?.let { AnalysisJobEntityMapper.toModel(it) }
    }

    override fun updateStatus(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep?,
        description: String?
    ): AnalysisJob.Data {
        val entity = analysisJobRepository.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        entity.status = status
        entity.step = step
        entity.description = description

        return analysisJobRepository.save(entity)
            .let { AnalysisJobEntityMapper.toModel(it) }
    }

    override fun complete(jobId: String, result: String): AnalysisJob.Data {
        val entity = analysisJobRepository.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        entity.status = JobStatus.COMPLETED
        entity.step = AnalysisStep.POST_PROCESSING
        entity.result = result

        return analysisJobRepository.save(entity)
            .let { AnalysisJobEntityMapper.toModel(it) }
    }
}
