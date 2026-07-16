package github.lms.lemuel.xr.emotion.application.port.out

/**
 * 감정 분류 out 포트 (DIP) — application 계층은 이 포트에만 의존하고, 실제 HTTP 호출은
 * `adapter/out/sidecar` 의 어댑터가 담당한다.
 *
 * Python AI 사이드카 (`ai/`) 의 `POST /classify-emotion`
 * (`{text}` → `{emotion, confidence}`) 하나만 추상화. 호출/파싱 실패 시
 * `null` 을 반환하고, 도메인 fallback(CONFUSED) 판단은 호출자
 * (`EmotionClassifierService`) 에 맡긴다.
 */
interface EmotionClassificationPort {

    data class AiResponse(val emotion: String?, val confidence: Double)

    /** 사이드카 호출 성공 시 파싱된 응답, 실패(네트워크/5xx/빈본문)면 `null`. */
    fun classify(rawText: String): AiResponse?
}
