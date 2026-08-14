package github.lms.lemuel.xr.tts.application.port.out

/**
 * TTS 합성 아웃바운드 포트 — 외부 TTS 사이드카(Coqui XTTS-v2) 호출을 애플리케이션 계층에서
 * 격리한다. ai `LlmGenerationPort` · emotion `EmotionClassificationPort` 와 동일 패턴:
 * WebClient 등 구체 HTTP 기술은 `adapter/out/sidecar` 구현에만 존재한다.
 */
interface TtsSynthesisPort {

    /**
     * text+voice+rate+language → wav URL. 실패 시 `E_TTS_UPSTREAM_FAIL`.
     *
     * [language] 는 `ko`·`en` 만 유효하다. 사이드카는 모르는 값을 조용히 기본 언어로
     * 떨어뜨리지 않고 400 을 준다 — 그러니 호출 전에 걸러야 한다
     * ([github.lms.lemuel.xr.tts.application.SynthesizeTtsUseCase.SUPPORTED_LANGUAGES]).
     */
    fun synthesize(
        text: String,
        voiceId: String?,
        speakingRate: Double?,
        language: String,
    ): SynthesisResult

    data class SynthesisResult(
        val audioUrl: String?,
        val durationMs: Int?,
        val engine: String?,
        /** 사이드카가 실제로 합성한 언어. 요청과 다르면 계약 위반이다. */
        val language: String? = null,
    )
}
