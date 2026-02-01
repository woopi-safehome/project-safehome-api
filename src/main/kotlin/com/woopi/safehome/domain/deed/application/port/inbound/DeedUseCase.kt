package com.woopi.safehome.domain.deed.application.port.inbound

import com.woopi.safehome.domain.deed.adapter.inbound.web.dto.DeedRequest
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface DeedUseCase {

    /**
     * 분석 작업
     */
    fun analyzeDeed(request: DeedRequest.Analyze): SseEmitter

}