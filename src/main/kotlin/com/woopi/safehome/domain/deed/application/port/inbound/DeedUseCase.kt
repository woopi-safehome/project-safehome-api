package com.woopi.safehome.domain.deed.application.port.inbound

import com.woopi.safehome.domain.deed.application.port.inbound.command.DeedCommand
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface DeedUseCase {

    /**
     * 분석 작업
     */
    fun analyzeDeed(command: DeedCommand.Analyze): SseEmitter

}