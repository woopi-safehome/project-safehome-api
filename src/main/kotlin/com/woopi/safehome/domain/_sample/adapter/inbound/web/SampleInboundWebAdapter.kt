package com.woopi.safehome.domain._sample.adapter.inbound.web

import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleRequest
import com.woopi.safehome.domain._sample.adapter.inbound.web.dto.SampleResponse
import com.woopi.safehome.domain._sample.application.port.inbound.SampleUseCase
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "샘플 API", description = "샘플 API")
@RestController
@RequestMapping("/api/sample")
class SampleInboundWebAdapter(
    private val sampleUseCase: SampleUseCase,
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

    @Operation(summary = "PDF 파싱 테스트", description = "PDF 파싱 테스트")
    @PostMapping(
        "/pdf/parse",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun parsingPdfSample(@RequestBody file: MultipartFile): ApiResponse<String> {
        sampleUseCase.parsePdfSample(file)
        return ApiResponse.success("PDF 파싱 테스트가 완료되었습니다.")
    }

}