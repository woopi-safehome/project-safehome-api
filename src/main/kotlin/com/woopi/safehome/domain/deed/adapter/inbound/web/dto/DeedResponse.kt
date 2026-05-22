package com.woopi.safehome.domain.deed.adapter.inbound.web.dto

import com.fasterxml.jackson.annotation.JsonRawValue
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

object DeedResponse {

    data class UploadResult(
        @Schema(description = "생성된 분석 Job ID")
        val jobId: String,
    )

    data class JobDetail(
        @Schema(description = "Job ID")
        val jobId: String,
        @Schema(description = "파일명")
        val fileName: String,
        @Schema(description = "파일 크기 (bytes)")
        val fileSize: Long,
        @Schema(description = "분석 상태")
        val status: JobStatus,
        @Schema(description = "현재 단계")
        val step: AnalysisStep?,
        @Schema(description = "상태 메시지")
        val description: String?,
        @JsonRawValue
        @Schema(description = "분석 결과 JSON")
        val result: String?,
    ) {
        companion object {
            fun from(job: AnalysisJob.Data) = JobDetail(
                jobId = job.jobId,
                fileName = job.fileName,
                fileSize = job.fileSize,
                status = job.status,
                step = job.step,
                description = job.description,
                result = job.result,
            )
        }
    }

    data class JobSummary(
        @Schema(description = "Job ID")
        val jobId: String,
        @Schema(description = "파일명")
        val fileName: String,
        @Schema(description = "파일 크기 (bytes)")
        val fileSize: Long,
        @Schema(description = "분석 상태")
        val status: JobStatus,
        @Schema(description = "안전 등급")
        val safetyLevel: SafetyLevel?,
        @Schema(description = "부동산 주소")
        val address: String?,
        @Schema(description = "분석 시작 일시")
        val createdAt: LocalDateTime?,
        @Schema(description = "임대차 유형 (전세/월세)")
        val leaseType: String?,
    ) {
        companion object {
            fun from(job: AnalysisJob.Data) = JobSummary(
                jobId = job.jobId,
                fileName = job.fileName,
                fileSize = job.fileSize,
                status = job.status,
                safetyLevel = job.safetyLevel,
                address = job.address,
                createdAt = job.createdAt,
                leaseType = job.leaseType,
            )
        }
    }
}
