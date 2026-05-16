package com.woopi.safehome.domain.auth.adapter.inbound.web

import com.woopi.safehome.domain.auth.adapter.inbound.web.dto.AuthRequest
import com.woopi.safehome.domain.auth.adapter.inbound.web.dto.AuthResponse
import com.woopi.safehome.domain.auth.application.port.inbound.AuthUseCase
import com.woopi.safehome.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증 API", description = "카카오 로그인, 토큰 갱신")
@RestController
@RequestMapping("/api/auth")
class AuthInboundWebAdapter(
    private val authUseCase: AuthUseCase,
) {

    @Operation(summary = "카카오 로그인", description = "카카오 액세스 토큰으로 로그인 또는 회원가입 처리")
    @PostMapping("/kakao")
    fun kakaoLogin(@RequestBody request: AuthRequest.KakaoLogin): ApiResponse<AuthResponse.Login> {
        val result = authUseCase.kakaoLogin(request.kakaoAccessToken)
        return ApiResponse.success(
            AuthResponse.Login(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresIn = result.expiresIn,
                isNewUser = result.isNewUser,
            )
        )
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰 발급")
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: AuthRequest.Refresh): ApiResponse<AuthResponse.Token> {
        val result = authUseCase.refresh(request.refreshToken)
        return ApiResponse.success(
            AuthResponse.Token(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresIn = result.expiresIn,
            )
        )
    }
}
