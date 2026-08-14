package github.lms.lemuel.xr.tts.adapter.`in`.web

import github.lms.lemuel.xr.tts.application.SynthesizeTtsUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** `/api/tts/...` — 사용자 클라이언트가 직접 호출 가능. */
@RestController
@RequestMapping("/api/tts")
@Validated
class TtsController(
    private val synthesizeUc: SynthesizeTtsUseCase,
) {

    /**
     * 합성 요청. 캐시에 있으면 200 + 오디오, 없으면 **202 + jobId** 를 주고 클라이언트가 폴링한다.
     * 큐가 가득 차면 429 + `Retry-After`.
     */
    // @Valid 가 있어야 SynthesizeRequest 안의 제약이 실제로 걸린다. 클래스의 @Validated 만으로는
    // 파라미터 자신에 붙은 제약만 검사하고 body 안으로는 안 들어간다 — 종전에는 그래서 상한이
    // 5000 이든 500 이든 아무 것도 막지 못하는 장식이었다.
    @PostMapping("/synthesize")
    fun synthesize(@Valid @RequestBody req: SynthesizeRequest): ResponseEntity<SynthesizeResponse> =
        when (val r = synthesizeUc.submit(req.text, req.voiceId, req.speakingRate)) {
            is SynthesizeTtsUseCase.Submission.Ready ->
                ResponseEntity.ok(
                    SynthesizeResponse(STATUS_READY, r.audioUrl, r.durationMs, true, null),
                )

            is SynthesizeTtsUseCase.Submission.Pending ->
                ResponseEntity.accepted()
                    .body(SynthesizeResponse(STATUS_PENDING, null, null, false, r.jobId))

            is SynthesizeTtsUseCase.Submission.Rejected ->
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds(r.queueDepth).toString())
                    .body(SynthesizeResponse(STATUS_BUSY, null, null, false, null))
        }

    /** 폴링. 아직이면 pending, 끝났으면 ready + 오디오, 실패면 failed (200 으로 준다). */
    @GetMapping("/jobs/{jobId}")
    fun job(@PathVariable jobId: String): ResponseEntity<SynthesizeResponse> =
        when (val v = synthesizeUc.poll(jobId)) {
            null -> ResponseEntity.notFound().build()

            is SynthesizeTtsUseCase.JobView.Ready ->
                ResponseEntity.ok(
                    SynthesizeResponse(STATUS_READY, v.audioUrl, v.durationMs, true, jobId),
                )

            SynthesizeTtsUseCase.JobView.Pending ->
                ResponseEntity.ok(
                    SynthesizeResponse(STATUS_PENDING, null, null, false, jobId),
                )

            SynthesizeTtsUseCase.JobView.Failed ->
                ResponseEntity.ok(
                    SynthesizeResponse(STATUS_FAILED, null, null, false, jobId),
                )
        }

    /**
     * 대기열 길이로 재시도 시각을 추정한다.
     *
     * 문장 하나가 워커를 붙잡는 시간이 실측 수십 초 규모라, 앞에 n건이 있으면 최소
     * 그만큼은 기다려야 한다. 정확한 예측이 목적이 아니라 **클라이언트가 즉시
     * 재요청해 큐를 더 두드리는 것을 막는 것**이 목적이다.
     */
    private fun retryAfterSeconds(queueDepth: Int): Int =
        (queueDepth.coerceAtLeast(1) * SECONDS_PER_QUEUED_JOB).coerceAtMost(MAX_RETRY_AFTER)

    @GetMapping("/voices")
    fun voices(): ResponseEntity<VoicesResponse> =
        ResponseEntity.ok(
            VoicesResponse(
                listOf(
                    mapOf("id" to "narrator-male-low", "label" to "내레이터 (낮음)", "lang" to "ko"),
                    mapOf("id" to "narrator-female-soft", "label" to "내레이터 (부드러움)", "lang" to "ko"),
                    mapOf(
                        "id" to "goliath-bass", "label" to "골리앗", "lang" to "ko",
                        "warning" to "low-frequency threat",
                    ),
                ),
            ),
        )

    data class SynthesizeRequest(
        /**
         * 상한 500자 — 실측으로 정한 값이다.
         *
         * 앱 안의 나레이션 문자열 185개를 전수 측정한 결과 중앙값 43자, p95 198자,
         * **최대 492자** 였다. 500 이면 현재 콘텐츠에서 잘리는 문장이 0개다.
         * (300 이면 5개가 잘린다 — 실제 콘텐츠를 깎게 되므로 안 된다.)
         * 종전 값 5000 은 실제 최대치의 10배로, 상한 구실을 못 했다.
         */
        @field:NotBlank @field:Size(max = 500) val text: String,
        @field:Size(max = 50) val voiceId: String?,
        val speakingRate: Double?,
    )

    /**
     * [status] 가 이 응답의 주계약이다.
     * - `ready`   : [audioUrl] 재생 가능
     * - `pending` : [jobId] 로 `GET /api/tts/jobs/{jobId}` 폴링
     * - `failed`  : 합성 실패 — 조용히 비활성화할 것
     * - `busy`    : 큐 포화(429) — `Retry-After` 만큼 뒤에 재시도
     *
     * [cached] 는 구버전 클라이언트 호환으로 남긴다.
     */
    data class SynthesizeResponse(
        val status: String,
        val audioUrl: String?,
        val durationMs: Int?,
        val cached: Boolean,
        val jobId: String?,
    )

    data class VoicesResponse(val voices: List<Map<String, Any>>)

    companion object {
        const val STATUS_READY: String = "ready"
        const val STATUS_PENDING: String = "pending"
        const val STATUS_FAILED: String = "failed"
        const val STATUS_BUSY: String = "busy"

        /** 대기 1건당 이 정도는 걸린다고 보고 Retry-After 를 잡는다 (실측 수십 초 규모). */
        private const val SECONDS_PER_QUEUED_JOB: Int = 20

        private const val MAX_RETRY_AFTER: Int = 300
    }
}
