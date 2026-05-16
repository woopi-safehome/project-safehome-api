package com.woopi.safehome.global.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date

@Component
class JwtProvider(
    @Value("\${safehome.jwt.secret}") secret: String,
    @Value("\${safehome.jwt.access-token-expiry}") val accessTokenExpiry: Long,
    @Value("\${safehome.jwt.refresh-token-expiry}") val refreshTokenExpiry: Long,
) {
    private val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun generateAccessToken(userId: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessTokenExpiry)))
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(userId: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(refreshTokenExpiry)))
            .signWith(secretKey)
            .compact()
    }

    fun validateAccessToken(token: String): Long? = runCatching {
        val claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
        if (claims["type"] != "access") return null
        claims.subject.toLong()
    }.getOrNull()

    fun validateRefreshToken(token: String): Long? = runCatching {
        val claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
        if (claims["type"] != "refresh") return null
        claims.subject.toLong()
    }.getOrNull()
}
