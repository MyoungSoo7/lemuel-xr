package github.lms.lemuel.xr.tts.adapter.out.sidecar

import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.common.sidecar.SidecarHttp
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/** [TtsSynthesisPort] 의 Python TTS 사이드카(Coqui XTTS-v2) 구현. */
@Component
class TtsSynthesisSidecarAdapter(
    @Value("\${tts.base-url}") baseUrl: String,
    @Value("\${tts.max-response-bytes:16777216}") maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    @Value("\${tts.timeout-seconds:300}") timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : TtsSynthesisPort {

    private val client: WebClient = SidecarHttp.client(baseUrl, maxResponseBytes)
    private val timeout: Duration = Duration.ofSeconds(timeoutSeconds)

    override fun synthesize(
        text: String,
        voiceId: String?,
        speakingRate: Double?,
        language: String,
    ): TtsSynthesisPort.SynthesisResult =
        SidecarHttp.post(
            client, "/synthesize",
            mapOf(
                "text" to text,
                "voiceId" to (voiceId ?: "narrator-male-low"),
                "speakingRate" to (speakingRate ?: 1.0),
                // 이 키가 없던 동안 사이드카는 자기 기본값(ko)으로만 합성했다. 즉 영어 문장도
                // 한국어 발음 규칙으로 읽혔고, 응답 어디에도 그 사실이 드러나지 않았다.
                "language" to language,
            ),
            mapOf(),
            timeout, ErrorCode.E_TTS_UPSTREAM_FAIL,
        ) { resp ->
            TtsSynthesisPort.SynthesisResult(
                resp["audioUrl"] as String?,
                resp["durationMs"] as Int?,
                resp["engine"] as String?,
                resp["language"] as String?,
            )
        }

    companion object {
        /**
         * 16MiB. 사이드카는 오디오를 base64 data URL 로 인라인해서 돌려주므로
         * 응답이 WAV 원본의 약 4/3 크기가 된다 — 긴 나레이션(수십 초)까지 여유 있게 받는 값.
         * WebClient 기본값 256KB 로는 짧은 한 문장(실측 357KB)도 못 받는다. [SidecarHttp.client] 참조.
         */
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 16 * 1024 * 1024

        /**
         * 300초. 종전 30초는 **사용자 요청이 직접 이 호출을 기다리던 시절**의 값이었고,
         * 실측에서 긴 문장이 30.4초에 타임아웃 나 502 가 됐다. 그때 사이드카 로그에는
         * 같은 요청이 `200 OK` 로 남아 있었다 — 합성은 됐는데 우리가 먼저 끊은 것이다.
         *
         * 이제 이 호출은 워커 스레드에서만 일어나고 아무도 붙잡혀 기다리지 않으므로,
         * 짧은 타임아웃으로 얻을 게 없다. 다 만든 오디오를 버리는 쪽이 훨씬 손해다.
         * CPU-only XTTS-v2 의 RTF(오디오 1초당 CPU 4.1~4.6초) 로 보면 300초는
         * 대략 60~70초 분량 오디오까지 커버한다.
         */
        const val DEFAULT_TIMEOUT_SECONDS: Long = 300
    }
}
