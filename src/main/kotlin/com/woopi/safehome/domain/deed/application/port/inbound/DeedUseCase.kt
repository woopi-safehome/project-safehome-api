package com.woopi.safehome.domain.deed.application.port.inbound

import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import com.woopi.safehome.domain.deed.domain.model.AnalysisJob
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface DeedUseCase {

    fun analyzeDeed(command: DeedCommand.Analyze): SseEmitter

    fun getJob(jobId: String): AnalysisJob.Data

}
