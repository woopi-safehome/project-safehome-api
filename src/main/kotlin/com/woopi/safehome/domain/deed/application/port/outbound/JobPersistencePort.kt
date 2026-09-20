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

    /**
     * 그 시각 이후 이 사용자가 시작한 분석 수 — 하루 제한을 세는 데 쓴다.
     *
     * **실패한 분석은 세지 않는다.** 서버나 상류 장애로 실패한 것까지 세면
     * 사용자가 자기 탓이 아닌 이유로 하루치를 잃는다. 진행 중인 것은 센다 —
     * 세지 않으면 끝나기 전에 여러 건을 한꺼번에 밀어 넣을 수 있다.
     */
    fun countStartedSince(userId: Long, since: java.time.LocalDateTime): Long

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
