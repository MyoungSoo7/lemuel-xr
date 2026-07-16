package github.lms.lemuel.xr.common.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

/** application.yml 의 security.jwt.*. */
@ConfigurationProperties(prefix = "security.jwt")
data class JwtProperties(
    val secret: String,
    val expiryDays: Int,
    val issuer: String,
)
