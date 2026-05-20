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