package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 직전 결정에 대한 응답 텍스트를 *어떤 전략으로 만들지* 결정하는 전략 컴포넌트.
 *
 * - `Scene.realtimeLlm=true` → 실시간 LLM 호출 (실패 시 정적 fallback)
 * - 그 외 → yml 의 monologues/outcomes/reactions 정적 lookup
 *
 * `DecideSceneUseCase` 의 god `execute()` 에서 응답 해석 책임을 분리한 것.
 * LLM 클라이언트([GenerateLlmResponseUseCase])는 ai 컨텍스트의 협력자로 주입한다.
 */
@Component
class ResponseResolver(
    private val llm: GenerateLlmResponseUseCase,
    private val keyExtractor: DecisionKeyExtractor,
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
                if (v != null) return v.toString()
            }
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResponseResolver::class.java)
    }
}
