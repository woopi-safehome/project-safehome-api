package com.woopi.safehome.domain.deed.application.usecase

import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.application.port.outbound.DeedJobCreationPort
import com.woopi.safehome.domain.deed.application.port.outbound.DeedJobExecutorPort
import com.woopi.safehome.domain.deed.application.port.outbound.DeedSsePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@Transactional(readOnly = true)
@Service
class DeedUseCaseImpl(
    private val deedJobCreationPort: DeedJobCreationPort,
    private val deedSsePort: DeedSsePort,
    private val deedJobExecutorPort: DeedJobExecutorPort,
) : DeedUseCase {

    @Transactional
    override fun analyzeDeed(command: DeedCommand.Analyze): SseEmitter {

        // 고유 Job ID 생성
        val jobId = UUID.randomUUID().toString()

        // Job 저장
        deedJobCreationPort.createJob(jobId, command.fileName, command.fileSize)

        // SSE Emitter 생성
        val emitter = deedSsePort.createEmitter(jobId)

        // SSE 연결 성공 이벤트 전송
        deedSsePort.notifyPending(jobId, "분석 작업이 시작되었습니다.")

        // 비동기 실행
        deedJobExecutorPort.execute(jobId, command.file)

        // emitter 반환 (연결 유지)
        return emitter
    }
}
