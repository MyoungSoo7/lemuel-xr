package github.lms.lemuel.xr.safety.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 금지 토큰 *집행* 서비스 — [ForbiddenTokenScanner] 가 "걸렸는지" 를 판정하고,
 * 이 클래스가 "그래서 무엇을 내보낼지" 를 정한다.
 *
 * 이 분리가 필요한 이유는 게이트를 걸어야 할 출구가 하나가 아니기 때문이다. 같은 yml 문장이
 * 여러 경로로 클라이언트에 나가는데, 경로마다 대체 문구를 따로 주입하면 그중 하나만 바뀌거나
 * 하나만 빠지는 일이 생긴다. 실제로 그렇게 됐다 — 게이트가 LLM 경로에만 있었다.
 *
 * 현재 집행 지점:
 * - `game.ResponseResolver.matchResponseText` — `/decide` 의 `responseText` (정적 큐레이션)
 * - `game.ScenePayloadAssembler.build` — `/start`·`/decide` 의 `scenePayload` 전체
 * - `game.CompleteGameSessionUseCase.extractValuePrompt` — `/complete` 의 `valuePrompt.message`
 *
 * (LLM 경로 `ai.GenerateLlmResponseUseCase` 는 재시도 정책이 얽혀 있어 자체 처리하지만
 * 같은 설정 키 `safety.forbidden-tokens.fallback-text` 를 읽으므로 사용자가 보는 문구는 동일하다.)
 */
@Component
class ForbiddenTokenSanitizer(
    private val scanner: ForbiddenTokenScanner,
    @param:Value("\${safety.forbidden-tokens.fallback-text:}") private val fallbackText: String,
) {

    /**
     * 문자열 하나를 검사해 걸리면 대체 문구로 바꾼다.
     *
     * @param where 로그용 위치 표식. 런타임 대체는 사용자를 지키는 것이지 *저작 결함을 지우는 것이 아니다* —
     *   저작자가 어느 yml 의 어느 키를 고쳐야 하는지 로그만 보고 찾을 수 있어야 한다.
     */
    fun sanitizeText(text: String, where: String): String {
        val scan = scanner.scan(text)
        if (!scan.matched) {
            return text
        }
        log.warn("금지 토큰 — 대체 문구로 교체. at={} token={}", where, scan.matchedToken)
        return fallbackText
    }

    /**
     * payload 를 재귀로 훑어 **문자열 leaf 마다** 검사·치환한다.
     * [github.lms.lemuel.xr.game.application.CrisisTokenResolver] 의 순회와 같은 모양이다.
     *
     * 설계상 두 가지를 지킨다.
     *
     * 1. **맵 전체를 대체 문구로 갈아치우지 않는다.** `monologues` 안의 한 문장이 걸렸다고 맵째로
     *    날리면 나머지 분기 텍스트가 같이 사라지고, 클라이언트가 기대하는 자료구조가 무너진다.
     *    걸린 leaf 하나만 교체한다.
     * 2. **키는 건드리지 않는다** (`k to walk(v)`). `sceneId`·`narrationId` 같은 구조 키와
     *    캐시 키는 값이 아니라 키이거나 비문자열이라 순회의 영향을 받지 않는다.
     *    문자열 *값* 인 구조 필드(`narrationId` 등)는 검사 대상에 들어가지만, 금지 토큰은
     *    한국어 문장이고 그 값들은 ascii snake_case 라 매칭될 수 없다. 반대로 `title` 처럼
     *    사용자가 실제로 읽는 문자열은 검사 대상이어야 하므로, 키 이름으로 예외를 두지 않는다
     *    (예외 목록은 새 키가 생길 때마다 낡는다).
     */
    @Suppress("UNCHECKED_CAST")
    fun sanitize(payload: Map<String, Any?>, where: String): Map<String, Any?> =
        walk(payload, where) as Map<String, Any?>

    private fun walk(value: Any?, path: String): Any? = when (value) {
        is String -> sanitizeText(value, path)
        is Map<*, *> -> value.entries.associate { (k, v) -> k to walk(v, "$path.$k") }
        is List<*> -> value.mapIndexed { i, v -> walk(v, "$path[$i]") }
        else -> value
    }

    private companion object {
        val log = LoggerFactory.getLogger(ForbiddenTokenSanitizer::class.java)
    }
}
