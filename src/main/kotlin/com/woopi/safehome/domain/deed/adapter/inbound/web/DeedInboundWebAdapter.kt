package com.woopi.safehome.domain.deed.adapter.inbound.web

import com.woopi.safehome.domain.deed.adapter.inbound.web.dto.DeedRequest
import com.woopi.safehome.domain.deed.application.port.inbound.DeedUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "등기부등본 API", description = "등기부등본 API")
@RestController
@RequestMapping("/api/deed")
class DeedInboundWebAdapter(
    private val deedUseCase: DeedUseCase
) {

    @Operation(
        summary = "등기부등본 분석",
        description = "등기부등본 PDF파일 업로드 후, 분석 작업을 시작하고 진행상황을 SSE로 스트리밍합니다."
    )
    @PostMapping(
        "/analyze",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun analyzeDeed(@ModelAttribute request: DeedRequest.Analyze): SseEmitter {
        return deedUseCase.analyzeDeed(request)
    }

}