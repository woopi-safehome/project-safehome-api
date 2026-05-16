package com.woopi.safehome.domain.auth.adapter.inbound.web.dto

object AuthRequest {

    data class KakaoLogin(
        val kakaoAccessToken: String,
    )

    data class Refresh(
        val refreshToken: String,
    )
}
