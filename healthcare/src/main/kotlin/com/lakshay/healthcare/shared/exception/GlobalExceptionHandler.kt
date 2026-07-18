package com.lakshay.healthcare.shared.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime
import com.lakshay.healthcare.shared.exception.InvalidSsnException
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.exception.DuplicateResourceException
import com.lakshay.healthcare.shared.exception.UnauthorizedException

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(InvalidSsnException::class)
    fun handleInvalidSsn(ex: InvalidSsnException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid SSN")

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicate(ex: DuplicateResourceException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.CONFLICT, ex.message ?: "Resource already exists")

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized")

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.FORBIDDEN, ex.message ?: "Forbidden")

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(AccountLockedException::class)
    fun handleAccountLocked(ex: AccountLockedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(
                ErrorResponse(
                    status = HttpStatus.TOO_MANY_REQUESTS.value(),
                    error = HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
                    message = ex.message ?: "Too many failed attempts",
                    timestamp = LocalDateTime.now().toString()
                )
            )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File too large")

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception for request {}", request.getDescription(false), ex)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }

    private fun errorResponse(status: HttpStatus, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                timestamp = LocalDateTime.now().toString()
            )
        )
}

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: String
)
