package com.woopi.safehome.domain.analysisjob.application.usecase

import com.woopi.safehome.domain.analysisjob.application.port.inbound.JobUseCase
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