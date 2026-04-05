package com.woopi.safehome.domain.deed.domain.model

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus

object AnalysisJob {

    data class Create(
        val jobId: String,
        val fileName: String,
        val fileSize: Long,
        val status: JobStatus,
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
        val step: AnalysisStep? = null,
        val result: String? = null,
        val description: String? = null
    )
}
