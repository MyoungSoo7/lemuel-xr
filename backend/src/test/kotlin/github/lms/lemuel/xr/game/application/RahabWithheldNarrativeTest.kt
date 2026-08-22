package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.LemuelXrApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * `scenarios/rahab.yml` 의 세 게이트를 **기계로** 확인한다 —
 * `content_fidelity_rahab` · `R4_consent_delta_is_real` · `staging_invariants_rahab`.
 *
 * 이 파일이 없으면 셋 다 `enforcement: reviewer_only` 로 남는다. 정직하긴 하지만
 * 아무것도 지키지 않는다.
 *
 * ── 왜 이 인물에 별도 검사가 필요한가 ──
 * 라합은 **낭독 트랙**이다(`docs/SEED-RAHAB.md` rev.15 D1). 사용자 선택 분기가 없으므로
 * 다섯 Scene 전부가 `interactions: []` · `branches: []` 이고, 사용자 행위는 속도·동의/거절·
 * 이탈 셋뿐이다. 그 말은 **동의 카드가 유일한 통제**라는 뜻이다. 카드가 없애 주겠다고 한
 * 자막이 실제로 빠지지 않으면 이 미션에는 남는 보호가 없다.
 *
 * ── 왜 배제 자구를 기대값으로 적지 않는가 ──
 * `DanielViolenceExclusionTest` 와 같은 이유다. 잘라낸 문장을 기대값에 적으면 그 문장이
 * 리포로 돌아오고, `extras` 는 payload 로 통째 복사되므로 화면으로 나갈 길이 생긴다.
 * 그래서 여기서는 "없어야 할 것" 을 열거하지 않고 **저작 정본과 같은가** 로만 판정한다.
 * 기대값의 출처는 `content/rahab/scene1~5.yml` 이고, 이 검사는 그 둘이 갈라지는 순간
 * 빨개진다.
 *
 * ── `JacobReconciliationPressureTest` 와 갈리는 점 ──
 * 야곱은 **있어야 하는 문장**(해제 문구)을 파일에 적어 래칫으로 썼다. 라합에는 그런 단일
 * 문장이 없다 — 지켜야 할 것이 "24행이 저작과 같다" 라는 관계이지 특정 자구가 아니다.
 * 그래서 상수 대신 저작 트리를 읽는다. 대신 **개수를 못 박는다**(24행 · 네 블록) —
 * 저작과 런타임을 같이 비우는 편집이 관계 검사만으로는 통과하기 때문이다.
 */
class RahabWithheldNarrativeTest {

    @Test
    fun `라합 런타임 자막이 저작 정본의 자구와 한 글자도 다르지 않다`() {
        var total = 0

        for (sceneId in 1..5) {
            val authored = authoredCaptions(sceneId)
            val runtime = runtimeCaptions(sceneId)

            assertThat(runtime.map { it["text_ko"]?.toString() })
                .describedAs(
                    "Scene %d — 런타임 자막이 저작 정본과 다르다. 이 파일은 저작의 투영이고, " +
                        "런타임에서 자구를 발명하지 않는다(자구 정본 docs/VERSES-RAHAB-GAE.md)",
                    sceneId,
                )
                .containsExactlyElementsOf(authored.map { it["text_ko"]?.toString() })

            /*
              화자 표기도 같이 잰다. 저작의 `attribution_display_ko` 가 null 인 행은 런타임에서
              `speaker_ko` 키 자체를 두지 않는다 — 프론트 캡션 타입이 `speaker_ko?` 라 없으면
              아무것도 렌더하지 않는다. "해설" 같은 대체 화자를 지어 넣으면 본문이 누구에게도
              돌리지 않은 말에 발화자가 생긴다. 그 형태를 여기서 막는다.
            */
            assertThat(runtime.map { it["speaker_ko"]?.toString() })
                .describedAs(
                    "Scene %d — 화자 표기가 저작과 다르다. 저작이 비워 둔 자리에 화자를 지어 넣지 않는다",
                    sceneId,
                )
                .containsExactlyElementsOf(authored.map { it["attribution_display_ko"]?.toString() })

            total += runtime.size
        }

        // 개수를 못 박는 이유는 공집합 전칭명제 방지다 — 저작과 런타임의 자막을 함께 지우면
        // 위의 대조가 자동으로 참이 된다. 24 = 3+6+4+4+7.
        assertThat(total)
            .describedAs("라합 자막이 %d행이다 — 다섯 Scene 합계 %d 여야 한다", total, EXPECTED_CAPTIONS)
            .isEqualTo(EXPECTED_CAPTIONS)
    }

    @Test
    fun `건너뛴 사람에게서 빠지는 자막이 저작이 지정한 그 줄들이다`() {
        val checked = mutableListOf<String>()

        for (sceneId in 1..5) {
            val authored = authoredCaptions(sceneId)
            val routes = authored.mapNotNull { it["omitted_on_route"]?.toString() }.distinct()
            if (routes.isEmpty()) continue

            for (route in routes) {
                val block = runtimeBlock(sceneId, route)
                assertThat(block)
                    .describedAs("Scene %d — 저작이 지정한 경로 '%s' 에 해당하는 축약 블록이 런타임에 없다", sceneId, route)
                    .isNotNull()
                requireNotNull(block)

                val expected = authored
                    .filterNot { it["omitted_on_route"]?.toString() == route }
                    .map { it["text_ko"]?.toString() }

                /*
                  성경 자막만 비교한다. 축약 경로에는 비성경 내레이션이 한 줄 더 붙는 자리가
                  있고(Scene 5 의 `rahab_nar_s5_close`), 그 줄은 `verse_ref` 가 없다.
                  내레이션까지 기대값에 섞으면 이 검사가 "저작과 같은가" 가 아니라
                  "내가 적은 것과 같은가" 가 된다.
                */
                val actual = blockCaptions(block)
                    .filter { it.containsKey("verse_ref") }
                    .map { it["text_ko"]?.toString() }

                assertThat(actual)
                    .describedAs(
                        "Scene %d 경로 '%s' — 빠지는 자막이 저작이 지정한 줄들과 다르다. " +
                            "덜 빠지면 카드가 한 약속이 거짓이 되고, 더 빠지면 약속하지 않은 것을 가져간다.",
                        sceneId, route,
                    )
                    .containsExactlyElementsOf(expected)

                // 거절 델타가 공집합이면 그 카드는 보호를 얻었다는 착각만 준다.
                assertThat(actual.size)
                    .describedAs("Scene %d 경로 '%s' — 빠지는 자막이 없다. 빈 안전 통제다", sceneId, route)
                    .isLessThan(authored.size)

                checked += "$sceneId:$route"
            }
        }

        // 네 경로 — s1_no_epithet · s3_omit_2_13_2_14 · s4_declined · s5_close_on_6_23_6_27.
        // Scene 2 는 씬 전체를 건너뛰므로 축약 블록이 아니라 정수 목적지를 쓴다(머리 주석 1).
        assertThat(checked)
            .describedAs("저작이 지정한 축약 경로가 %d 건이다 — %d 여야 한다", checked.size, EXPECTED_ROUTES)
            .hasSize(EXPECTED_ROUTES)
    }

    @Test
    fun `성구 참조가 자막에 남는 절만 가리킨다`() {
        /*
          자막에서 뺀 절이 **성구 블록으로 되돌아오는** 형태를 막는다. 화면은
          `scripture_ref` 하나만 여는 것이 아니라 `additional_refs` 도 전부 연다
          (`ScenePassage` — frontend/src/app/mission-passage.test.tsx 셋째 케이스).
          그래서 축약 블록이 자막만 줄이고 참조를 그대로 두면, 호칭을 거절한 사용자가
          바로 아래에서 그 절들을 **전절 자구로** 다시 만난다. 자막 대조(위 검사)만으로는
          이 누출이 초록으로 통과한다 — 축약 블록의 captions 는 실제로 줄어 있기 때문이다.

          판정식은 하나다: `additional_refs` == 그 경로에 남는 자막의 절 − `scripture_ref`.
          기대값을 손으로 적지 않고 자막의 `verse_ref` 에서 만든다.
        */
        var checkedRoutes = 0

        for (sceneId in 1..5) {
            val scene = runtimeScene(sceneId)
            val sceneRef = scene["scripture_ref"]?.toString()

            assertRefsMatchCaptions(
                where = "Scene $sceneId 정본 경로",
                captions = runtimeCaptions(sceneId),
                sceneRef = sceneRef,
                refs = additionalRefs(extrasOf(sceneId)),
            )

            @Suppress("UNCHECKED_CAST")
            val blocks = (extrasOf(sceneId)["conditional_blocks"] as? List<Map<String, Any?>>).orEmpty()
            for (block in blocks) {
                assertRefsMatchCaptions(
                    where = "Scene $sceneId 축약 경로 '${block["id"]}'",
                    captions = blockCaptions(block),
                    sceneRef = sceneRef,
                    // 블록 계약: 메타 키를 뺀 나머지가 payload override 다. 블록에 이 키가
                    // 없으면 정본 목록이 그대로 살아 나간다 — 그 상태를 통과시키지 않는다.
                    refs = additionalRefs(block),
                )
                checkedRoutes++
            }
        }

        assertThat(checkedRoutes)
            .describedAs("축약 경로 %d 건을 쟀다 — %d 여야 한다", checkedRoutes, EXPECTED_ROUTES)
            .isEqualTo(EXPECTED_ROUTES)
    }

    @Test
    fun `다섯 Scene 의 위기 래칭과 연출 불변식이 값까지 같다`() {
        val latches = (1..5).map { it to extrasOf(it)["crisis_latch"] as? Map<*, *> }
        val staging = (1..5).map { it to (extrasOf(it)["staging_invariants"] as? List<*>).orEmpty() }

        val missingLatch = latches.filter { it.second == null }.map { it.first }
        assertThat(missingLatch)
            .describedAs("R1 래치 3키가 없는 Scene 이 있다 — 한 Scene 만 풀리면 그 Scene 에서 통제가 사라지고 나머지 넷이 초록이라 조용하다")
            .isEmpty()

        /*
          저작 정본에서 기대값을 가져온다. 세 키는 `content/rahab/scene1.yml` 최상위에 있고,
          다섯 저작 파일이 같은 값을 갖는다. 여기 상수로 복제하면 저작이 값을 바꿔도 이 검사가
          옛 값을 계속 지키게 된다 — 그건 래칫이 아니라 표류다.
        */
        val expectedLatch = authoredRoot(1).let { root ->
            LATCH_KEYS.associateWith { root[it]?.toString() }
        }
        assertThat(expectedLatch.values).doesNotContainNull()

        for ((sceneId, latch) in latches) {
            assertThat(LATCH_KEYS.associateWith { latch?.get(it)?.toString() })
                .describedAs("Scene %d 의 R1 래치 값이 저작 정본과 다르다", sceneId)
                .isEqualTo(expectedLatch)
        }

        /*
          저작의 `staging_constraints` 는 여덟 항이고 그중 앞 세 항이 「불변식」 으로 시작한다 —
          사용자를 어디에 두지 않는가에 관한 것들이다. 나머지 다섯(접근선·조도·컷 길이 등)은
          연출 지침이라 런타임 payload 로 나갈 이유가 없다.

          기대값은 **저작 Scene 1** 의 세 항이다. 저작 Scene 2~5 는 같은 세 항을 줄여 적었는데
          (예: 3항의 「(수 2:1 의 유숙)」 이 빠져 있다), 런타임은 다섯 Scene 모두에 Scene 1 의
          긴 형태를 쓴다 — 어느 Scene 에서도 통제의 범위 주석이 사라지지 않게 하려는 것이다.
          그래서 여기서는 Scene 1 을 정본으로 삼고, 나머지 넷은 **개수만** 확인한다.
        */
        val expectedInvariants = authoredInvariants(1)
        assertThat(expectedInvariants)
            .describedAs("저작 Scene 1 에 연출 불변식이 없다 — 사용자를 라합의 몸·정탐꾼의 자리에 두지 않는다는 통제가 통째로 빈다")
            .hasSize(EXPECTED_INVARIANTS)

        for ((sceneId, list) in staging) {
            assertThat(list.map { it.toString() })
                .describedAs("Scene %d 의 연출 불변식이 저작 정본과 다르다", sceneId)
                .containsExactlyElementsOf(expectedInvariants)

            assertThat(authoredInvariants(sceneId))
                .describedAs("저작 Scene %d 에서 연출 불변식이 %d 항으로 줄었다", sceneId, authoredInvariants(sceneId).size)
                .hasSize(EXPECTED_INVARIANTS)
        }

        /*
          낭독 트랙이라 사용자 선택 분기가 없다(seed rev.15 D1). 그 결정이 런타임에서 지켜지는지
          같이 본다 — 인터랙션이 하나라도 들어오면 본문이 라합에게 돌린 행위를 사용자가
          가로채는 형태가 열린다(저작 §3-1).
        */
        for (sceneId in 1..5) {
            val extras = extrasOf(sceneId)
            assertThat((extras["interactions"] as? List<*>).orEmpty())
                .describedAs("Scene %d 에 인터랙션이 생겼다 — 라합은 낭독 트랙이고 선택 분기를 두지 않는다", sceneId)
                .isEmpty()
            assertThat((extras["branches"] as? List<*>).orEmpty())
                .describedAs("Scene %d 에 분기가 생겼다 — 라합은 낭독 트랙이고 선택 분기를 두지 않는다", sceneId)
                .isEmpty()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun runtimeScene(sceneId: Int): Map<String, Any?> =
        (runtimeRoot()["scenes"] as List<Map<String, Any?>>).single { it["id"] == sceneId }

    @Suppress("UNCHECKED_CAST")
    private fun extrasOf(sceneId: Int): Map<String, Any?> =
        runtimeScene(sceneId)["extras"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun runtimeCaptions(sceneId: Int): List<Map<String, Any?>> =
        extrasOf(sceneId)["captions"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun runtimeBlock(sceneId: Int, blockId: String): Map<String, Any?>? =
        (extrasOf(sceneId)["conditional_blocks"] as? List<Map<String, Any?>>)
            ?.singleOrNull { it["id"] == blockId }

    @Suppress("UNCHECKED_CAST")
    private fun blockCaptions(block: Map<String, Any?>): List<Map<String, Any?>> =
        (block["captions"] as? List<Map<String, Any?>>).orEmpty()

    private fun runtimeRoot(): Map<String, Any?> {
        val url = requireNotNull(javaClass.classLoader.getResource("scenarios/rahab.yml")) {
            "클래스패스에 scenarios/rahab.yml 이 없다"
        }

        @Suppress("UNCHECKED_CAST")
        return File(url.toURI()).reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
    }

    /**
     * 저작 정본은 클래스패스가 아니라 **리포 트리**에 있다(`content/rahab/`). 런타임 리소스로
     * 복사되지 않는 것이 의도다 — 저작 파일에는 배제 자구를 가리키는 주석과 미해소 부채가
     * 들어 있고, payload 로 나갈 이유가 없다.
     */
    private fun authoredRoot(sceneId: Int): Map<String, Any?> {
        val file = repoRoot().resolve("content/rahab/scene$sceneId.yml").toFile()
        check(file.isFile) { "저작 정본이 없다: ${file.path}" }

        @Suppress("UNCHECKED_CAST")
        return file.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun authoredCaptions(sceneId: Int): List<Map<String, Any?>> =
        authoredRoot(sceneId)["captions"] as List<Map<String, Any?>>

    private fun authoredInvariants(sceneId: Int): List<String> =
        (authoredRoot(sceneId)["staging_constraints"] as? List<*>).orEmpty()
            .map { it.toString() }
            .filter { it.startsWith("불변식") }

    /**
     * payload 의 `additional_refs` 를 읽는다. 씬/블록 어느 층에서든 같은 키다 —
     * 조건 블록은 메타 키(`id`·`reached_by`·`renders`·`note`)를 뺀 나머지가 payload
     * override 이므로, 블록에 이 키가 있으면 씬의 목록을 **덮는다**.
     */
    private fun additionalRefs(layer: Map<String, Any?>): List<String>? =
        (layer["additional_refs"] as? List<*>)?.map { it.toString() }

    /**
     * 「그 경로에 남는 자막의 절」 − 「`scripture_ref`」 == 「`additional_refs`」 를 판정한다.
     * 기대값은 자막의 `verse_ref` 에서 만든다 — 손으로 적으면 자막과 참조가 함께 틀려도
     * 초록이 된다.
     */
    private fun assertRefsMatchCaptions(
        where: String,
        captions: List<Map<String, Any?>>,
        sceneRef: String?,
        refs: List<String>?,
    ) {
        val labels = captions.mapNotNull { it["verse_ref"]?.toString() }

        /*
          반절 인용(`수 6:17b`)은 참조 키가 될 수 없다 — 조회 키는 절 단위라
          `jos-6:17` 로 열면 전절이 뜨고, 그 앞머리가 미션 밖으로 배제한 자구다
          (docs/SCRIPTURE-REF-CONVENTION.md — 반절은 형식불가).
        */
        val (halves, wholes) = labels.partition { HALF_VERSE.containsMatchIn(it) }
        val expected = wholes.map { toRef(it) }.distinct().filter { it != sceneRef }

        assertThat(refs)
            .describedAs(
                "%s — `additional_refs` 가 없다. 축약 블록이 자막만 줄이고 참조를 그대로 두면 " +
                    "정본 목록이 살아 나가고, 자막에서 지운 절이 바로 아래 성구 블록에 전절로 뜬다",
                where,
            )
            .isNotNull()

        assertThat(refs)
            .describedAs(
                "%s — 성구 참조가 그 경로에 남는 자막의 절과 다르다. 더 있으면 뺀 자막이 " +
                    "본문으로 되돌아오고(축약을 약속하고 축약하지 않는다), 덜 있으면 읽히는 자막의 " +
                    "본문이 화면에서 빠진다",
                where,
            )
            .containsExactlyInAnyOrderElementsOf(expected)

        for (half in halves) {
            assertThat(refs.orEmpty())
                .describedAs("%s — 반절 인용 '%s' 이 참조로 들어갔다. 절 단위로 열려 배제 자구가 함께 뜬다", where, half)
                .doesNotContain(toRef(half.removeSuffix("a").removeSuffix("b")))
        }
    }

    /** `수 2:9` → `jos-2:9`. 약칭이 표에 없으면 조용히 건너뛰지 않고 실패한다. */
    private fun toRef(label: String): String {
        val (book, rest) = label.trim().split(" ", limit = 2)
        val code = checkNotNull(BOOK_CODES[book]) {
            "성경 약칭 '$book' 의 참조 코드를 모른다 — 자막이 새 책을 인용했다면 이 표에 넣어야 한다"
        }
        return "$code-$rest"
    }

    private fun repoRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p?.parent) { "리포 루트 탐색 실패" }
    }

    private companion object {
        /** 3 + 6 + 4 + 4 + 7. 저작 트리와 런타임을 같이 비우는 편집을 막는 계수다. */
        const val EXPECTED_CAPTIONS = 24

        /** 저작 `omitted_on_route` 가 지정한 축약 경로 수. Scene 2 는 정수 목적지라 여기 없다. */
        const val EXPECTED_ROUTES = 4

        /** 라합의 연출 불변식 3종(저작 `staging_constraints` 중 사용자 배치에 관한 것). */
        const val EXPECTED_INVARIANTS = 3

        /** 자막이 인용하는 세 책. 라합 밖으로 넓히지 않는다 — 모르면 실패시키는 것이 목적이다. */
        val BOOK_CODES = mapOf("수" to "jos", "약" to "jas", "마" to "matt")

        /** `6:17b` 처럼 절 뒤에 a·b 가 붙은 반절 인용. */
        val HALF_VERSE = Regex("""\d+[ab]$""")

        val LATCH_KEYS = listOf(
            "post_crisis_render_policy",
            "post_crisis_latch_scope",
            "crisis_card_position",
        )
    }
}
