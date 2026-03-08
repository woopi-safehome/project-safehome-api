package com.woopi.safehome.domain.analysisjob.application.port.outbound

import com.woopi.safehome.domain.analysisjob.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus

interface AnalysisJobPersistencePort {

    /**
     * 분석 작업 저장
     */
    fun create(analysisJobCreate: AnalysisJob.Create): AnalysisJob.Data

    /**
     * Job 상태 / 단계 / 설명 업데이트
     * (IN_PROGRESS, FAILED 등)
     */
    fun update(command: AnalysisJob.Update): AnalysisJob.Data

    /**
     * JobId 기준 조회 (SSE 재연결, 결과 조회용)
     */
    fun findByJobId(jobId: String): AnalysisJob.Data?

    /**
     * JobId 기준 상태 + 단계 변경 (편의 메서드)
     */
    fun updateStatus(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep? = null,
        description: String? = null
    ): AnalysisJob.Data

    /**
     * Job 완료 처리 (결과 저장 포함)
     */
    fun complete(
        jobId: String,
        result: String
    ): AnalysisJob.Data

}
