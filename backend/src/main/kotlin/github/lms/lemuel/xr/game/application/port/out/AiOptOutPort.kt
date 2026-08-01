package github.lms.lemuel.xr.game.application.port.out

import java.util.UUID

/**
 * AI opt-out 조회 아웃바운드 포트 — R5("정적 큐레이션이 기본 경로, LLM 은 명시적 opt-in") 의 런타임 스위치.
 *
 * game 컨텍스트는 auth 의 어댑터(JPA·엔티티)를 알지 않는다. 이 포트만 알고,
 * 구현([github.lms.lemuel.xr.game.adapter.out.optout.AuthAiOptOutAdapter])이
 * auth 의 [github.lms.lemuel.xr.auth.application.port.out.UserPort] 로 위임한다.
 * ([CrisisContactPort] 와 같은 모양이다.)
 *
 * **계약**: 절대 예외를 던지지 않는다. 조회가 어떤 이유로든 실패하면 `true`(=opt-out 으로 간주)로 떨어진다.
 *
 * 기본값을 `true` 로 두는 이유 — 두 실패 모드의 값이 대칭이 아니다.
 * - `false` 로 떨어지면: AI 를 *끈* 사용자에게 LLM 응답이 간다. 사용자가 명시적으로 거부한 처리가 일어나고,
 *   되돌릴 수 없으며, 그 사실이 로그에도 "정상 동작"으로 남는다.
 * - `true` 로 떨어지면: AI 를 *켠* 사용자가 그 회차에 정적 큐레이션 문장을 받는다. 이 경로는 항상 존재하고
 *   (`realtimeLlm=true` Scene 도 yml 정적 텍스트를 갖는 것이 전제다) 이미 LLM 사이드카 장애 시의 정상 경로다.
 *
 * 즉 fail-closed 의 비용은 "이미 지원되는 열화 경로", fail-open 의 비용은 "안전 제약 위반"이다.
 */
fun interface AiOptOutPort {

    /**
     * @param userId 게임 세션의 소유자. 게스트/미상 세션에서 null 일 수 있다.
     * @return true 면 이 사용자에게 LLM 을 호출하면 안 된다.
     */
    fun isOptedOut(userId: UUID?): Boolean
}
