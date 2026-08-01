package github.lms.lemuel.xr.game.adapter.out.crisis

import github.lms.lemuel.xr.game.application.port.out.CrisisContactPort
import github.lms.lemuel.xr.safety.application.GetCrisisResourcesUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * [CrisisContactPort] 구현 — safety 컨텍스트의 [GetCrisisResourcesUseCase] 로 위임한다.
 *
 * game.application 은 safety 를 직접 알지 않고 이 어댑터만 경계를 넘는다.
 * 진실의 출처는 `crisis_resources` 테이블의 region/locale 별 *우선순위 1위 활성 전화* 자원이다
 * (2026-08-02 기준 KR/ko-KR = 109, V20260802031449 마이그레이션).
 *
 * **Fail-safe**: 조회가 실패하거나 결과가 비면 `safety.crisis.default-contact` 설정값으로 떨어진다.
 * 어떤 실패 경로에서도 호출자에게 null·빈 문자열이 가지 않는다 — 위기 카피의 자리표시자가
 * 사용자에게 그대로 노출되는 것이 이 클래스가 막아야 할 최악의 결과다.
 */
@Component
class SafetyCrisisContactAdapter(
    private val crisisResources: GetCrisisResourcesUseCase,
    @Value("\${safety.crisis.default-contact:109}") private val fallbackContact: String,
) : CrisisContactPort {

    init {
        // [CrisisContactPort] 계약("절대 빈 문자열을 주지 않는다")의 마지막 근거가 이 값이다.
        // 빈 값으로 설정되면 DB 조회가 실패한 순간 위기 문구가 "." 한 글자로 렌더된다 —
        // 조용히 무너지느니 부팅을 실패시킨다.
        require(fallbackContact.isNotBlank()) {
            "safety.crisis.default-contact 가 비어 있다 — 위기 연락처 fail-safe 대체값은 비울 수 없다."
        }
    }

    override fun defaultContact(region: String, locale: String): String {
        val looked = try {
            crisisResources.execute(region, locale)
                .firstOrNull { PHONE.equals(it.contactType, ignoreCase = true) }
                ?.contactValue
        } catch (e: Exception) {
            log.warn("위기 연락처 조회 실패 — 설정 대체값 사용 ({}/{}): {}", region, locale, e.message)
            null
        }
        if (looked.isNullOrBlank()) {
            return fallbackContact
        }
        return looked
    }

    companion object {
        private const val PHONE = "phone"
        private val log = LoggerFactory.getLogger(SafetyCrisisContactAdapter::class.java)
    }
}
