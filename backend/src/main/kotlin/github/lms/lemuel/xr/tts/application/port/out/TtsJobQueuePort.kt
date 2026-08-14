package github.lms.lemuel.xr.tts.application.port.out

/**
 * 합성 작업을 백그라운드로 넘기는 아웃바운드 포트.
 *
 * 왜 큐가 *유계(bounded)* 여야 하는가 — 실측 근거:
 * CPU-only Coqui XTTS-v2 의 실시간 계수(RTF)는 오디오 1초당 CPU 4.1~4.6초다.
 * 즉 워커 하나가 문장 하나에 수십 초~수 분을 붙잡는다. 큐가 무제한이면 요청이
 * 쌓이는 만큼 대기시간이 선형으로 늘어, 뒤에 선 사용자는 영원히 못 받으면서
 * 서버는 "정상" 으로 보인다. 차라리 즉시 429 로 거절하고 Retry-After 를 주는 편이
 * 사용자에게 정직하다.
 */
interface TtsJobQueuePort {

    /**
     * 작업을 큐에 넣는다.
     *
     * @return 큐가 가득 차 받지 못했으면 false. 호출자는 이때 429 로 응답해야 한다.
     */
    fun submit(jobId: String, task: () -> Unit): Boolean

    /** 대기 중인 작업 수 — Retry-After 를 추정하는 데 쓴다. */
    fun queueDepth(): Int
}
