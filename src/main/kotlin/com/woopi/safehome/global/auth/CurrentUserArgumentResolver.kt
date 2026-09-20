package com.woopi.safehome.global.auth

import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import com.woopi.safehome.global.jwt.JwtProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver(
    private val jwtProvider: JwtProvider,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) && isUserId(parameter)

    /**
     * `Long` 과 `Long?` 을 모두 받는다.
     *
     * **둘의 뜻이 다르다.** `Long` 은 인증을 요구하고, `Long?` 은 **비회원도 통과시킨다.**
     * 코틀린의 `Long` 은 원시형으로, `Long?` 은 박싱된 형으로 컴파일되므로 여기서 구분할 수 있다.
     */
    private fun isUserId(parameter: MethodParameter): Boolean =
        parameter.parameterType == Long::class.java ||
                parameter.parameterType == Long::class.javaObjectType

    /** 비워 둘 수 있는 인자인가. 그렇다면 인증이 없어도 예외 대신 null 을 준다. */
    private fun isOptional(parameter: MethodParameter): Boolean =
        parameter.parameterType == Long::class.javaObjectType

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long? {
        val optional = isOptional(parameter)

        fun absent(): Long? =
            if (optional) null else throw BusinessException(ErrorCode.UNAUTHORIZED)

        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: return absent()

        val token = request.getHeader("Authorization")
            ?.removePrefix("Bearer ")
            ?: return absent()

        // 토큰이 왔는데 유효하지 않으면, 비회원으로 받아 주지 않고 거절한다.
        // 만료된 토큰을 든 사용자가 조용히 남의 작업을 만드는 것을 막는다.
        return jwtProvider.validateAccessToken(token)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}
