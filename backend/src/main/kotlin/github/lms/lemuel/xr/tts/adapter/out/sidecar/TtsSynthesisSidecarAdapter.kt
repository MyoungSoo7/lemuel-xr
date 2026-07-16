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
) : TtsSynthesisPort {

    private val client: WebClient = WebClient.builder().baseUrl(baseUrl).build()
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
}
