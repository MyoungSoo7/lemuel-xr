package github.lms.lemuel.xr.common.jwt

import io.jsonwebtoken.Jwts
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class JwtIssuer(private val props: JwtProperties) {

    // HS256 — 비밀키는 최소 32바이트 (256bit). application.yml dev 값은 충분히 김.
    private val key: SecretKey = SecretKeySpec(props.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

    /** 게스트 사용자 JWT 발급. */
    fun issue(userId: UUID, deviceType: String?): IssuedToken {
        val now = Instant.now()
        val exp = now.plus(props.expiryDays.toLong(), ChronoUnit.DAYS)
        val token = Jwts.builder()
            .issuer(props.issuer)
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claims(mapOf("device" to (deviceType ?: "unknown")))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
        return IssuedToken(token, exp)
    }

    data class IssuedToken(val token: String, val expiresAt: Instant)
}
