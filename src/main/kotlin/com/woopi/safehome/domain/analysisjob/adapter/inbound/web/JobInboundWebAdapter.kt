package com.woopi.safehome.domain.analysisjob.adapter.inbound.web

import com.woopi.safehome.domain.analysisjob.application.port.inbound.JobUseCase
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Job API", description = "Job API")
@RestController
@RequestMapping("/api/job")
class JobInboundWebAdapter(
    private val jobUseCase: JobUseCase
) {

    @Operation(summary = "job id 생성 후 조회", description = "job id 생성 후 조회")
    @PostMapping("/id")
    fun createJobId(): ApiResponse<String> {
        return ApiResponse.success(jobUseCase.createJobId())
    }

}