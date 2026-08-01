package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.CrisisContactPort
import org.springframework.stereotype.Component

/**
 * 시나리오 yml 의 위기 연락처 자리표시자를 실제 번호로 치환한다.
 *
 * 왜 토큰인가: 위기 상담 번호는 정책으로 바뀐다(2024-01-01 자살예방 상담 109 통합).
 * 번호를 6개 yml 에 하드코딩해 두면 다음 개정 때 또 낡은 번호가 위기 카피에 남는다.
 * yml 은 [TOKEN] 만 쓰고, 실제 번호는 `crisis_resources` 정본에서 런타임에 주입한다.
 *
 * 치환은 payload 의 *중첩 map/list* 까지 재귀로 훑는다 — Scene extras 는
 * `extras.crisis_reminder` 처럼 한 단계 이상 안쪽에 있다.
 *
 * **원시 토큰은 어떤 실패 경로에서도 클라이언트로 나가면 안 된다.** 연락처 조회는
 * [CrisisContactPort] 계약상 예외를 던지지 않고 설정 대체값을 보장한다.
 */
@Component
class CrisisTokenResolver(
    private val contacts: CrisisContactPort,
) {

    /** 기본 region/locale (KR/ko-KR) 로 치환. */
    fun resolve(payload: Map<String, Any?>): Map<String, Any?> =
        resolve(payload, DEFAULT_REGION, DEFAULT_LOCALE)

    @Suppress("UNCHECKED_CAST")
    fun resolve(payload: Map<String, Any?>, region: String, locale: String): Map<String, Any?> {
        // lazy — 토큰이 하나도 없으면 DB 를 건드리지 않는다.
        val contact = lazy { contacts.defaultContact(region, locale) }
        return substitute(payload, contact) as Map<String, Any?>
    }

    private fun substitute(value: Any?, contact: Lazy<String>): Any? = when (value) {
        is String -> if (value.contains(TOKEN)) value.replace(TOKEN, contact.value) else value
        is Map<*, *> -> value.entries.associate { (k, v) -> k to substitute(v, contact) }
        is List<*> -> value.map { substitute(it, contact) }
        else -> value
    }

    companion object {
        /** 시나리오 yml 에서 쓰는 자리표시자. */
        const val TOKEN = "{{crisis_resources.default}}"

        const val DEFAULT_REGION = "KR"
        const val DEFAULT_LOCALE = "ko-KR"
    }
}
