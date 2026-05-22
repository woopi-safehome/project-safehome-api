package com.woopi.safehome.domain.deed.adapter.inbound.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import org.springframework.web.multipart.MultipartFile

object DeedRequest {

    data class Upload(
        @Schema(description = "등기부등본 PDF 파일")
        @NotNull
        val file: MultipartFile,
        @Schema(description = "임대차 유형 (전세 / 월세)", allowableValues = ["전세", "월세"])
        val leaseType: String? = null,
    )
}