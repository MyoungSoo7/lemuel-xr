package github.lms.lemuel.xr.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(JwtProperties props) {
        this.key = new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 검증 통과 시 {@link Principal} 반환, 실패 시 {@link JwtException}. */
    public Principal verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String device = claims.get("device", String.class);
        return new Principal(userId, device);
    }

    public record Principal(UUID userId, String deviceType) {}
}
