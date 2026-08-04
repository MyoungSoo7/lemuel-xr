package github.lms.lemuel.xr.ai.grounding.adapter.out.metrics

import github.lms.lemuel.xr.ai.grounding.application.EvaluateGoldenSetUseCase
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.ai.grounding.eval.EvalMetrics
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 지표가 **모르는 것을 아는 척하지 않는지** 확인하는 테스트.
 *
 * 표본이 없을 때 0.0 을 내보내면 대시보드에서 "오탐률 0%" 로 읽혀 승격조건 P3(<5%) 을
 * 표본 없이 통과한 것처럼 보인다. 그래서 NaN 이어야 한다 — 이 파일의 절반이 그 검증이다.
 */
class MicrometerGoldenSetEvalMetricsAdapterTest {

    private val registry = SimpleMeterRegistry()
    private val clock = Clock.fixed(Instant.parse("2026-08-04T18:30:00Z"), ZoneOffset.UTC)
    private val adapter = MicrometerGoldenSetEvalMetricsAdapter(registry, clock)

    private fun gauge(name: String) = registry.find("grounding.goldenset.$name").gauge()?.value()

    private fun outcome(id: String, expected: GroundingStatus, actual: GroundingStatus, className: String) =
        EvalMetrics.Outcome(
            id = id,
            className = className,
            difficulty = "medium",
            reviewStatus = GoldenSet.ReviewStatus.SIGNED_OFF,
            expected = expected,
            actual = actual,
            unsupportedRate = 0.0,
        )

    private fun result(outcomes: List<EvalMetrics.Outcome>) = EvaluateGoldenSetUseCase.Result(
        version = "v1",
        policy = GroundingPolicy(0.62, 0.3),
        summary = EvalMetrics.summarize(outcomes),
        outcomes = outcomes,
        embeddedTexts = 11,
        excludedDrafts = 6,
    )

    private val sample = result(
        listOf(
            outcome("h1", GroundingStatus.REJECTED, GroundingStatus.REJECTED, "heterodox"),
            outcome("h2", GroundingStatus.REJECTED, GroundingStatus.ACCEPTED, "heterodox"),
            outcome("o1", GroundingStatus.ACCEPTED, GroundingStatus.ACCEPTED, "orthodox"),
        ),
    )

    @Test
    fun `채점 전에는 0 이 아니라 NaN 이다`() {
        listOf(
            "precision", "recall", "f1", "false_reject_rate", "false_positive_rate",
            "exact_accuracy", "abstain_rate", "samples", "mismatches",
            "excluded_drafts", "embedded_texts",
            "policy.similarity_threshold", "policy.max_unsupported_rate",
            "last_success_epoch_seconds",
        ).forEach { name ->
            assertThat(gauge(name))
                .describedAs("$name 은 값이 없을 때 NaN 이어야 한다 — 0 은 '완벽함' 으로 오독된다")
                .isNaN()
        }
    }

    @Test
    fun `채점 결과가 게이지에 반영된다`() {
        adapter.publish(sample)

        assertThat(gauge("precision")).isEqualTo(1.0)
        assertThat(gauge("recall")).isEqualTo(0.5)
        assertThat(gauge("false_reject_rate")).isEqualTo(0.0)
        assertThat(gauge("false_positive_rate")).isEqualTo(0.0)
        assertThat(gauge("samples")).isEqualTo(3.0)
        assertThat(gauge("mismatches")).isEqualTo(1.0)
        assertThat(gauge("excluded_drafts")).isEqualTo(6.0)
        assertThat(gauge("embedded_texts")).isEqualTo(11.0)
        // 임계치를 함께 내보내야 "지표가 왜 움직였나" 를 사후에 되짚을 수 있다.
        assertThat(gauge("policy.similarity_threshold")).isEqualTo(0.62)
        assertThat(gauge("policy.max_unsupported_rate")).isEqualTo(0.3)
        assertThat(gauge("last_success_epoch_seconds")).isEqualTo(clock.instant().epochSecond.toDouble())
        assertThat(registry.get("grounding.goldenset.runs").counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `클래스별 정확도는 층마다 따로 나온다`() {
        adapter.publish(sample)

        // 전체 정확도 2/3 하나만 보면 heterodox 층이 반쪽 났다는 사실이 묻힌다.
        val heterodox = registry.get("grounding.goldenset.class_accuracy").tag("class", "heterodox").gauge()
        val orthodox = registry.get("grounding.goldenset.class_accuracy").tag("class", "orthodox").gauge()
        assertThat(heterodox.value()).isEqualTo(0.5)
        assertThat(orthodox.value()).isEqualTo(1.0)
    }

    @Test
    fun `두 번 실행해도 미터가 중복 등록되지 않고 값만 갱신된다`() {
        adapter.publish(sample)
        adapter.publish(result(listOf(outcome("h1", GroundingStatus.REJECTED, GroundingStatus.REJECTED, "heterodox"))))

        assertThat(registry.find("grounding.goldenset.precision").gauges()).hasSize(1)
        assertThat(registry.find("grounding.goldenset.class_accuracy").gauges()).hasSize(2)
        assertThat(gauge("recall")).isEqualTo(1.0)
        // 이번 실행에 없던 클래스는 마지막 값이 남는다 — 사라진 게 아니라 갱신이 멈춘 것이다.
        assertThat(registry.get("grounding.goldenset.class_accuracy").tag("class", "orthodox").gauge().value())
            .isEqualTo(1.0)
        assertThat(registry.get("grounding.goldenset.runs").counter().count()).isEqualTo(2.0)
    }

    @Test
    fun `실패는 세기만 하고 지난 성공 지표를 덮어쓰지 않는다`() {
        adapter.publish(sample)
        adapter.failed()

        // 장애 때 지표를 0 으로 밀면 "정밀도가 0 으로 떨어졌다" 는 가짜 회귀 알람이 뜬다.
        assertThat(gauge("precision")).isEqualTo(1.0)
        assertThat(gauge("last_success_epoch_seconds")).isEqualTo(clock.instant().epochSecond.toDouble())
        assertThat(registry.get("grounding.goldenset.failures").counter().count()).isEqualTo(1.0)
        assertThat(registry.get("grounding.goldenset.runs").counter().count()).isEqualTo(1.0)
    }

    @Test
    fun `분모가 없는 지표는 NaN 으로 나간다`() {
        // 전부 구조적 상태라 이진 지표의 분모가 0 인 경우.
        adapter.publish(
            result(listOf(outcome("s1", GroundingStatus.NO_EVIDENCE, GroundingStatus.NO_EVIDENCE, "structural"))),
        )

        assertThat(gauge("precision")).isNaN()
        assertThat(gauge("recall")).isNaN()
        assertThat(gauge("abstain_rate")).isNaN()
        // 완전 일치율은 계산 가능하므로 값이 나와야 한다 — 전부 NaN 이면 그것대로 눈이 먼다.
        assertThat(gauge("exact_accuracy")).isEqualTo(1.0)
    }
}
