package com.woopi.safehome.global.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Duration
import java.util.UUID

/**
 * 익명 쿠키를 읽어 식별자를 넘긴다. 없거나 형식이 틀리면 새로 만들어 쿠키로 내려준다.
 *
 * 형식을 검사하는 이유: 이 값은 그대로 저장소 키와 DB 에 들어간다. 임의 문자열을 받으면
 * 아무 길이의 키를 만들어 한도 저장소를 채울 수 있다.
 */
@Component
class AnonymousIdArgumentResolver : HandlerMethodArgumentResolver {

    companion object {
        const val COOKIE_NAME = "safehome_anon"
        private val MAX_AGE = Duration.ofDays(365)
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(AnonymousId::class.java) &&
            parameter.parameterType == String::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): String {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
        request?.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value
            ?.takeIf(::isValid)
            ?.let { return it }

        val issued = UUID.randomUUID().toString()
        webRequest.getNativeResponse(HttpServletResponse::class.java)?.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from(COOKIE_NAME, issued)
                // 스크립트가 읽을 필요가 없다. 읽을 수 있으면 페이지에 끼어든 스크립트가 훔쳐 간다.
                .httpOnly(true)
                // 브라우저는 localhost 를 안전한 출처로 보므로 로컬 http 에서도 받아들인다.
                .secure(true)
                // 웹 프론트가 같은 도메인에서 api 로 넘겨주는 구성을 전제한다. 다른 도메인이면 쿠키가 실리지 않는다.
                .sameSite("Lax")
                .path("/api")
                .maxAge(MAX_AGE)
                .build()
                .toString(),
        )
        return issued
    }

    private fun isValid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}
