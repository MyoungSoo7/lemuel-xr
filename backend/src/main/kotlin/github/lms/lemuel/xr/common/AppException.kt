package github.lms.lemuel.xr.common

/**
 * Domain·application 계층에서 던지는 비즈니스 예외.
 * ProblemDetailMapper 가 RFC 7807 응답으로 변환.
 */
class AppException : RuntimeException {

    val code: ErrorCode

    constructor(code: ErrorCode) : super(code.defaultTitle) {
        this.code = code
    }

    constructor(code: ErrorCode, detail: String?) : super(detail) {
        this.code = code
    }

    constructor(code: ErrorCode, detail: String?, cause: Throwable?) : super(detail, cause) {
        this.code = code
    }
}
