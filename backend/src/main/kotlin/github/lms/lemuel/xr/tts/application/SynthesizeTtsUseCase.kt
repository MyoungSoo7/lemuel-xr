package github.lms.lemuel.xr.tts.application

import github.lms.lemuel.xr.tts.application.port.out.TtsCachePort
import github.lms.lemuel.xr.tts.application.port.out.TtsJobQueuePort
import github.lms.lemuel.xr.tts.application.port.out.TtsSynthesisPort
import github.lms.lemuel.xr.tts.domain.TtsCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat

/**
 * 나레이션 합성 — 캐시 조회는 동기, 실제 합성은 비동기(202 + 폴링).
 *
 * ── 왜 비동기인가 (실측) ──
 * 버퍼 상한을 올려 `DataBufferLimitException` 을 없앤 뒤에도, 캐시에 없는 문장은
 * 30초 타임아웃에 걸려 502 가 났다. 사이드카 로그에는 같은 요청이 `200 OK` 로 찍혀
 * 있었다 — 합성은 성공했는데 HTTP 커넥션이 먼저 끊긴 것이다. 같은 문장을 다시
 * 부르면 (사이드카 캐시 히트) 1.5초에 200 이 왔다.
 *
 * 즉 남은 벽은 버그가 아니라 **합성 자체의 소요시간**이다. CPU-only XTTS-v2 는
 * 오디오 1초당 CPU 4.1~4.6초를 쓴다. HTTP 요청 하나를 그동안 붙잡고 있을 방법은
 * 없다. 그래서 요청은 즉시 돌려주고(202), 클라이언트가 폴링하게 한다.
 *
 * jobId 는 곧 cacheKey 다. 덕분에 같은 텍스트에 대한 동시 요청이 자연히 하나의
 * 작업으로 합쳐진다 — 별도 중복 제거 장치가 필요 없다.
 */
@Service
class SynthesizeTtsUseCase(
    private val cache: TtsCachePort,
    private val sidecar: TtsSynthesisPort,
    private val queue: TtsJobQueuePort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 합성을 요청한다. 캐시에 있으면 [Submission.Ready], 없으면 큐에 넣고 [Submission.Pending].
     *
     * **의도적으로 `@Transactional` 이 아니다.** 트랜잭션 안에서 큐에 넣으면 PENDING 행이
     * 커밋되기 *전에* 워커가 시작될 수 있고, 워커가 먼저 READY 를 쓴 뒤 바깥 트랜잭션이
     * 커밋되면서 PENDING 으로 덮어써 버린다. 각 save 를 자체 트랜잭션으로 두고
     * "PENDING 커밋 → 그 다음 enqueue" 순서를 보장한다.
     */
    fun submit(
        text: String,
        voiceId: String?,
        speakingRate: Double?,
        language: String? = null,
    ): Submission {
        val lang = normalizeLanguage(language)
        val key = key(text, voiceId, speakingRate, lang)

        val existing = cache.findById(key)
        if (existing.isPresent) {
            val e = existing.get()
            when {
                e.isPlayable() -> {
                    val hit = e.registerHit(LocalDateTime.now())
                    cache.save(hit)
                    return Submission.Ready(hit.audioUrl, hit.durationMs)
                }
                e.status == TtsCache.PENDING -> return Submission.Pending(key)
                // FAILED — 또는 READY 인데 오디오가 비어 있는 모순 상태. 아래로 떨어져 재시도한다.
            }
        }

        // 자리표를 *먼저 커밋* 한 뒤에 큐에 넣는다 (위 주석의 순서 보장).
        cache.save(TtsCache.pendingEntry(key, voiceId, LocalDateTime.now()))

        val accepted = queue.submit(key) { runJob(key, text, voiceId, speakingRate, lang) }
        if (!accepted) {
            // 큐가 가득 찼다. 자리표를 PENDING 으로 남겨두면 아무도 처리하지 않는데
            // 폴링만 영원히 도는 유령 작업이 된다 — FAILED 로 되돌려 재요청 가능하게 한다.
            cache.save(TtsCache.pendingEntry(key, voiceId, LocalDateTime.now()).failed())
            return Submission.Rejected(queue.queueDepth())
        }
        return Submission.Pending(key)
    }

    /** 폴링 대상 — GET /api/tts/jobs/{jobId}. */
    fun poll(jobId: String): JobView? {
        val e = cache.findById(jobId)
        if (e.isEmpty) return null
        val entry = e.get()
        return when {
            entry.isPlayable() -> JobView.Ready(entry.audioUrl, entry.durationMs)
            entry.status == TtsCache.PENDING -> JobView.Pending
            else -> JobView.Failed
        }
    }

    /**
     * 워커 스레드에서 실행되는 실제 합성.
     *
     * **트랜잭션을 걸지 않는다.** 합성은 수 분이 걸릴 수 있고, 그동안 DB 커넥션을
     * 붙잡고 있으면 커넥션 풀이 마른다. 저장은 각 save 자체 트랜잭션으로 충분하다.
     */
    private fun runJob(key: String, text: String, voiceId: String?, rate: Double?, lang: String) {
        val started = System.currentTimeMillis()
        try {
            val fresh = sidecar.synthesize(text, voiceId, rate, lang)

            // 요청한 언어와 다른 언어가 돌아왔다면 오디오 자체가 틀린 것이다. 저장하면 그
            // 잘못된 오디오가 캐시에 눌러앉아 이후 모든 요청에 그대로 나간다 — 귀로 듣기
            // 전에는 아무도 모른다. 그래서 저장하지 않고 실패로 떨어뜨린다.
            // null 은 허용한다: 새 백엔드 + 옛 사이드카가 잠깐 공존하는 롤아웃 구간에서는
            // 응답에 language 키 자체가 없다.
            if (fresh.language != null && fresh.language != lang) {
                throw IllegalStateException(
                    "TTS 사이드카가 요청과 다른 언어를 돌려줬다 — 요청=$lang, 응답=${fresh.language}",
                )
            }

            val entry = cache.findById(key).orElseGet {
                TtsCache.pendingEntry(key, voiceId, LocalDateTime.now())
            }
            cache.save(entry.completed(fresh.engine, fresh.audioUrl, fresh.durationMs))
            log.info(
                "TTS job {} 완료 — {}자, lang={}, {}ms 소요",
                key.take(8), text.length, lang, System.currentTimeMillis() - started,
            )
        } catch (e: Exception) {
            log.warn("TTS job {} 실패 — {}자", key.take(8), text.length, e)
            val entry = cache.findById(key).orElseGet {
                TtsCache.pendingEntry(key, voiceId, LocalDateTime.now())
            }
            cache.save(entry.failed())
        }
    }

    /**
     * 캐시 키 — 언어도 재료다.
     *
     * 같은 문장·같은 화자라도 언어가 다르면 오디오가 다르다. 언어를 키에서 빼면 먼저 구워진
     * 쪽이 이기고, 나중 요청은 엉뚱한 언어의 오디오를 캐시 히트로 받아간다.
     *
     * 단 `ko` 는 접두를 붙이지 않는다. 이 서비스는 여태 ko 로만 합성했으므로 기존 캐시 행은
     * 전부 ko 다 — 전부에 접두를 붙이면 그 행들이 한꺼번에 무효가 되고 다시 구워야 한다.
     * (사이드카 캐시도 같은 규칙을 쓴다: `tts/app.py` 의 `LEGACY_KEY_LANG`.)
     */
    private fun key(text: String, voiceId: String?, rate: Double?, language: String): String {
        val langPart = if (language == LEGACY_KEY_LANGUAGE) "" else "$language|"
        val concat = langPart + text + "|" + (voiceId ?: "") +
            "|" + (rate?.toString() ?: "1.0")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(concat.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }

    /**
     * 빈 값이면 [DEFAULT_LANGUAGE], 지원 목록 밖이면 예외.
     *
     * 컨트롤러가 이미 400 으로 거르지만 여기서도 막는다 — 유스케이스는 웹 어댑터 말고도
     * 불릴 수 있고, 검증 안 된 언어가 사이드카까지 가면 그때는 **비동기 워커 안에서** 400 이
     * 나 사용자에게는 이유 없는 `failed` 로만 보인다.
     */
    private fun normalizeLanguage(language: String?): String {
        val lang = language?.trim()?.lowercase().orEmpty()
        if (lang.isEmpty()) return DEFAULT_LANGUAGE
        require(lang in SUPPORTED_LANGUAGES) {
            "지원하지 않는 언어: $language (지원: ${SUPPORTED_LANGUAGES.joinToString(", ")})"
        }
        return lang
    }

    companion object {
        /**
         * 지원 언어. 이 목록의 근거는 **사이드카 이미지가 빌드 시 실제로 합성해 본 언어**다
         * (`tts/selftest.py` 가 이 둘을 전부 합성하지 못하면 이미지 빌드가 실패한다).
         * 늘리려면 사이드카의 `SUPPORTED_LANGS` 와 selftest 를 먼저 늘려야 한다.
         */
        val SUPPORTED_LANGUAGES: Set<String> = setOf("ko", "en")

        const val DEFAULT_LANGUAGE: String = "ko"

        /** 캐시 키의 옛 공간(언어 접두가 없던 시절)을 소유하는 언어. [key] 참조. */
        private const val LEGACY_KEY_LANGUAGE: String = "ko"
    }

    /** POST /api/tts/synthesize 의 결과. */
    sealed interface Submission {
        /** 캐시 히트 — 바로 재생 가능. */
        data class Ready(val audioUrl: String?, val durationMs: Int?) : Submission

        /** 큐에 올라감 — [jobId] 로 폴링할 것. */
        data class Pending(val jobId: String) : Submission

        /** 큐 포화 — 429. */
        data class Rejected(val queueDepth: Int) : Submission
    }

    /** GET /api/tts/jobs/{jobId} 의 결과. */
    sealed interface JobView {
        data class Ready(val audioUrl: String?, val durationMs: Int?) : JobView
        data object Pending : JobView
        data object Failed : JobView
    }
}
