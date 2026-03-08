package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisSseNotifierPort
import com.woopi.safehome.domain.deed.adapter.inbound.web.dto.DeedRequest
import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import com.woopi.safehome.domain.deed.application.port.outbound.AnalysisJobExecutorPort
import com.woopi.safehome.domain.analysisjob.application.port.outbound.AnalysisJobPersistencePort
import com.woopi.safehome.domain.analysisjob.model.AnalysisJob
import com.woopi.safehome.global.enums.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@Transactional(readOnly = true)
@Service
class DeedUseCaseImpl(
    private val analysisJobPersistencePort: AnalysisJobPersistencePort,
    private val analysisSseNotifierPort: AnalysisSseNotifierPort,
    private val analysisJobExecutorPort: AnalysisJobExecutorPort
) : DeedUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun analyzeDeed(request: DeedRequest.Analyze): SseEmitter {

        // 고유 Job ID 생성
        val jobId = UUID.randomUUID().toString()

        // Job 생성 (아직 아무 작업도 안 함)
        val job = AnalysisJob.Create(
            jobId = jobId,
            fileName = request.file.originalFilename ?: "unknown.pdf",
            fileSize = request.file.size,
            status = JobStatus.PENDING,
        )

        // Job 저장
        analysisJobPersistencePort.create(job)

        // SSE Emitter 생성 (예: 30분)
        val emitter = analysisSseNotifierPort.createEmitter(jobId)

        // SSE 연결 성공 이벤트 전송
        analysisSseNotifierPort.notifyStep(
            jobId = jobId,
            JobStatus.PENDING,
            null,
            "분석 작업이 시작되었습니다."
        )

        analysisJobExecutorPort.execute(jobId, request.file)


        // emitter 반환 (연결 유지)
        return emitter
    }

}