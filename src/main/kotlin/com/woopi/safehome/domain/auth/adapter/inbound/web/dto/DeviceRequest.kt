package com.woopi.safehome.domain.auth.adapter.inbound.web.dto

import jakarta.validation.constraints.NotBlank

object DeviceRequest {

    data class Register(
        @field:NotBlank val fcmToken: String,
    )
}
