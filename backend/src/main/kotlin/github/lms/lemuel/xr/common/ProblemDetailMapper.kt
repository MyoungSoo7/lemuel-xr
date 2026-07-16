package github.lms.lemuel.xr.common

import jakarta.validation.ConstraintViolationException
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

/**
 * 전역 예외 → RFC 7807 ProblemDetail 매퍼.
 * BACKEND-API-DESIGN.md §12 의 ErrorCode 모두 처리.
 */
@RestControllerAdvice
class ProblemDetailMapper {

    @ExceptionHandler(AppException::class)
    fun handleApp(ex: AppException, req: WebRequest): ResponseEntity<ProblemDetail> {
        val code = ex.code
        val pd = ProblemDetail.forStatusAndDetail(code.httpStatus, ex.message)
        pd.type = URI.create(TYPE_BASE + code.name)
        pd.title = code.defaultTitle
        pd.setProperty("code", code.name)
        // Spring 4 ProblemDetail 에 path 자동 추가 X — 수동.
        pd.setProperty("instance", req.getDescription(false).replaceFirst("^uri=".toRegex(), ""))
        return ResponseEntity.status(code.httpStatus).body(pd)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, req: WebRequest): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(
            ErrorCode.E_VALIDATION.httpStatus,
            ex.bindingResult.allErrors.stream()
                .findFirst()
                .map { it.defaultMessage }
                .orElse(ErrorCode.E_VALIDATION.defaultTitle),
        )
        pd.type = URI.create(TYPE_BASE + ErrorCode.E_VALIDATION.name)
        pd.title = ErrorCode.E_VALIDATION.defaultTitle
        pd.setProperty("code", ErrorCode.E_VALIDATION.name)
        return ResponseEntity.status(ErrorCode.E_VALIDATION.httpStatus).body(pd)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraint(ex: ConstraintViolationException, req: WebRequest): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(ErrorCode.E_VALIDATION.httpStatus, ex.message)
        pd.type = URI.create(TYPE_BASE + ErrorCode.E_VALIDATION.name)
        pd.title = ErrorCode.E_VALIDATION.defaultTitle
        pd.setProperty("code", ErrorCode.E_VALIDATION.name)
        return ResponseEntity.status(ErrorCode.E_VALIDATION.httpStatus).body(pd)
    }

    @ExceptionHandler(Exception::class)
    fun handleFallback(ex: Exception, req: WebRequest): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception", ex)
        val pd = ProblemDetail.forStatusAndDetail(
            ErrorCode.E_INTERNAL.httpStatus,
            "Server error — see logs",
        )
        pd.type = URI.create(TYPE_BASE + ErrorCode.E_INTERNAL.name)
        pd.title = ErrorCode.E_INTERNAL.defaultTitle
        pd.setProperty("code", ErrorCode.E_INTERNAL.name)
        return ResponseEntity.status(ErrorCode.E_INTERNAL.httpStatus).body(pd)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProblemDetailMapper::class.java)
        private const val TYPE_BASE = "https://lemuel.co.kr/errors/"
    }
}
