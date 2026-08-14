package github.lms.lemuel.xr.common.sidecar

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import java.time.Duration
import java.util.function.Function
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

/**
 * WebClient 기반 사이드카 POST 호출의 **공통 봉투(envelope)** — Template Method(합성 버전).
 *
 * ai `LlmGenerationSidecarAdapter` 와 tts `TtsSynthesisSidecarAdapter` 가
 * 공유하던 `retrieve → timeout → block → null-body 검사 → try/catch 로 AppException 래핑`
 * 구조를 한곳으로 모은다. 각 어댑터별로 달라지는 부분(엔드포인트·요청 본문·헤더·에러코드·응답 파싱)은
 * 인자와 `mapper` 훅으로 주입한다.
 *
 * emotion 사이드카는 RestClient + "실패 시 null 반환" 계약이라 이 봉투를 공유하지 않는다 (의도적).
 */
object SidecarHttp {

    private val log = LoggerFactory.getLogger(SidecarHttp::class.java)

    /**
     * 사이드카용 [WebClient] 를 만든다 — **인메모리 버퍼 한도를 반드시 명시해서**.
     *
     * `WebClient.builder().build()` 의 기본 한도는 256KB 다. 그런데 TTS 사이드카는 WAV 를
     * base64 data URL 로 통째로 실어 보낸다: 실측으로 5.7초짜리 한 문장이 WAV 268KB → base64
     * **357KB** 라, 기본값에서는 사이드카가 200 을 돌려준 *뒤에* 디코더가
     * `DataBufferLimitException: Exceeded limit on max bytes to buffer : 262144` 로 터졌다.
     * 호출자에겐 502(E_TTS_UPSTREAM_FAIL)로 보이고, 사이드카 로그엔 성공만 남아
     * "엔진은 멀쩡한데 소리는 한 번도 안 나는" 상태가 된다.
     *
     * 그래서 이 팩토리를 통하지 않고 `WebClient.builder()` 를 직접 쓰지 말 것.
     */
    @JvmStatic
    fun client(baseUrl: String, maxInMemoryBytes: Int): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(maxInMemoryBytes) }
            .build()

    /**
     * 사이드카에 JSON POST 후 응답 Map 을 `mapper` 로 도메인 타입으로 변환해 반환한다.
     * 빈 본문·타임아웃·업스트림 오류는 `onFail` 코드의 [AppException] 으로 정규화한다.
     *
     * @param mapper 응답 Map → 결과 타입 변환 훅 (어댑터별로 다른 유일한 부분)
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> post(
        client: WebClient,
        uri: String,
        body: Map<String, Any?>,
        headers: Map<String, String>,
        timeout: Duration,
        onFail: ErrorCode,
        mapper: Function<Map<String, Any?>, T>,
    ): T {
        try {
            val resp = client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { h -> headers.forEach { (k, v) -> h.add(k, v) } }
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .timeout(timeout)
                .block() as Map<String, Any?>?
                ?: throw AppException(onFail, "empty body")
            return mapper.apply(resp)
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            log.warn("사이드카 {} 실패: {}", uri, e.message)
            throw AppException(onFail, e.message, e)
        }
    }
}
