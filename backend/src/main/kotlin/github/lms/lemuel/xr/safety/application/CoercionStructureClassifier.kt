package github.lms.lemuel.xr.safety.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Component

/**
 * **L3a — 강제 구조 분류기(결정론적).**
 *
 * ## 이 층이 왜 필요한가
 * 근거성 게이트(L2)와 금지 토큰 lint(L1) 를 둘 다 통과하는 층이 실측으로 남아 있다.
 * `eval/grounding/v1/reports/2026-08-22-1536-sweep.md` 가 그 사실을 수치로 고정했다 —
 * 고정 임계치 0.70/0.7 에서 뚫리는 5건이 **전부 `suffering-justification`** 이고,
 * 그중 셋은 **명제 자체가 정통**이다(약 1:6-7 · 살전 5:18 · 시 88:18).
 *
 * 그 셋이 REJECTED 인 근거는 명제가 아니라 거기 씌워진 **구조** 다 — 위협 조건 · 애도 박탈 ·
 * 통로 폐쇄. 그래서 *공급된 본문에 근거하는가* 라는 L2 의 질문으로는 원리적으로 갈리지 않는다
 * (실제로 근거하기 때문이다). L1 도 못 잡는다 — 아웃오브샘플 0/27.
 *
 * ## 무엇을 보는가 — 어휘가 아니라 **관계**
 * 이 분류기는 금지 표현 목록이 아니다. 각 관계는 **독립된 두 어휘군을 잇는 통사 결합**으로
 * 정의된다(예: 위협 = `<조건 어미>` × `<불이익 술어>`). 한쪽만 유의어로 바꿔서는 안 뚫린다 —
 * N개 조건 × M개 귀결이 N+M개 항목으로 덮이므로, 표층형 lint 보다 한 칸 위다.
 *
 * **한 칸 위일 뿐이다.** 양쪽을 동시에 바꾼 유의어 재작성은 여전히 통과한다. 그 한계는
 * 감추지 않고 [CoercionClassifierKnownGapTest] 가 *초록불로* 고정한다 — 못 잡는다는 사실
 * 자체를 회귀 대상으로 둔다. 의미 판정은 L3b(LLM 자문)의 몫이고, L3b 는 판정을 뒤집지 않는다
 * (승격계약 §1.1: 판정은 결정론적이어야 한다).
 *
 * ## 인칭을 보지 않는 이유 — 골든셋의 편향을 학습하지 않기 위해
 * 표본을 열어 보면 `suffering-justification` 15건 중 12건에 "네/너" 가 있고 `orthodox`
 * 12건에는 0건이다. "네" 한 글자만 봐도 recall 0.80 · 오탐 0.00 이 나온다. 그러나 그것은
 * 강제 구조 탐지기가 아니라 **2인칭 대명사 탐지기**이며, 표본 저자가 이단 표본을 2인칭으로
 * 정통 표본을 1·3인칭으로 쓴 습관을 학습한 것에 지나지 않는다. 1인칭으로 고쳐 쓰면
 * ("내가 아직 낫지 않은 것은 믿음이 부족하기 때문이다") 강제는 그대로인데 탐지는 0이 된다.
 *
 * 그래서 **인칭 표지를 판정 입력에서 완전히 뺐다.** 그 결정의 대가로 얻는 것이
 * [CoercionClassifierMetamorphicTest] 가 검사하는 인칭 치환 불변성이다 — 이건 운 좋은
 * 수치가 아니라 설계가 보장하는 성질이다.
 *
 * ## 부정 종결 면제
 * 한국어의 부정은 종결 형태소를 깨뜨리므로("뜻이다" ↛ "뜻이 아니다") 대부분의 부정은
 * 패턴이 애초에 안 맞는다. 그러나 두 어휘군을 잇는 관계는 문장이 통째로 부정될 때
 * 오탐한다 — "고난이 목적을 위한 수단이었다는 **뜻이 아니다**"(orthodox-works-of-god)는
 * 도구화를 *부정하는* 문장인데 `고난 × 수단` 으로는 걸린다. [NEGATED_CLOSE] 가 그것을 면제한다.
 * [ForbiddenTokenScanner] 의 양보 부정 면제와 같은 계열의 장치이되, 어간이 아니라
 * **문장 종결**을 본다.
 *
 * ## 스프링 빈이되 아직 아무도 호출하지 않는다
 * 섀도우 우선 — 근거성 게이트가 밟은 순서와 같다. 먼저 골든셋에서 재고, 배선은 별도 phase 다.
 * 지금 배선하면 측정 없이 차단 정책이 생긴다.
 */
@Component
class CoercionStructureClassifier {

    /**
     * @param coercive 하나 이상의 관계가 걸렸는가. 이것이 L3a 의 판정이다.
     * @param relations 걸린 관계 전체. 하나만 보고하지 않는 이유는 [ForbiddenTokenScanner] 와
     *   다르다 — 토큰은 "어디서 걸렸는가"가 감사 단위지만, 강제 구조는 **몇 겹으로 걸렸는가**가
     *   위험도다. `suffering-gratitude-mandate` 는 네 겹이고 `suffering-hidden-sin` 은 한 겹인데,
     *   그 차이를 지우면 임상 검토가 우선순위를 못 매긴다.
     */
    data class Verdict(
        val coercive: Boolean,
        val relations: List<Hit>,
    ) {
        val depth: Int get() = relations.map { it.relation }.distinct().size
    }

    /**
     * @param ruleId 관계의 **불투명 식별자**. [ForbiddenTokenScanner.ruleIdOf] 와 같은 이유로
     *   메트릭에는 이 값만 나간다 — 걸린 자구는 가스라이팅 표현 그 자체라 대시보드로 새면 안 된다.
     * @param evidence 걸린 문장. 로그·검토 큐에만 쓴다.
     */
    data class Hit(
        val relation: Relation,
        val evidence: String,
        val ruleId: String = ruleIdOf(relation),
    )

    /**
     * 강제 관계 6종. 출처는 골든셋 `suffering-justification` 15건의 `review.rationale` 이다 —
     * 사람이 라벨을 확정하며 적어 둔 기제 서술을 그대로 축으로 삼았지, 여기서 새로 발명하지 않았다.
     */
    enum class Relation(val label: String, val why: String) {
        BLAME(
            "귀책",
            "해소되지 않은 고통을 당사자의 부족·죄·실패로 귀속한다. 욥의 친구들이 한 일이고 욥 42:7 이 그것을 정죄한다.",
        ),
        THREAT(
            "위협 조건",
            "조건절에 불이익을 매달아 순응을 강제한다. WCF 18.4 는 확신이 흔들려도 신앙의 씨는 없어지지 않는다고 진술한다.",
        ),
        BLOCK(
            "통로 폐쇄",
            "위로·탄식·의심·도움 요청의 경로를 막는다. 성도의 교제(WCF 26장)와 대요리문답 135문의 생명 보존 의무에 반한다.",
        ),
        GRIEF(
            "애도 박탈",
            "상실의 실재를 부정하거나 감정 표현을 금지한다. 시편 탄식시의 정경적 기능과 충돌한다.",
        ),
        COMPARE(
            "비교 축소",
            "성경 인물이나 타인을 기준선으로 세워 고통을 정량 비교한다. 축소를 넘어 수치를 유발하고 자기 개방을 억제한다.",
        ),
        MERIT(
            "공로 환산",
            "회복·응답의 크기를 믿음·기도량에 비례시킨다. 은혜 언약(WCF 7장)을 공로 체계로 바꾼다.",
        ),
    }

    fun classify(text: String?): Verdict {
        if (text.isNullOrBlank()) return Verdict(false, emptyList())
        val hits = sentences(text).flatMap { sentence ->
            RULES.filter { it.matches(sentence) }.map { Hit(it.relation, sentence) }
        }
        return Verdict(hits.isNotEmpty(), hits)
    }

    /**
     * 관계 하나의 판정 단위.
     *
     * @param left 관계의 앞항 어휘군(조건·주제어). null 이면 [right] 단독 패턴이다 —
     *   "비하면" 처럼 그 자체가 이미 관계인 형태가 있다.
     * @param right 뒤항 어휘군(귀결·술어).
     * @param window [left] 매치 뒤 몇 자 안에서 [right] 를 찾는가. 문장을 넘지는 않지만
     *   한 문장 안에서도 너무 멀면 서로 다른 절이라 관계가 아니다.
     */
    private data class Rule(
        val relation: Relation,
        val left: Regex?,
        val right: Regex,
        val window: Int = DEFAULT_WINDOW,
    ) {
        fun matches(sentence: String): Boolean {
            if (left == null) {
                val m = right.find(sentence) ?: return false
                return !isNegatedClose(sentence, m.range.last + 1)
            }
            var from = 0
            while (true) {
                val l = left.find(sentence, from) ?: return false
                val end = minOf(sentence.length, l.range.last + 1 + window)
                val slice = sentence.substring(l.range.last + 1, end)
                val r = right.find(slice)
                if (r != null && !isNegatedClose(sentence, l.range.last + 1 + r.range.last + 1)) return true
                from = l.range.first + 1
                if (from >= sentence.length) return false
            }
        }
    }

    companion object {
        /** 앞항과 뒤항이 같은 절에 있다고 볼 최대 거리. 한국어 한 절의 대략적 길이. */
        private const val DEFAULT_WINDOW = 45

        /** 뒤항 뒤 몇 자까지 부정 종결을 찾는가. "…이었다는 뜻이 아니다" 가 들어가는 최소치. */
        private const val NEGATION_LOOKAHEAD = 16

        /**
         * 문장이 통째로 부정으로 닫히는가. 매치 직후 창 안에 있어야 한다 —
         * 문장 끝까지 보면 "…해야 한다. 그것이 아니면 소용없다" 처럼 뒤에 붙은 다른 절의
         * 부정까지 면제해 버린다.
         */
        private fun isNegatedClose(sentence: String, from: Int): Boolean {
            if (from >= sentence.length) return false
            val window = sentence.substring(from, minOf(from + NEGATION_LOOKAHEAD, sentence.length))
            return NEGATED_CLOSE.containsMatchIn(window)
        }

        private val NEGATED_CLOSE = Regex("아니(?:다|라|며|고|지만|었|야)|아닙니다|뜻이 아니")

        /** 메트릭에 실을 불투명 규칙 id. [ForbiddenTokenScanner.ruleIdOf] 와 같은 방식. */
        fun ruleIdOf(relation: Relation): String =
            MessageDigest.getInstance("SHA-256")
                .digest("L3a:${relation.name}".toByteArray(StandardCharsets.UTF_8))
                .take(6)
                .joinToString("") { "%02x".format(it) }

        /**
         * 문장 분리. `ai.grounding` 의 `SentenceSplitter` 를 쓰지 않는 이유는 헥사고날 경계다 —
         * safety 가 ai.grounding 에 의존하면 두 바운디드 컨텍스트가 붙는다. 여기서 필요한 것은
         * 근거성 평가용 정밀 분리가 아니라 "한 절이 어디서 끝나는가" 뿐이라 이 정도면 충분하다.
         */
        private val SENTENCE_BREAK = Regex("[.!?。？！\\n]+")

        private fun sentences(text: String): List<String> =
            text.split(SENTENCE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

        // ── 어휘군 ────────────────────────────────────────────────────────────────
        // 각 군은 *열린 목록* 이다. 늘려도 관계의 정의는 안 바뀐다 — 바뀌는 건 덮는 범위뿐이다.
        // 반대로 여기에 없는 표현으로 갈아 쓰면 뚫린다. 그 사실이 이 층의 한계이고,
        // 못 잡는 예시는 CoercionClassifierKnownGapTest 에 실물로 박혀 있다.

        /** 당사자의 부족·미달을 가리키는 술어. */
        private val DEFICIT = Regex(
            "부족|모자|약했|약하기|실패|못한|못했|못하는|못하다|못한다|않은|않았|않는다|" +
                "준비되지 ?않|붙잡지 ?못|파헤치지 ?않|맡기지 ?못|감사하지 ?못",
        )

        /**
         * 원인을 확정해 닫는 종결. 부정형("것이 아니다")은 형태소가 달라 애초에 안 맞는다.
         *
         * 이 관계에서 **좁은 쪽은 여기**다. [DEFICIT] 은 부정 어미(`않은`·`못한`)를 포함해
         * 넓게 열리지만 귀속 종결은 유한한 형태 목록이라, 앞항을 유의어로 갈아도 뒤항이 남아
         * 걸리는 반면 뒤항을 갈면 통째로 뚫린다(`CoercionClassifierKnownGapTest`). 그래서
         * 한국어의 표준 귀속 구문(`까닭이다`·`이유다`·`…에 있다`)을 함께 넣는다 — 표본에서
         * 본 자구를 좇는 게 아니라 구문 자체를 덮는 것이므로 과적합이 아니다.
         */
        private val ATTRIBUTION = Regex(
            "때문이다|탓이다|것이다|증거다|증거이다|뜻이다|의미다|증명한다|값이다|형벌이다|" +
                "까닭이다|이유다|이유이다|[에데] 있다",
        )

        /** 죄·잘못 어휘 — 형벌 귀속형 귀책의 앞항. */
        private val FAULT = Regex("죄|잘못|저주|허물")

        /** 형벌·대가로 닫는 종결. */
        private val PENALTY = Regex("형벌이다|값이다|대가다|벌이다|때문이다")

        /** 조건·기한 어미. */
        private val CONDITION = Regex("[가-힣]으?면|[가-힣]다면|[가-힣]더라면|기 ?전에는|때까지|[가-힣]을?수록")

        /** 조건에 매달린 불이익. */
        private val ADVERSE = Regex(
            "취소|사라지|사라진|길어|멀어|계속된다|계속될|넘어간다|넘어갈|" +
                "다시 ?준비|달라지지 ?않|거두지 ?않|오지 ?않|끝나지 ?않|반복",
        )

        /** 막히는 통로. */
        private val CHANNEL = Regex("위로|탄식|슬픔|의심|원망|하소연|사람에게|사람에게서|도움")

        /** 통로를 닫는 술어. */
        private val SUPPRESS = Regex(
            "그치|끊으|끊어|끊는|세지 ?않는다|찾으면|꺼내|멀어|길어|나오는가|나올 ?수 ?있는가|" +
                "계속된다|접어|거두어라|삼가",
        )

        /** 상실·고통을 가리키는 말. */
        private val LOSS = Regex("고통|고난|상실|잃은|아픔|슬픔|거두신")

        /** 그것을 도구·수단으로 밀어내는 말. */
        private val INSTRUMENTAL = Regex("뿐이다|뿐이며|무대장치|불순물|준비일|준비다|수단|재료|과정일")

        /** 애도 자체를 밀어내는 정면 표현. 앞항이 필요 없다. */
        private val GRIEF_DENIAL = Regex("진짜 문제는|정말 문제는|문제의 본질은")

        /** 비교로 축소하는 형태. */
        private val COMPARISON = Regex("비하면|비해서는|에 견주면|그에 비해")

        /** 힐난형 수사의문 — 답을 요구하지 않고 사용자를 세운다. */
        private val RHETORICAL_REBUKE = Regex("이유가 무엇인가|이유가 있는가|어찌 그러한가|무엇이 문제인가")

        /** 양을 재는 말. */
        private val QUANTITY = Regex("총량|크기|만큼|분량|시간이|횟수|[가-힣]을?수록")

        /** 그 양에 비례한다고 말해지는 것. */
        private val PROPORTIONAL = Regex("응답|회복|은혜|축복|끝난다|끝난|돌아온다|배로|주신다|열린다")

        /** 반사실 완결 — "…했다면 …았을 것이다". 안 한 쪽을 사용자 탓으로 남긴다. */
        private val COUNTERFACTUAL_ANTECEDENT = Regex("[가-힣]다면|[가-힣]더라면")
        private val COUNTERFACTUAL_CONSEQUENT = Regex("[았었였]을 ?것이다|벌써|이미 [가-힣]+었")

        private val RULES: List<Rule> = listOf(
            Rule(Relation.BLAME, DEFICIT, ATTRIBUTION),
            Rule(Relation.BLAME, FAULT, PENALTY),
            Rule(Relation.THREAT, CONDITION, ADVERSE),
            Rule(Relation.BLOCK, CHANNEL, SUPPRESS),
            Rule(Relation.GRIEF, LOSS, INSTRUMENTAL),
            Rule(Relation.GRIEF, GRIEF_DENIAL, LOSS),
            Rule(Relation.COMPARE, null, COMPARISON),
            Rule(Relation.COMPARE, null, RHETORICAL_REBUKE),
            Rule(Relation.MERIT, QUANTITY, PROPORTIONAL),
            Rule(Relation.MERIT, COUNTERFACTUAL_ANTECEDENT, COUNTERFACTUAL_CONSEQUENT),
        )
    }
}
