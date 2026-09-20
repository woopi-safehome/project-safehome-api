package com.woopi.safehome.domain.deed.adapter.outbound.persistence.jpa

import com.woopi.safehome.global.enums.JobStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AnalysisJobRepository : JpaRepository<AnalysisJobEntity, Long> {
    fun findByJobId(jobId: String): AnalysisJobEntity?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<AnalysisJobEntity>

    /** 하루 사용량. 제외할 상태를 인자로 받는다 — 무엇을 세지 않을지는 부르는 쪽이 정한다. */
    fun countByUserIdAndCreatedAtGreaterThanEqualAndStatusNot(
        userId: Long,
        createdAt: LocalDateTime,
        status: JobStatus,
    ): Long
}
