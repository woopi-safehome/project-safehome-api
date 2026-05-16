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
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                parameter.parameterType == Long::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val token = request.getHeader("Authorization")
            ?.removePrefix("Bearer ")
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return jwtProvider.validateAccessToken(token)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}
