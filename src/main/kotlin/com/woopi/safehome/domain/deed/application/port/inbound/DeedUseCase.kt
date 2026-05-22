package com.woopi.safehome.domain.deed.application.port.inbound

import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface DeedUseCase {

    /** PDF 업로드 → Job 생성 → 비동기 분석 시작, jobId 반환 */
    fun uploadDeed(command: DeedCommand.Upload): String

    /** jobId에 해당하는 SSE 스트림 구독 (분석 진행상황 실시간 수신) */
    fun streamJob(jobId: String, userId: Long): SseEmitter

    fun getJob(jobId: String, userId: Long): AnalysisJob.Data

    fun getMyJobs(userId: Long, pageable: Pageable): Page<AnalysisJob.Data>

}
