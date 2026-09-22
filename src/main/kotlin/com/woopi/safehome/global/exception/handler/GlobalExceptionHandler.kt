package com.woopi.safehome.global.exception.handler

import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import com.woopi.safehome.global.response.ApiResponse
import io.sentry.Sentry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        if (e.errorCode.httpStatus.is5xxServerError) {
            Sentry.captureException(e)
        }
        if (isSseContext(request, response)) return null
        return createErrorResponse(e.errorCode, e.details)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        if (isSseContext(request, response)) return null
        val errorDetails = ex.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "잘못된 입력입니다.")
        }
        return createErrorResponse(ErrorCode.VALIDATION_FAILED, errorDetails)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        if (isSseContext(request, response)) return null
        val errorDetails = ex.constraintViolations.associate { violation ->
            violation.propertyPath.toString() to violation.message
        }
        return createErrorResponse(ErrorCode.CONSTRAINT_VIOLATION, errorDetails)
    }

    /**
     * 업로드 한도를 넘은 요청. **컨트롤러에 닿기 전에 터진다** — 멀티파트를 푸는 단계에서 걸리므로
     * 업로드 엔드포인트의 코드로는 이 경우를 다룰 수 없고, 여기서만 다룰 수 있다.
     *
     * 따로 받지 않으면 아래 포괄 핸들러로 떨어져 **"서버 내부 오류"** 가 나간다.
     * 사용자가 할 수 있는 일(더 작은 파일)이 있는데도 서버 잘못처럼 보이고, Sentry 에도 쌓인다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        if (isSseContext(request, response)) return null
        // 한도를 문서나 메시지에 복제하지 않는다. 설정이 원본이고 예외가 그 값을 들고 온다.
        val details = ex.maxUploadSize.takeIf { it > 0 }?.let { mapOf("maxBytes" to it) }
        return createErrorResponse(ErrorCode.FILE_TOO_LARGE, details)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        Sentry.captureException(ex)
        if (isSseContext(request, response)) return null
        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, ex.localizedMessage)
    }

    private fun isSseContext(request: HttpServletRequest, response: HttpServletResponse): Boolean =
        response.isCommitted
        || response.contentType?.contains("text/event-stream") == true
        || request.getHeader("Accept")?.contains("text/event-stream") == true

    private fun createErrorResponse(
        errorCode: ErrorCode,
        details: Any? = null
    ): ResponseEntity<ApiResponse<Nothing?>> {
        val response = ApiResponse.error(
            code = errorCode.code,
            message = errorCode.defaultMessage,
            details = details
        )
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

}