package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.SseNotifierPort
import com.woopi.safehome.global.enums.AnalysisStep
import com.woopi.safehome.global.enums.JobStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class SseNotifierAdapter : SseNotifierPort {

    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    override fun createEmitter(jobId: String): SseEmitter {
        val emitter = SseEmitter(300_000L)

        emitter.onCompletion { emitters.remove(jobId) }
        emitter.onTimeout { emitters.remove(jobId) }
        emitter.onError { emitters.remove(jobId) }

        emitters[jobId] = emitter
        return emitter
    }

    override fun notifyStep(
        jobId: String,
        status: JobStatus,
        step: AnalysisStep?,
        message: String
    ) {
        val payload = mapOf(
            "jobId" to jobId,
            "status" to status.name,
            "step" to step?.name,
            "message" to message,
            "timestamp" to LocalDateTime.now().toString()
        )

        emitters[jobId]?.let { emitter ->
            try {
                emitter.send(SseEmitter.event().data(payload))
            } catch (ex: Exception) {
                emitters.remove(jobId)
                emitter.completeWithError(ex)
                return
            }

            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
                emitter.complete()
                emitters.remove(jobId)
            }
        }
    }
}
