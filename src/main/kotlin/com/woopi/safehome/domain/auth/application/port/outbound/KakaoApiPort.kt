package com.woopi.safehome.domain.auth.application.port.outbound

import com.woopi.safehome.domain.auth.domain.model.KakaoUserInfo

interface KakaoApiPort {

    fun getUserInfo(accessToken: String): KakaoUserInfo

    fun unlinkUser(kakaoId: Long)
}
