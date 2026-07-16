package github.lms.lemuel.xr.game.application

import org.springframework.stereotype.Component

/**
 * 결정 페이로드(JSON → Map)에서 정적 lookup / LLM variable 로 쓸 *매칭 키* 를 추출한다.
 *
 * `DecideSceneUseCase` 의 god-method 에서 분리된 순수 파싱 책임.
 *
 * - { "value": "save_33" } → "save_33"
 * - { "priority": "farmer" } → "farmer"
 * - { "save_33": true } → "save_33"
 * - 단일 string wrapper ({"_": "reveal"}) → 값
 */
@Component
class DecisionKeyExtractor {

    fun extract(decision: Map<String, Any?>?): String? {
        if (decision == null) return null

        // 1) priority 필드 (Scene 3 distribute 패턴)
        val priority = decision["priority"]
        if (priority is String && priority.isNotBlank()) return priority

        // 2) value 필드
        val value = decision["value"]
        if (value is String && value.isNotBlank()) return value

        // 3) 단일 entry 의 key (Controller 가 raw string 을 받았을 때 wrapper)
        if (decision.size == 1) {
            val e = decision.entries.iterator().next()
            val v = e.value
            if (v is String) return v // {"_": "reveal"}
            return e.key // {"save_33": true}
        }
        return null
    }
}
