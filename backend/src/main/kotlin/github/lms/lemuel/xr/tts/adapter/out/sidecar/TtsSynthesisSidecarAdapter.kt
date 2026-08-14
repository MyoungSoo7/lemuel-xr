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
) : TtsSynthesisPort {

    private val client: WebClient = SidecarHttp.client(baseUrl, maxResponseBytes)
    private val timeout: Duration = Duration.ofSeconds(30)

    override fun synthesize(text: String, voiceId: String?, speakingRate: Double?): TtsSynthesisPort.SynthesisResult =
        SidecarHttp.post(
            client, "/synthesize",
            mapOf(
                "text" to text,
                "voiceId" to (voiceId ?: "narrator-male-low"),
                "speakingRate" to (speakingRate ?: 1.0),
            ),
            mapOf(),
            timeout, ErrorCode.E_TTS_UPSTREAM_FAIL,
        ) { resp ->
            TtsSynthesisPort.SynthesisResult(
                resp["audioUrl"] as String?,
                resp["durationMs"] as Int?,
                resp["engine"] as String?,
            )
        }

    companion object {
        /**
         * 16MiB. 사이드카는 오디오를 base64 data URL 로 인라인해서 돌려주므로
         * 응답이 WAV 원본의 약 4/3 크기가 된다 — 긴 나레이션(수십 초)까지 여유 있게 받는 값.
         * WebClient 기본값 256KB 로는 짧은 한 문장(실측 357KB)도 못 받는다. [SidecarHttp.client] 참조.
         */
        const val DEFAULT_MAX_RESPONSE_BYTES: Int = 16 * 1024 * 1024
    }
}
