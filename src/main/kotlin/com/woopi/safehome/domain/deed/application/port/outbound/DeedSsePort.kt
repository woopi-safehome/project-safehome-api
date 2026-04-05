package com.woopi.safehome.domain.deed.application.port.outbound

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface DeedSsePort {
    fun createEmitter(jobId: String): SseEmitter
    fun notifyPending(jobId: String, message: String)
}
