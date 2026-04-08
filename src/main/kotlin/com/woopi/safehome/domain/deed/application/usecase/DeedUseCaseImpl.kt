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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    override fun analyzeDeed(command: DeedCommand.Analyze): SseEmitter {

        val jobId = UUID.randomUUID().toString()

        jobPersistencePort.create(
            AnalysisJob.Create(
                jobId = jobId,
                fileName = command.fileName,
                fileSize = command.fileSize,
                status = JobStatus.PENDING,
            )
        )

        val emitter = sseNotifierPort.createEmitter(jobId)

        sseNotifierPort.notifyStep(jobId, JobStatus.PENDING, null, "분석 작업이 시작되었습니다.")

        analysisExecutorPort.execute(jobId, command.file)

        return emitter
    }

    override fun getJob(jobId: String): AnalysisJob.Data {
        return jobPersistencePort.findByJobId(jobId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)
    }
}
