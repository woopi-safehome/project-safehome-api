package com.woopi.safehome.domain._sample.adapter.inbound.web

import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleRequest
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleResponse
import com.woopi.safehome.domain._sample.application.port.inbound.SampleUseCase
import com.woopi.safehome.domain.analysisjob.adapter.outbound.sse.AnalysisSseNotifier
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "샘플 API", description = "샘플 API")
@RestController
@RequestMapping("/api/sample")
class SampleInboundWebAdapter (
    private val sampleUseCase: SampleUseCase,
    private val analysisSseNotifier: AnalysisSseNotifier
) {

    @Operation(summary = "샘플 리스트 조회", description = "샘플 리스트 조회")
    @GetMapping
    fun getSampleList (request: SampleRequest.Search): ApiResponse<List<SampleResponse>> {
        return ApiResponse.success(sampleUseCase.getSampleList(request))
    }

    @Operation(summary = "샘플 상세 조회", description = "샘플 상세 조회")
    @GetMapping("/details/{id}")
    fun getSampleDetails (@PathVariable id: Long): ApiResponse<SampleResponse> {
        return ApiResponse.success(sampleUseCase.getSampleDetails(id))
    }

    @Operation(summary = "웹소켓 샘플", description = "웹소켓 샘플")
    @GetMapping("/job-id")
    fun getJobId (): ApiResponse<String> {
        return ApiResponse.success(sampleUseCase.getJobId())
    }

    @Operation(summary = "샘플 분석 시작", description = "웹소켓 샘플")
    @PostMapping("/sample/analysis/start")
    fun websocketSample (): ApiResponse<String> {
        return ApiResponse.success(sampleUseCase.getJobId())
    }

    @GetMapping("/analysis/{jobId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamProgress(@PathVariable jobId: String): SseEmitter {
        val emitter = analysisSseNotifier.createEmitter(jobId)

        // SSE 연결 후 작업 시작
        sampleUseCase.sseSample(jobId)

        return emitter
    }
}