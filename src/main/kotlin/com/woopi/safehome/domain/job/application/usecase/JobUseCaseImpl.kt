package com.woopi.safehome.domain.job.application.usecase

import com.woopi.safehome.domain.job.application.port.inbound.JobUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional(readOnly = true)
@Service
class JobUseCaseImpl : JobUseCase {

    override fun createJobId(): String {
        return UUID.randomUUID().toString()
    }

}