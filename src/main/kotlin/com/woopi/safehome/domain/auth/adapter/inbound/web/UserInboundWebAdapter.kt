package com.woopi.safehome.domain.auth.adapter.inbound.web

import com.woopi.safehome.domain.auth.adapter.inbound.web.dto.DeviceRequest
import com.woopi.safehome.domain.auth.application.port.inbound.AuthUseCase
import com.woopi.safehome.global.auth.CurrentUser
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "사용자 API", description = "사용자 관련 API")
@RestController
@RequestMapping("/api/users")
class UserInboundWebAdapter(
    private val authUseCase: AuthUseCase,
) {

    @Operation(summary = "회원 탈퇴", description = "카카오 연결 해제 및 계정 삭제")
    @DeleteMapping("/me")
    fun withdraw(@CurrentUser userId: Long): ApiResponse<Unit> {
        authUseCase.withdraw(userId)
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "FCM 디바이스 토큰 등록", description = "앱 시작 시 FCM 토큰을 등록합니다. 동일 토큰 재등록 시 upsert 처리됩니다.")
    @PostMapping("/devices")
    fun registerDevice(
        @CurrentUser userId: Long,
        @RequestBody @Valid request: DeviceRequest.Register,
    ): ApiResponse<Unit> {
        authUseCase.registerDevice(userId, request.fcmToken)
        return ApiResponse.success(Unit)
    }
}
