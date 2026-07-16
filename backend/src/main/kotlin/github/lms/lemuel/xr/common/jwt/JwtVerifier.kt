package github.lms.lemuel.xr.common.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class JwtVerifier(props: JwtProperties) {

    private val key: SecretKey = SecretKeySpec(props.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

    /** 검증 통과 시 [Principal] 반환, 실패 시 [JwtException]. */
    fun verify(token: String): Principal {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        val userId = UUID.fromString(claims.subject)
        val device = claims.get("device", String::class.java)
        return Principal(userId, device)
    }

    data class Principal(val userId: UUID, val deviceType: String?)
}
