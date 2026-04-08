package com.woopi.safehome.domain.deed.adapter.inbound.web.dto

import com.fasterxml.jackson.annotation.JsonRawValue
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import io.swagger.v3.oas.annotations.media.Schema

object DeedResponse {

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
}
