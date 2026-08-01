package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.game.application.port.out.AiOptOutPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import github.lms.lemuel.xr.safety.application.ForbiddenTokenSanitizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 직전 결정에 대한 응답 텍스트를 *어떤 전략으로 만들지* 결정하는 전략 컴포넌트.
 *
 * - `Scene.realtimeLlm=true` **그리고** 사용자가 AI opt-out 하지 않았을 때 → 실시간 LLM 호출 (실패 시 정적 fallback)
 * - 그 외 → yml 의 monologues/outcomes/reactions 정적 lookup
 *
 * `DecideSceneUseCase` 의 god `execute()` 에서 응답 해석 책임을 분리한 것.
 * LLM 클라이언트([GenerateLlmResponseUseCase])는 ai 컨텍스트의 협력자로 주입한다.
 *
 * **R5 안전 제약이 실제로 걸리는 곳이 여기다** — "정적 큐레이션이 기본 경로, LLM 은 명시적 opt-in".
 * 두 게이트를 모두 이 클래스가 진다.
 * 1. [AiOptOutPort] — `users.ai_opt_out=true` 면 realtime Scene 이라도 LLM 을 호출하지 않는다.
 * 2. [ForbiddenTokenSanitizer] — 정적 텍스트도 LLM 출력과 동일한 금지 토큰 검사를 통과한다.
 *
 * 단 **`responseText` 는 이 문장들의 출구 중 하나일 뿐이다.** 같은 `monologues`/`outcomes`/`reactions`
 * 맵이 [ScenePayloadAssembler] 를 통해 `scenePayload` 로도 나간다. 그래서 게이트는 양쪽에 있고,
 * 대체 문구는 [ForbiddenTokenSanitizer] 한 곳에서만 온다.
 *
 * safety 컨텍스트의 [ForbiddenTokenSanitizer] 직접 주입은 `CompleteGameSessionUseCase` 가
 * `CrisisKeywordScanner`·`RecordSafetyAlertUseCase` 를 직접 참조하는 것과 같은 관행이다.
 * 반면 사용자 조회는 auth 의 영속 세부를 끌어오므로 [AiOptOutPort] 뒤로 격리했다
 * ([github.lms.lemuel.xr.game.application.port.out.CrisisContactPort] 와 같은 모양).
 */
@Component
class ResponseResolver(
    private val llm: GenerateLlmResponseUseCase,
    private val keyExtractor: DecisionKeyExtractor,
    private val aiOptOut: AiOptOutPort,
    private val forbiddenTokens: ForbiddenTokenSanitizer,
) {

    /** Phase 2-B realtime LLM 우선, 실패/정적 Scene 은 yml fallback. */
    fun resolve(
        character: Character,
        scene: Scenario.Scene,
        decision: Map<String, Any?>?,
        session: GameSession,
    ): String? {
        if (scene.realtimeLlm != true) {
            return matchResponseText(scene, decision)
        }

        // R5 — 정적 큐레이션이 기본 경로고 LLM 은 명시적 opt-in 이다. `users.ai_opt_out=true` 면
        // Scene 이 realtime 이든 아니든 LLM 을 *부르지 않는다* (V20260521224700: "true 면 모든 LLM 호출 skip,
        // 큐레이션 콘텐츠로 fallback"). 호출해 놓고 결과를 버리는 것도, 예외를 잡아 fallback 하는 것도 아니다 —
        // 사이드카에 프롬프트(=사용자의 결정 이력)가 나가는 것 자체가 사용자가 거부한 처리다.
        if (aiOptOut.isOptedOut(session.userId)) {
            log.debug("AI opt-out 사용자 — LLM 을 호출하지 않고 정적 경로로 간다. scene={}", scene.id)
            return matchResponseText(scene, decision)
        }

        // promptKey: joseph.s4.reaction (Scene + decision key 는 variables 로)
        val decisionKey = keyExtractor.extract(decision)
        val promptKey = "${character.dbValue}.s${scene.id}.reaction"

        val vars = HashMap<String, Any?>()
        vars["character"] = character.dbValue
        vars["scene"] = scene.id
        vars["decision"] = decisionKey
        // 이전 결정 컨텍스트 (Scene 3 분배 결과 등)
        session.decisions.let { vars["history"] = it }

        return try {
            val r = llm.execute("game_reaction", promptKey, vars)
            r.text
        } catch (e: Exception) {
            // AI 사이드카 down / 인증 실패 / timeout — 정적 fallback 으로 graceful degradation
            log.warn(
                "realtime LLM 실패 — 정적 fallback. character={} scene={} cause={}",
                character, scene.id, e.message,
            )
            matchResponseText(scene, decision)
        }
    }

    /**
     * Scene 의 extras 에서 사용자 결정 키에 매칭되는 정적 텍스트를 찾는다.
     *
     * 지원 형식 — extras 의 `monologues` / `outcomes` / `reactions`
     * 중 하나가 Map<String, String> 이고, decision 의 key 또는 priority 값으로 lookup.
     *
     * 매칭 못 하면 null.
     *
     * 찾은 텍스트는 반환 전에 [ForbiddenTokenScanner] 를 통과한다. 게이트가 *예외 경로*(LLM)만 막고
     * *기본 경로*(큐레이션)를 통과시키면 게이트가 아니다 — 오히려 큐레이션 yml 이 사용자에게 노출되는
     * 문장의 대부분이고, 인물이 늘수록 그 비중이 커진다. 검사를 여기(단일 choke point)에 두어
     * `resolve()` 의 두 진입 경로(정적 Scene · LLM 실패 fallback)와 직접 호출이 모두 덮인다.
     */
    fun matchResponseText(scene: Scenario.Scene, decision: Map<String, Any?>?): String? {
        val extras = scene.extras ?: return null
        if (decision == null) return null

        // decision 에서 매칭 키 추출 — 단일 값 또는 priority 필드
        val decisionKey = keyExtractor.extract(decision) ?: return null

        // 우선순위 — monologues > outcomes > reactions
        for (mapKey in arrayOf("monologues", "outcomes", "reactions")) {
            val map = extras[mapKey]
            if (map is Map<*, *>) {
                val v = map[decisionKey]
                if (v != null) return gate(v.toString(), scene, decisionKey)
            }
        }
        return null
    }

    /**
     * 정적 큐레이션 텍스트의 금지 토큰 게이트.
     *
     * 집행은 [ForbiddenTokenSanitizer] 에 위임한다 — 같은 문장이 `scenePayload` 로도 나가므로
     * ([ScenePayloadAssembler]) 두 출구가 *같은* 대체 문구를 내야 하고, 그건 대체 문구를
     * 공유 협력자 한 곳에 두어야 구조적으로 보장된다.
     *
     * 재시도는 없다: LLM 은 같은 프롬프트로 다시 뽑으면 다른 문장이 나오지만
     * yml 텍스트는 몇 번을 읽어도 같은 문장이다.
     */
    private fun gate(text: String, scene: Scenario.Scene, decisionKey: String): String =
        forbiddenTokens.sanitizeText(text, "responseText/scene ${scene.id}/$decisionKey")

    companion object {
        private val log = LoggerFactory.getLogger(ResponseResolver::class.java)
    }
}
