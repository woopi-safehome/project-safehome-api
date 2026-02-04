package com.woopi.safehome.domain.analysisjob.adapter.outbound

import com.woopi.safehome.domain.analysisjob.application.service.AnalysisAsyncProcessor
import com.woopi.safehome.domain.deed.application.port.outbound.AnalysisJobExecutorPort
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class AnalysisJobAsyncExecutorAdapter(
    private val analysisAsyncProcessor: AnalysisAsyncProcessor
) : AnalysisJobExecutorPort {

    override fun execute(jobId: String, file: MultipartFile) {
        analysisAsyncProcessor.process(jobId, file)
    }
}