package github.lms.lemuel.xr.game.adapter.out.optout

import github.lms.lemuel.xr.auth.application.port.out.UserPort
import github.lms.lemuel.xr.game.application.port.out.AiOptOutPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [AiOptOutPort] 구현 — auth 컨텍스트의 [UserPort] 로 위임한다.
 *
 * game.application 은 auth 를 직접 알지 않고 이 어댑터만 경계를 넘는다
 * ([github.lms.lemuel.xr.game.adapter.out.crisis.SafetyCrisisContactAdapter] 와 같은 모양).
 * 진실의 출처는 `users.ai_opt_out` 컬럼이다 (V20260521224700 마이그레이션 — "true 면 모든 LLM 호출 skip,
 * 큐레이션 콘텐츠로 fallback").
 *
 * **Fail-closed**: 조회 실패·사용자 없음·컬럼 null·userId null 은 전부 `true`(opt-out)로 처리한다.
 * 근거는 [AiOptOutPort] 계약 주석 참조 — 요약하면, 잘못 끄면 정적 문장이 나가고(이미 지원되는 열화 경로),
 * 잘못 켜면 사용자가 명시적으로 거부한 LLM 호출이 일어난다(안전 제약 위반).
 *
 * `userId == null`(게스트/미상 세션)을 opt-out 으로 보는 이유: R5 는 LLM 을 *명시적 opt-in* 으로 규정한다.
 * 조회할 사용자 레코드가 없다는 것은 "동의 기록이 없다"는 뜻이지 "동의했다"는 뜻이 아니다.
 * 실제 게스트는 `IssueGuestTokenUseCase` 가 `ai_opt_out=false` 인 users 행을 만들어 주므로
 * 이 경로로 오지 않는다 (`game_sessions.user_id` 는 NOT NULL 이다). 즉 이 분기는 이례 상태 전용이고,
 * 정상 게스트의 LLM 이용을 막지 않는다.
 */
@Component
class AuthAiOptOutAdapter(
    private val users: UserPort,
) : AiOptOutPort {

    override fun isOptedOut(userId: UUID?): Boolean {
        if (userId == null) {
            log.debug("게임 세션에 userId 가 없다 — AI opt-out 으로 간주하고 LLM 을 호출하지 않는다.")
            return true
        }
        return try {
            users.findById(userId)
                .map { it.aiOptOut ?: true }
                .orElse(true)
        } catch (e: Exception) {
            log.warn("AI opt-out 조회 실패 — opt-out 으로 간주한다. userId={} cause={}", userId, e.message)
            true
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthAiOptOutAdapter::class.java)
    }
}
