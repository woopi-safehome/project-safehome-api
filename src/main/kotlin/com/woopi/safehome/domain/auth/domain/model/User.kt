package com.woopi.safehome.domain.auth.domain.model

object User {
    data class Create(
        val kakaoId: Long,
        val nickname: String,
        val profileImageUrl: String? = null,
    )

    data class Data(
        val id: Long,
        val kakaoId: Long,
        val nickname: String,
        val profileImageUrl: String? = null,
    )
}

data class KakaoUserInfo(
    val kakaoId: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val isNewUser: Boolean,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
