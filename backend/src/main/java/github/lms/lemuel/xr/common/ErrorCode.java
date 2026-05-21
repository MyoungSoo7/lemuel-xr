package github.lms.lemuel.xr.common;

import org.springframework.http.HttpStatus;

/** API 응답 ErrorCode — BACKEND-API-DESIGN.md §12. RFC 7807 ProblemDetail 의 code 필드. */
public enum ErrorCode {
    // 4xx
    E_VALIDATION("Invalid request payload", HttpStatus.BAD_REQUEST),
    E_DECISION_MALFORMED("Decision payload does not match scene type", HttpStatus.BAD_REQUEST),
    E_AUTH_REQUIRED("Authentication required", HttpStatus.UNAUTHORIZED),
    E_TOKEN_INVALID("JWT invalid or expired", HttpStatus.UNAUTHORIZED),
    E_INTERNAL_TOKEN_INVALID("Internal service token mismatch", HttpStatus.UNAUTHORIZED),
    E_FORBIDDEN("Not allowed", HttpStatus.FORBIDDEN),
    E_SESSION_NOT_FOUND("Game session not found", HttpStatus.NOT_FOUND),
    E_CHARACTER_UNKNOWN("Unknown character", HttpStatus.NOT_FOUND),
    E_SCRIPTURE_NOT_FOUND("Scripture passage not found for given reference/translation", HttpStatus.NOT_FOUND),
    E_CONTENT_VERSION_NOT_FOUND("Content version not found", HttpStatus.NOT_FOUND),
    E_SESSION_INVALID("Session is already completed or exited", HttpStatus.CONFLICT),
    E_SCENE_OUT_OF_ORDER("Scene id is out of expected progression", HttpStatus.CONFLICT),
    E_MODE_MISMATCH("Mode differs from session-initial mode", HttpStatus.CONFLICT),
    E_DUPLICATE_IDEMPOTENCY("Idempotency-Key already processed (replay)", HttpStatus.CONFLICT),
    E_RATE_LIMITED("Too many requests", HttpStatus.TOO_MANY_REQUESTS),

    // 5xx
    E_LLM_UPSTREAM_FAIL("AI sidecar returned error or timeout", HttpStatus.BAD_GATEWAY),
    E_TTS_UPSTREAM_FAIL("TTS sidecar returned error or timeout", HttpStatus.BAD_GATEWAY),
    E_STORAGE_UPSTREAM_FAIL("R2/object storage error", HttpStatus.BAD_GATEWAY),
    E_INTERNAL("Unexpected server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultTitle;
    private final HttpStatus httpStatus;

    ErrorCode(String defaultTitle, HttpStatus httpStatus) {
        this.defaultTitle = defaultTitle;
        this.httpStatus = httpStatus;
    }

    public String defaultTitle() { return defaultTitle; }
    public HttpStatus httpStatus() { return httpStatus; }
}
