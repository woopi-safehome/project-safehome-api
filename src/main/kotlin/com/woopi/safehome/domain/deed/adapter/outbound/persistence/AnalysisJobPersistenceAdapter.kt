package com.woopi.safehome.domain.deed.adapter.outbound.persistence

import com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa.AnalysisJobEntityMapper
import com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa.AnalysisJobRepository
import com.woopi.safehome.domain.deed.application.port.outbound.AnalysisJobPersistencePort
import com.woopi.safehome.domain.deed.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class AnalysisJobPersistenceAdapter(
    private val analysisJobRepository: AnalysisJobRepository
) : AnalysisJobPersistencePort {

    override fun create(analysisJobCreate: AnalysisJob.Create): AnalysisJob.Data {
        return analysisJobRepository.save(
            AnalysisJobEntityMapper.toEntity(analysisJobCreate)
        ).let { AnalysisJobEntityMapper.toModel(it) }
    }

    override fun update(command: AnalysisJob.Update): AnalysisJob.Data {
        val entity = analysisJobRepository.findById(command.id)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "AnalysisJob not found: id=${command.id}") }

        entity.status = command.status
        entity.step = command.step
        entity.result = command.result
        entity.description = command.description

        return analysisJobRepository.save(entity)
            .let { AnalysisJobEntityMapper.toModel(it) }
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

    override fun complete(
        jobId: String,
        result: String
    ): AnalysisJob.Data {
        val entity = analysisJobRepository.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "AnalysisJob not found: jobId=$jobId")

        entity.status = JobStatus.COMPLETED
        entity.step = AnalysisStep.POST_PROCESSING
        entity.result = result

        return analysisJobRepository.save(entity)
            .let { AnalysisJobEntityMapper.toModel(it) }
    }

}