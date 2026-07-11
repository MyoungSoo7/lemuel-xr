package github.lms.lemuel.xr.common.jwt;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtIssuer(JwtProperties props) {
        this.props = props;
        // HS256 — 비밀키는 최소 32바이트 (256bit). application.yml dev 값은 충분히 김.
        this.key = new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 게스트 사용자 JWT 발급. */
    public IssuedToken issue(UUID userId, String deviceType) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.expiryDays(), ChronoUnit.DAYS);
        String token = Jwts.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of("device", deviceType == null ? "unknown" : deviceType))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, exp);
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
