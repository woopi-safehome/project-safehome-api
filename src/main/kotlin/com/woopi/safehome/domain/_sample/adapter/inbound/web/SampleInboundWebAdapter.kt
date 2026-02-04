package com.woopi.safehome.domain._sample.adapter.inbound.web

import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleRequest
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleResponse
import com.woopi.safehome.domain._sample.application.port.inbound.SampleUseCase
import com.woopi.safehome.domain.analysisjob.adapter.outbound.sse.AnalysisSseNotifier
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "샘플 API", description = "샘플 API")
@RestController
@RequestMapping("/api/sample")
class SampleInboundWebAdapter(
    private val sampleUseCase: SampleUseCase,
    private val analysisSseNotifier: AnalysisSseNotifier
) {

    @Operation(summary = "샘플 리스트 조회", description = "샘플 리스트 조회")
    @GetMapping
    fun getSampleList(request: SampleRequest.Search): ApiResponse<List<SampleResponse>> {
        return ApiResponse.success(sampleUseCase.getSampleList(request))
    }

    @Operation(summary = "샘플 상세 조회", description = "샘플 상세 조회")
    @GetMapping("/details/{id}")
    fun getSampleDetails(@PathVariable id: Long): ApiResponse<SampleResponse> {
        return ApiResponse.success(sampleUseCase.getSampleDetails(id))
    }

}