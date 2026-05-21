package github.lms.lemuel.xr.common;

import java.net.URI;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * 전역 예외 → RFC 7807 ProblemDetail 매퍼.
 * BACKEND-API-DESIGN.md §12 의 ErrorCode 모두 처리.
 */
@RestControllerAdvice
@Slf4j
public class ProblemDetailMapper {

    private static final String TYPE_BASE = "https://lemuel.co.kr/errors/";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleApp(AppException ex, WebRequest req) {
        ErrorCode code = ex.getCode();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.httpStatus(), ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + code.name()));
        pd.setTitle(code.defaultTitle());
        pd.setProperty("code", code.name());
        // Spring 4 ProblemDetail 에 path 자동 추가 X — 수동.
        pd.setProperty("instance", req.getDescription(false).replaceFirst("^uri=", ""));
        return ResponseEntity.status(code.httpStatus()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ErrorCode.E_VALIDATION.httpStatus(),
                ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(e -> e.getDefaultMessage())
                        .orElse(ErrorCode.E_VALIDATION.defaultTitle()));
        pd.setType(URI.create(TYPE_BASE + ErrorCode.E_VALIDATION.name()));
        pd.setTitle(ErrorCode.E_VALIDATION.defaultTitle());
        pd.setProperty("code", ErrorCode.E_VALIDATION.name());
        return ResponseEntity.status(ErrorCode.E_VALIDATION.httpStatus()).body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ErrorCode.E_VALIDATION.httpStatus(), ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ErrorCode.E_VALIDATION.name()));
        pd.setTitle(ErrorCode.E_VALIDATION.defaultTitle());
        pd.setProperty("code", ErrorCode.E_VALIDATION.name());
        return ResponseEntity.status(ErrorCode.E_VALIDATION.httpStatus()).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleFallback(Exception ex, WebRequest req) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ErrorCode.E_INTERNAL.httpStatus(),
                "Server error — see logs");
        pd.setType(URI.create(TYPE_BASE + ErrorCode.E_INTERNAL.name()));
        pd.setTitle(ErrorCode.E_INTERNAL.defaultTitle());
        pd.setProperty("code", ErrorCode.E_INTERNAL.name());
        return ResponseEntity.status(ErrorCode.E_INTERNAL.httpStatus()).body(pd);
    }
}
