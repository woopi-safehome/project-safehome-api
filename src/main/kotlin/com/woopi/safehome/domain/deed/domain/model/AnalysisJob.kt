package com.woopi.safehome.domain.deed.domain.model

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
import java.time.LocalDateTime

object AnalysisJob {

    data class Create(
        val jobId: String,
        val fileName: String,
        val fileSize: Long,
        val status: JobStatus,
        val userId: Long? = null,
        val step: AnalysisStep? = null,
        val result: String? = null,
        val description: String? = null
    )

    data class Data(
        val id: Long,
        val jobId: String,
        val fileName: String,
        val fileSize: Long,
        val status: JobStatus,
        val userId: Long? = null,
        val step: AnalysisStep? = null,
        val result: String? = null,
        val description: String? = null,
        val safetyLevel: SafetyLevel? = null,
        val address: String? = null,
        val createdAt: LocalDateTime? = null,
    )
}
