package github.lms.lemuel.xr.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 security.jwt.*. */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        int expiryDays,
        String issuer
) {}
