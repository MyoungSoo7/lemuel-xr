package github.lms.lemuel.xr.common.jackson

import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.application.EvaluateGroundingUseCase
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 레거시 Jackson 2 매퍼 빈이 **프로덕션에서 실제로 쓰이는 형태 그대로** 동작하는지 본다.
 *
 * 이 테스트가 존재하는 이유 — 2026-08-05 골든셋 정기 채점이 운영에서만 매번 실패했다.
 * 기존 테스트들은 전부 `jacksonObjectMapper()` 를 손으로 만들어 넘겼고, 프로덕션만
 * [JacksonCompatConfig.legacyJacksonObjectMapper] 를 주입받았다. 즉 **테스트가 검증한 매퍼와
 * 배포된 매퍼가 서로 다른 물건이었다.** 그래서 여기서는 매퍼를 만들지 않고 빈을 호출한다.
 * 이 파일에서 `jacksonObjectMapper()` 를 쓰면 테스트의 의미가 사라진다.
 */
class LegacyJacksonObjectMapperTest {

    private val mapper = JacksonCompatConfig().legacyJacksonObjectMapper()

    @Test
    fun `기본값 없는 Kotlin data class 를 역직렬화할 수 있다`() {
        // Passage 는 파라미터에 기본값이 없다 → KotlinModule 이 없으면 여기서
        // "no Creators, like default constructor, exist" 로 터진다.
        val passage = mapper.readValue(
            """{"reference":"시편 23:1","text":"여호와는 나의 목자시니"}""",
            EvaluateGroundingUseCase.Passage::class.java,
        )

        assertThat(passage.reference).isEqualTo("시편 23:1")
        assertThat(passage.text).isEqualTo("여호와는 나의 목자시니")
    }

    @Test
    fun `이 매퍼로 골든셋 전체가 근거 본문까지 채워진 채 읽힌다`() {
        val dataset = ClasspathGoldenSetAdapter(mapper).load(GoldenSet.DEFAULT_VERSION)

        assertThat(dataset.fixtures).isNotEmpty()

        // passages 가 비면 채점이 근거 없이 돌아 전부 오답이 된다. 로드 성공만으로는 부족하고
        // 안쪽까지 채워졌는지 봐야 한다 — 실제 사고도 바로 이 한 단계 안쪽에서 터졌다.
        //
        // 단 NO_EVIDENCE 표본(structural-no-evidence)은 근거가 *없는* 상황 자체를 시험하므로
        // 정당하게 비어 있다. 그 한 건까지 싸잡아 요구하면 데이터가 아니라 테스트가 틀린 것이다.
        val mustHavePassages = dataset.fixtures.filter { it.expected != GroundingStatus.NO_EVIDENCE }
        assertThat(mustHavePassages)
            .describedAs("근거가 있어야 하는 픽스처")
            .isNotEmpty()
        assertThat(mustHavePassages.filter { it.passages.isEmpty() }.map { it.id })
            .describedAs("근거 본문이 비어 있는 픽스처")
            .isEmpty()

        assertThat(dataset.fixtures.flatMap { it.passages }.map { it.text })
            .describedAs("근거 본문 문자열")
            .allSatisfy { assertThat(it).isNotBlank() }
    }
}
