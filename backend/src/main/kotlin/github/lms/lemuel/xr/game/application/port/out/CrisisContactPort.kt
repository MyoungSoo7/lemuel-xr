package github.lms.lemuel.xr.game.application.port.out

/**
 * 위기 연락처 아웃바운드 포트 — 시나리오 payload 의 `{{crisis_resources.default}}` 치환 소스.
 *
 * game 컨텍스트는 safety 의 어댑터(JPA·엔티티)를 알지 않는다. 이 포트만 알고,
 * 구현([github.lms.lemuel.xr.game.adapter.out.crisis.SafetyCrisisContactAdapter])이
 * safety 의 유스케이스로 위임한다.
 *
 * **계약**: 절대 예외를 던지지 않고 절대 빈 문자열을 주지 않는다.
 * 위기 카피에 원시 토큰이 새는 것보다는 설정된 대체 상수라도 내보내는 편이 안전하다.
 */
fun interface CrisisContactPort {

    fun defaultContact(region: String, locale: String): String
}
