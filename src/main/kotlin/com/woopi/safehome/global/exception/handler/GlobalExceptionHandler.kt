package com.woopi.safehome.global.exception.handler

import com.woopi.safehome.global.exception.BusinessException
import com.woopi.safehome.global.exception.ErrorCode
import com.woopi.safehome.global.response.ApiResponse
import io.sentry.Sentry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.beans.TypeMismatchException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
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

    /**
     * 프레임워크가 요청 단계에서 던지는 예외 — 없는 경로, 맞지 않는 메서드·형식, 읽을 수 없는 본문 따위.
     * **컨트롤러에 닿기 전에 터지므로 여기서만 다룰 수 있다.**
     *
     * 따로 받지 않으면 아래 포괄 핸들러로 떨어져 **요청한 쪽의 잘못이 500 으로 나간다.**
     * API 가 인터넷에 열려 있어 경로를 훑는 요청이 끊임없이 오고, 그것이 전부 서버 오류로 Sentry 에 쌓인다.
     *
     * 4xx 를 들고 온 예외만 받는다. 5xx(비동기 타임아웃 등)는 실제로 서버 쪽 사정이므로 포괄 핸들러가 그대로 다룬다.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing?>>? {
        clientErrorCodeOf(ex)?.let { errorCode ->
            if (isSseContext(request, response)) return null
            return createErrorResponse(errorCode)
        }

        Sentry.captureException(ex)
        if (isSseContext(request, response)) return null
        // 예외 메시지를 싣지 않는다. 내부 구현(클래스·경로·쿼리)이 그대로 밖으로 나가고, 원인은 Sentry 가 갖는다.
        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    /**
     * 요청한 쪽의 잘못이면 그에 맞는 에러 코드, 아니면 null.
     *
     * 상태별로 하나씩 고르고, 계약 표에 없는 4xx 는 400 으로 묶는다 — 응답의 상태와 코드가
     * 계약 표와 어긋나지 않게 하기 위해서다. 상태를 새로 살리려면 코드와 계약 표를 함께 늘린다.
     */
    private fun clientErrorCodeOf(ex: Exception): ErrorCode? {
        // 이 둘은 ErrorResponse 가 아니지만 요청 본문·인자를 읽지 못한 것이라 400 이다.
        if (ex is HttpMessageNotReadableException || ex is TypeMismatchException) return ErrorCode.VALIDATION_FAILED
        if (ex !is ErrorResponse || !ex.statusCode.is4xxClientError) return null
        return when (ex.statusCode.value()) {
            404 -> ErrorCode.NOT_FOUND
            405 -> ErrorCode.METHOD_NOT_ALLOWED
            415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            else -> ErrorCode.VALIDATION_FAILED
        }
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