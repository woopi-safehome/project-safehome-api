package com.woopi.safehome.domain.auth.adapter.outbound

import com.woopi.safehome.domain.auth.application.port.outbound.KakaoApiPort
import com.woopi.safehome.domain.auth.domain.model.KakaoUserInfo
import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Component
class KakaoApiAdapter(
    @Value("\${safehome.kakao.admin-key}") private val adminKey: String,
) : KakaoApiPort {

    private val restTemplate = RestTemplate()

    override fun getUserInfo(accessToken: String): KakaoUserInfo {
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $accessToken")
        }

        val response = try {
            restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                HttpEntity<Unit>(headers),
                Map::class.java,
            )
        } catch (e: HttpClientErrorException) {
            throw BusinessException(ErrorCode.KAKAO_API_ERROR)
        }

        val body = response.body ?: throw BusinessException(ErrorCode.KAKAO_API_ERROR)
        val kakaoId = (body["id"] as? Number)?.toLong()
            ?: throw BusinessException(ErrorCode.KAKAO_API_ERROR)

        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = body["kakao_account"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val profile = kakaoAccount?.get("profile") as? Map<String, Any>

        return KakaoUserInfo(
            kakaoId = kakaoId,
            nickname = profile?.get("nickname") as? String ?: "사용자",
            profileImageUrl = profile?.get("profile_image_url") as? String,
        )
    }

    override fun unlinkUser(kakaoId: Long) {
        val headers = HttpHeaders().apply {
            set("Authorization", "KakaoAK $adminKey")
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("target_id_type", "user_id")
            add("target_id", kakaoId.toString())
        }

        try {
            restTemplate.postForEntity(
                "https://kapi.kakao.com/v1/user/unlink",
                HttpEntity(body, headers),
                String::class.java,
            )
        } catch (e: HttpClientErrorException) {
            throw BusinessException(ErrorCode.KAKAO_API_ERROR)
        }
    }
}
