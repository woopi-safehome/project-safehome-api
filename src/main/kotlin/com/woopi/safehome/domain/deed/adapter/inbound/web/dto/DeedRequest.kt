package com.woopi.safehome.domain.deed.adapter.inbound.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

object DeedRequest {

    data class Analyze(
        @Schema(description = "등기부등본 PDF 파일")
        @NotNull
        val file: MultipartFile
    )
}