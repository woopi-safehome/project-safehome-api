package com.woopi.safehome.domain.analysisjob.adapter.outbound.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AnalysisJobRepository : JpaRepository<AnalysisJobEntity, Long> {
    fun findByJobId(jobId: String): AnalysisJobEntity?
}
