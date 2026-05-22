package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.application.port.inbound.AnalysisExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.JobPersistencePort
import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@Transactional(readOnly = true)
@Service
class DeedUseCaseImpl(
    private val jobPersistencePort: JobPersistencePort,
    private val sseNotifierPort: SseNotifierPort,
    private val analysisExecutorPort: AnalysisExecutorPort,
) : DeedUseCase {

    @Transactional
    override fun uploadDeed(command: DeedCommand.Upload): String {
        val jobId = UUID.randomUUID().toString()

        jobPersistencePort.create(
            AnalysisJob.Create(
                jobId = jobId,
                fileName = command.fileName,
                fileSize = command.fileSize,
                status = JobStatus.PENDING,
                userId = command.userId,
                leaseType = command.leaseType,
            )
        )

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                analysisExecutorPort.execute(jobId, command.file, command.leaseType)
            }
        })

        return jobId
    }

    override fun streamJob(jobId: String, userId: Long): SseEmitter {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        if (job.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN)

        val emitter = sseNotifierPort.createEmitter(jobId)

        // 이미 분석이 끝난 경우 — 즉시 최종 상태를 전송하고 emitter를 닫음
        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED) {
            sseNotifierPort.notifyStep(
                jobId = jobId,
                status = job.status,
                step = job.step,
                message = job.description ?: "분석이 완료되었습니다.",
            )
        }

        return emitter
    }

    override fun getJob(jobId: String, userId: Long): AnalysisJob.Data {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        if (job.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN)

        return job
    }

    override fun getMyJobs(userId: Long, pageable: Pageable): Page<AnalysisJob.Data> {
        return jobPersistencePort.findByUserId(userId, pageable)
    }
}
