package com.woopi.safehome.domain.analysisjob.adapter.outbound.persistence.jpa

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.`object`.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "analysis_jobs")
class AnalysisJobEntity(

    @Column(name = "job_id")
    var jobId: String,

    @Column(name = "file_name")
    var fileName: String,

    @Column(name = "file_size")
    var fileSize: Long,

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    var status: JobStatus,

    @Column(name = "step")
    @Enumerated(EnumType.STRING)
    var step: AnalysisStep? = null,

    @Column(name = "result")
    var result: String? = null,

    @Column(name = "description")
    var description: String? = null,

    ) : BaseEntity() {

}
