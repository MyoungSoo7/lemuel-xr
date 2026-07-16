package github.lms.lemuel.xr.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ErrorCodeTest {

    @Test
    fun `모든 ErrorCode 가 제목과 status 가짐`() {
        for (c in ErrorCode.entries) {
            assertThat(c.defaultTitle).isNotBlank()
            assertThat(c.httpStatus).isNotNull()
        }
    }

    @Test
    fun `핵심 매핑 검증`() {
        assertThat(ErrorCode.E_AUTH_REQUIRED.httpStatus).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(ErrorCode.E_SESSION_INVALID.httpStatus).isEqualTo(HttpStatus.CONFLICT)
        assertThat(ErrorCode.E_RATE_LIMITED.httpStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(ErrorCode.E_LLM_UPSTREAM_FAIL.httpStatus).isEqualTo(HttpStatus.BAD_GATEWAY)
    }
}
