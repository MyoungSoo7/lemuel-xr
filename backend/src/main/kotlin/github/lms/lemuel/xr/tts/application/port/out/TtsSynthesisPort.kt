package github.lms.lemuel.xr.tts.application.port.out

/**
 * TTS 합성 아웃바운드 포트 — 외부 TTS 사이드카(Coqui XTTS-v2) 호출을 애플리케이션 계층에서
 * 격리한다. ai `LlmGenerationPort` · emotion `EmotionClassificationPort` 와 동일 패턴:
 * WebClient 등 구체 HTTP 기술은 `adapter/out/sidecar` 구현에만 존재한다.
 */
interface TtsSynthesisPort {

    /** text+voice+rate → wav URL. 실패 시 `E_TTS_UPSTREAM_FAIL`. */
    fun synthesize(text: String, voiceId: String?, speakingRate: Double?): SynthesisResult

    data class SynthesisResult(
        val audioUrl: String?,
        val durationMs: Int?,
        val engine: String?,
    )
}
