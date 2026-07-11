package github.lms.lemuel.xr.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void 모든_ErrorCode_가_제목과_status_가짐() {
        for (ErrorCode c : ErrorCode.values()) {
            assertThat(c.defaultTitle()).isNotBlank();
            assertThat(c.httpStatus()).isNotNull();
        }
    }

    @Test
    void 핵심_매핑_검증() {
        assertThat(ErrorCode.E_AUTH_REQUIRED.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.E_SESSION_INVALID.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.E_RATE_LIMITED.httpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.E_LLM_UPSTREAM_FAIL.httpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
