package com.woopi.safehome.domain.auth.application.port.inbound

import com.woopi.safehome.domain.auth.domain.model.AuthResult
import com.woopi.safehome.domain.auth.domain.model.TokenPair

interface AuthUseCase {

    fun kakaoLogin(kakaoAccessToken: String): AuthResult

    fun refresh(refreshToken: String): TokenPair

    fun withdraw(userId: Long)
}
