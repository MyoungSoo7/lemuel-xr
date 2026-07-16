package github.lms.lemuel.xr.common.jwt

import io.jsonwebtoken.JwtException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtIssuerVerifierTest {

    private val props = JwtProperties(
        "test-secret-must-be-at-least-32-bytes-long-for-hs256",
        30,
        "lemuel-xr",
    )
    private val issuer = JwtIssuer(props)
    private val verifier = JwtVerifier(props)

    @Test
    fun `issue then verify round trip`() {
        val userId = UUID.randomUUID()
        val issued = issuer.issue(userId, "quest3")
        assertThat(issued.token).isNotBlank()

        val principal = verifier.verify(issued.token)
        assertThat(principal.userId).isEqualTo(userId)
        assertThat(principal.deviceType).isEqualTo("quest3")
    }

    @Test
    fun `잘못된 시크릿은 verify 실패`() {
        val issued = issuer.issue(UUID.randomUUID(), "vision_pro")
        val other = JwtVerifier(
            JwtProperties("different-secret-also-32-bytes-or-more-please-yes", 30, "lemuel-xr"),
        )
        assertThatThrownBy { other.verify(issued.token) }
            .isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `deviceType null 도 발급 가능`() {
        val issued = issuer.issue(UUID.randomUUID(), null)
        assertThat(verifier.verify(issued.token).deviceType).isEqualTo("unknown")
    }
}
