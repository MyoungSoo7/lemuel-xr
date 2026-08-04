package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetEvalMetricsPort
import github.lms.lemuel.xr.ai.grounding.eval.EvalMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 골든셋 채점 결과 → Prometheus Gauge. 이미 배치돼 있는 ServiceMonitor 가
 * `/actuator/prometheus` 를 긁으므로 신규 수집 컴포넌트(Pushgateway 등)가 필요 없다.
 *
 * ## 값이 없으면 0 이 아니라 NaN 이다
 * 표본이 없거나 분모가 0 이면 `NaN` 을 내보낸다. 0.0 으로 채우면 "오탐률 0%" 로 읽혀
 * 승격조건 P3(<5%) 을 **표본 없이 통과한 것처럼** 보이게 만든다. 대시보드에서 빈 값이
 * 보이는 쪽이 조용히 틀린 값보다 낫다.
 *
 * ## 게이지는 한 번만 등록한다
 * Micrometer 게이지는 등록 시점의 공급 함수를 계속 호출한다. 그래서 최신 결과를
 * [AtomicReference] 에 담아 두고 게이지가 그것을 읽게 한다 — 매 실행마다 새 미터를
 * 만들면 registry 가 중복 미터로 오염된다.
 */
class MicrometerGoldenSetEvalMetricsAdapter(
    private val registry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) : GoldenSetEvalMetricsPort {

    private val latest = AtomicReference<EvaluateGoldenSetUseCase.Result?>(null)
    private val lastSuccessEpoch = AtomicReference(Double.NaN)
    private val classGauges = ConcurrentHashMap<String, AtomicReference<Double>>()

    private val runs: Counter = Counter.builder("$PREFIX.runs")
        .description("골든셋 채점 실행 횟수(성공)")
        .register(registry)

    private val failures: Counter = Counter.builder("$PREFIX.failures")
        .description("골든셋 채점 실패 횟수(임베딩 장애 등)")
        .register(registry)

    init {
        gauge("precision", "정밀도 TP/(TP+FP)") { it.summary.binary.precision }
        gauge("recall", "재현율 TP/(TP+FN)") { it.summary.binary.recall }
        gauge("f1", "F1") { it.summary.binary.f1 }
        // 승격계약 §2 P3 이 가리키는 값. 통계적 FPR 과 다른 정의라 이름을 갈라 둘 다 낸다.
        gauge("false_reject_rate", "P3 오탐률 FP/(TP+FP) — reject 표본 기준") {
            it.summary.binary.p3FalseRejectRate
        }
        gauge("false_positive_rate", "통계적 FPR FP/(FP+TN)") { it.summary.binary.falsePositiveRate }
        gauge("exact_accuracy", "구조적 상태까지 포함한 완전 일치율") { it.summary.exactAccuracy }
        gauge("abstain_rate", "판정 불가 비율 — 높으면 위 지표를 믿으면 안 된다") {
            it.summary.binary.abstainRate
        }
        gauge("samples", "채점에 쓰인 signed_off 표본 수") { it.summary.sampleCount.toDouble() }
        gauge("mismatches", "기대와 다른 판정이 나온 픽스처 수") { it.summary.mismatches.size.toDouble() }
        gauge("excluded_drafts", "사인오프 전이라 제외한 draft 수") { it.excludedDrafts.toDouble() }
        gauge("embedded_texts", "직전 실행의 임베딩 API 호출 텍스트 수") { it.embeddedTexts.toDouble() }
        // 임계치를 지표로 같이 내보내야 "언제 무엇이 바뀌어 지표가 움직였나" 를 사후에 되짚을 수 있다.
        gauge("policy.similarity_threshold", "적용된 문장 근거 임계치") { it.policy.similarityThreshold }
        gauge("policy.max_unsupported_rate", "적용된 허용 미근거 비율") { it.policy.maxUnsupportedRate }

        Gauge.builder("$PREFIX.last_success_epoch_seconds") { lastSuccessEpoch.get() }
            .description("마지막 성공 채점 시각(epoch seconds). 이 값이 늙으면 평가가 멈춘 것")
            .strongReference(true)
            .register(registry)
    }

    override fun publish(result: EvaluateGoldenSetUseCase.Result) {
        latest.set(result)
        result.summary.perClass.forEach { stats -> classGauge(stats).set(stats.accuracy ?: Double.NaN) }
        lastSuccessEpoch.set(clock.instant().epochSecond.toDouble())
        runs.increment()
    }

    override fun failed() {
        failures.increment()
    }

    /** 클래스(층)별 정확도. 전체 평균만 보면 회색지대 층이 망가져도 안 보인다. */
    private fun classGauge(stats: EvalMetrics.ClassStats): AtomicReference<Double> =
        classGauges.computeIfAbsent(stats.className) { name ->
            AtomicReference(Double.NaN).also { ref ->
                Gauge.builder("$PREFIX.class_accuracy") { ref.get() }
                    .description("클래스별 완전 일치율")
                    .tag("class", name)
                    .strongReference(true)
                    .register(registry)
            }
        }

    private fun gauge(
        name: String,
        description: String,
        value: (EvaluateGoldenSetUseCase.Result) -> Double?,
    ) {
        Gauge.builder("$PREFIX.$name") { latest.get()?.let(value) ?: Double.NaN }
            .description(description)
            .strongReference(true)
            .register(registry)
    }

    private companion object {
        const val PREFIX = "grounding.goldenset"
    }
}
