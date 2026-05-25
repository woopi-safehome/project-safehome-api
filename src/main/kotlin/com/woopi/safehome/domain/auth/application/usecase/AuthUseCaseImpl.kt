package com.woopi.safehome.domain.auth.application.usecase

import com.woopi.safehome.domain.auth.application.port.inbound.AuthUseCase
import com.woopi.safehome.domain.auth.application.port.outbound.KakaoApiPort
import com.woopi.safehome.domain.auth.application.port.outbound.UserDevicePersistencePort
import com.woopi.safehome.domain.auth.application.port.outbound.UserPersistencePort
import com.woopi.safehome.domain.auth.domain.model.AuthResult
import com.woopi.safehome.domain.auth.domain.model.TokenPair
import com.woopi.safehome.domain.auth.domain.model.User
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import com.woopi.safehome.global.jwt.JwtProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class AuthUseCaseImpl(
    private val userPersistencePort: UserPersistencePort,
    private val userDevicePersistencePort: UserDevicePersistencePort,
    private val kakaoApiPort: KakaoApiPort,
    private val jwtProvider: JwtProvider,
) : AuthUseCase {

    @Transactional
    override fun kakaoLogin(kakaoAccessToken: String): AuthResult {
        val kakaoUserInfo = kakaoApiPort.getUserInfo(kakaoAccessToken)

        val existingUser = userPersistencePort.findByKakaoId(kakaoUserInfo.kakaoId)

        val (user, isNewUser) = if (existingUser != null) {
            existingUser to false
        } else {
            userPersistencePort.save(
                User.Create(
                    kakaoId = kakaoUserInfo.kakaoId,
                    nickname = kakaoUserInfo.nickname,
                    profileImageUrl = kakaoUserInfo.profileImageUrl,
                )
            ) to true
        }

        return AuthResult(
            accessToken = jwtProvider.generateAccessToken(user.id),
            refreshToken = jwtProvider.generateRefreshToken(user.id),
            expiresIn = jwtProvider.accessTokenExpiry,
            isNewUser = isNewUser,
        )
    }

    override fun refresh(refreshToken: String): TokenPair {
        val userId = jwtProvider.validateRefreshToken(refreshToken)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return TokenPair(
            accessToken = jwtProvider.generateAccessToken(userId),
            refreshToken = jwtProvider.generateRefreshToken(userId),
            expiresIn = jwtProvider.accessTokenExpiry,
        )
    }

    @Transactional
    override fun withdraw(userId: Long) {
        val user = userPersistencePort.findById(userId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)
        kakaoApiPort.unlinkUser(user.kakaoId)
        userDevicePersistencePort.deleteByUserId(userId)
        userPersistencePort.deleteById(userId)
    }

    @Transactional
    override fun registerDevice(userId: Long, fcmToken: String) {
        userDevicePersistencePort.upsert(userId, fcmToken)
    }
}
