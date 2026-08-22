package github.lms.lemuel.xr.safety.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset.ClasspathGoldenSetAdapter
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * **인칭 치환 불변성** — L3a 가 강제 구조를 보는지, 아니면 2인칭 대명사를 보는지 가른다.
 *
 * ## 왜 이 검사가 필요한가
 * 골든셋을 열어 보면 `suffering-justification` 15건 중 12건에 "네/너" 가 있고 `orthodox`
 * 12건에는 0건이다. **"네" 한 글자만 봐도 recall 0.80 · 오탐 0.00 이 나온다.** 어떤 분류기든
 * 이 표본에서 좋은 수치를 내기는 쉽고, 그 수치의 대부분은 표본 저자가 이단 표본을 2인칭으로,
 * 정통 표본을 1·3인칭으로 쓴 **작성 습관**에서 온다.
 *
 * 그래서 골든셋의 수치만으로는 "구조를 보는 분류기"와 "대명사를 보는 분류기"를 구별할 수 없다.
 * 이 테스트가 그 구별을 만든다 — 라벨을 바꾸지 않는 변환을 가하고 판정이 그대로인지 본다
 * (metamorphic testing). 불변량은 하나다:
 *
 * > **인칭을 바꿔도 강제 구조는 바뀌지 않는다.**
 *
 * "네가 아직 낫지 않은 것은 믿음이 부족하기 때문이다" 를 "내가 …" 로 고쳐도 강제는 그대로다.
 * 오히려 앱이 그 말을 사용자 입에 넣는 형태라 더 나쁘다. 반대로 정통 묵상의 "나는" 을 "너는"
 * 으로 고쳐도 정통이다 — 2인칭이 곧 강제는 아니다.
 *
 * ## 새 라벨을 만드는 게 아니다
 * 여기서 만드는 문장은 기존 픽스처의 **기계적 변환**이고 라벨은 변환 규칙이 보존한다. 그래서
 * manifest rules 1번("라벨은 사람이 확정한다")에 걸리지 않는다 — 사람이 확정한 라벨을 그대로
 * 물려받을 뿐 새 판정을 만들지 않는다.
 */
class CoercionClassifierMetamorphicTest {

    private val classifier = CoercionStructureClassifier()
    private val dataset: GoldenSet.Loaded = ClasspathGoldenSetAdapter(jacksonObjectMapper())
        .load(GoldenSet.DEFAULT_VERSION)

    @Test
    fun `강제 구조를 1인칭으로 고쳐 써도 그대로 잡는다`() {
        val coercive = dataset.signedOff.filter { it.`class` == COERCIVE_CLASS }.sortedBy { it.id }
        val before = coercive.filter { classifier.classify(it.meditationText).coercive }.map { it.id }
        val after = coercive.filter { classifier.classify(toFirstPerson(it.meditationText)).coercive }.map { it.id }

        println(
            buildString {
                append("\n=== 인칭 치환 불변성 (2인칭 → 1인칭) ===\n")
                append("  치환 전 탐지: ${before.size}/${coercive.size}\n")
                append("  치환 후 탐지: ${after.size}/${coercive.size}\n")
                (before - after.toSet()).forEach { append("  ⚠️ 치환으로 놓침: $it\n") }
            },
        )

        assertThat(after)
            .describedAs(
                "인칭을 1인칭으로 바꾸자 탐지가 줄었다 = 이 분류기는 강제 구조가 아니라 2인칭 대명사를 보고 있다. " +
                    "골든셋 수치가 좋아 보이는 것은 표본 저자의 작성 습관 덕이지 분류기의 성질이 아니다.",
            )
            .isEqualTo(before)
    }

    @Test
    fun `정통 묵상을 2인칭으로 고쳐 써도 걸리지 않는다`() {
        val orthodox = dataset.signedOff.filter { it.`class` == ORTHODOX_CLASS }.sortedBy { it.id }
        val flagged = orthodox
            .map { it.id to classifier.classify(toSecondPerson(it.meditationText)) }
            .filter { it.second.coercive }

        assertThat(flagged.map { it.first })
            .describedAs(
                "정통 묵상을 2인칭으로 바꾸자 걸렸다 = 분류기가 '너에게 말하면 강제' 라고 학습한 것이다. " +
                    "2인칭은 강제의 조건이 아니다 — 실제 앱의 묵상은 대부분 2인칭으로 나간다.",
            )
            .isEmpty()
    }

    /** 2인칭 → 1인칭. 조사 결합형을 먼저 치환해야 "네가" 가 "내"+"가" 로 쪼개지지 않는다. */
    private fun toFirstPerson(text: String): String =
        text.replace("네가", "내가")
            .replace("너를", "나를")
            .replace("너는", "나는")
            .replace("너에게", "나에게")
            .replace("네게", "내게")
            .replace("당신이", "내가")
            .replace("당신의", "나의")
            .replace(Regex("네 (?=[가-힣])"), "내 ")

    /** 1인칭 → 2인칭. 위의 역방향. */
    private fun toSecondPerson(text: String): String =
        text.replace("내가", "네가")
            .replace("나를", "너를")
            .replace("나는", "너는")
            .replace("나에게", "너에게")
            .replace("내게", "네게")
            .replace(Regex("내 (?=[가-힣])"), "네 ")

    private companion object {
        const val COERCIVE_CLASS = "suffering-justification"
        const val ORTHODOX_CLASS = "orthodox"
    }
}
