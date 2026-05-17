package com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa

import com.woopi.safehome.domain.deed.domain.model.AnalysisJob

object AnalysisJobEntityMapper {

    fun toModel(entity: AnalysisJobEntity): AnalysisJob.Data {
        return AnalysisJob.Data(
            id = entity.id!!,
            jobId = entity.jobId,
            fileName = entity.fileName,
            fileSize = entity.fileSize,
            status = entity.status,
            userId = entity.userId,
            step = entity.step,
            result = entity.result,
            description = entity.description,
            safetyLevel = entity.safetyLevel,
            address = entity.address,
            createdAt = entity.createdAt,
            leaseType = entity.leaseType,
        )
    }

    fun toEntity(create: AnalysisJob.Create): AnalysisJobEntity {
        return AnalysisJobEntity(
            jobId = create.jobId,
            fileName = create.fileName,
            fileSize = create.fileSize,
            status = create.status,
            userId = create.userId,
            step = create.step,
            result = create.result,
            description = create.description,
            leaseType = create.leaseType,
        )
    }
}
