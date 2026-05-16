package com.woopi.safehome.domain.auth.adapter.inbound.web.dto

object AuthResponse {

    data class Login(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val isNewUser: Boolean,
    )

    data class Token(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
    )
}
