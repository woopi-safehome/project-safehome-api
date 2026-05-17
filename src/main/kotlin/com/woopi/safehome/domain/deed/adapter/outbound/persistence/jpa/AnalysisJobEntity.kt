package com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa

import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import com.woopi.safehome.global.enums.SafetyLevel
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

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "step")
    @Enumerated(EnumType.STRING)
    var step: AnalysisStep? = null,

    @Column(name = "result", columnDefinition = "TEXT")
    var result: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "safety_level")
    @Enumerated(EnumType.STRING)
    var safetyLevel: SafetyLevel? = null,

    @Column(name = "address")
    var address: String? = null,

    @Column(name = "lease_type")
    var leaseType: String? = null,

) : BaseEntity()
