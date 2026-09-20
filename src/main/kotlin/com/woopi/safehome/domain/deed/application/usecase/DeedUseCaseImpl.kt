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

        val fileBytes = command.file.bytes
        val contentType = command.file.contentType

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                analysisExecutorPort.execute(jobId, fileBytes, contentType, command.leaseType, command.userId)
            }
        })

        return jobId
    }

    /**
     * 이 작업을 볼 수 있는지 확인한다.
     *
     * **주인이 없는 작업은 jobId 를 아는 사람이 볼 수 있다.** 비회원 분석이 그렇다 —
     * 식별할 것이 없으므로 jobId 자체가 열쇠 역할을 한다. 그래서 jobId 는 추측할 수 없어야 한다.
     * 주인이 있는 작업은 종전대로 본인만 볼 수 있다. 비회원이 남의 작업을 여는 길은 열리지 않는다.
     */
    private fun assertReadable(job: AnalysisJob.Data, userId: Long?) {
        if (job.userId == null) return
        if (job.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN)
    }

    override fun streamJob(jobId: String, userId: Long?): SseEmitter {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        assertReadable(job, userId)

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

    override fun getJob(jobId: String, userId: Long?): AnalysisJob.Data {
        val job = jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        assertReadable(job, userId)

        return job
    }

    override fun getMyJobs(userId: Long, pageable: Pageable): Page<AnalysisJob.Data> {
        return jobPersistencePort.findByUserId(userId, pageable)
    }
}
