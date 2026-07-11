package github.lms.lemuel.xr.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtIssuerVerifierTest {

    private final JwtProperties props = new JwtProperties(
            "test-secret-must-be-at-least-32-bytes-long-for-hs256",
            30, "lemuel-xr"
    );
    private final JwtIssuer issuer = new JwtIssuer(props);
    private final JwtVerifier verifier = new JwtVerifier(props);

    @Test
    void issue_then_verify_round_trip() {
        UUID userId = UUID.randomUUID();
        var issued = issuer.issue(userId, "quest3");
        assertThat(issued.token()).isNotBlank();

        var principal = verifier.verify(issued.token());
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.deviceType()).isEqualTo("quest3");
    }

    @Test
    void 잘못된_시크릿은_verify_실패() {
        var issued = issuer.issue(UUID.randomUUID(), "vision_pro");
        JwtVerifier other = new JwtVerifier(new JwtProperties(
                "different-secret-also-32-bytes-or-more-please-yes", 30, "lemuel-xr"));
        assertThatThrownBy(() -> other.verify(issued.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void deviceType_null_도_발급_가능() {
        var issued = issuer.issue(UUID.randomUUID(), null);
        assertThat(verifier.verify(issued.token()).deviceType()).isEqualTo("unknown");
    }
}
