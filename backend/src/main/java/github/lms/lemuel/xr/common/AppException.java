package github.lms.lemuel.xr.common;

import lombok.Getter;

/**
 * Domain·application 계층에서 던지는 비즈니스 예외.
 * ProblemDetailMapper 가 RFC 7807 응답으로 변환.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode code;

    public AppException(ErrorCode code) {
        super(code.defaultTitle());
        this.code = code;
    }

    public AppException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public AppException(ErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }
}
