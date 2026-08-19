package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.ai.application.GenerateLlmResponseUseCase
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameDecisionPort
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.game.domain.GameDecision
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.game.domain.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class DecideSceneUseCaseTest {

    private val owner: UUID = UUID.randomUUID()
    private val sessions: GameSessionPort = mock()
    private val decisions: GameDecisionPort = mock()
    private val loader: ScenarioYamlLoader = mock()
    private val llm: GenerateLlmResponseUseCase = mock()

    // ResponseResolver 는 실제 협력자(real) — LLM 클라이언트만 mock, 키 추출은 진짜 로직.
    private val uc: DecideSceneUseCase = DecideSceneUseCase(
        sessions, decisions, loader,
        // AiOptOutPort mock 은 기본값 false(=opt-out 아님) — 종전 거동 그대로 LLM 경로가 살아있다.
        ResponseResolver(llm, DecisionKeyExtractor(), mock(), SafetyGateFixtures.sanitizer()),
        // ScenePayloadAssembler 도 실제 협력자 — 위기 토큰 치환·금지 토큰 게이트까지 통과하는지 함께 본다.
        ScenePayloadAssembler(CrisisTokenResolver { _, _ -> "109" }, SafetyGateFixtures.sanitizer()),
        SceneConvergenceResolver(DecisionKeyExtractor()),
        SceneSkipResolver(DecisionKeyExtractor()),
    )

    private fun scene(id: Int, next: Int?, llmFlag: Boolean?, extras: Map<String, Any?>?): Scenario.Scene =
        Scenario.Scene(
            id, "장면$id", "interaction", "pick_one", 60,
            "narr", "ref", llmFlag, next, extras,
        )

    private fun liveSession(id: UUID, character: String?, dimension: String?): GameSession =
        GameSession.reconstitute(
            id, owner, null, character, dimension,
            LocalDateTime.now(), null, null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    private fun completedSession(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, owner, null, character, null,
            LocalDateTime.now(), LocalDateTime.now(), null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    private fun abandonedSession(id: UUID, character: String?): GameSession =
        GameSession.reconstitute(
            id, owner, null, character, null,
            LocalDateTime.now(), null, LocalDateTime.now(), null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )

    // ── happy path: 정적 monologue lookup, next scene payload ──
    @Test
    fun `execute 정적 monologue lookup 후 다음scene payload`() {
        val sid = UUID.randomUUID()
        val monos = mapOf("save_33" to "요셉이 실제로 따른 비율")
        val scenario = Scenario(
            "joseph", "곡식 7년",
            listOf(
                scene(2, 3, false, mapOf("monologues" to monos)),
                scene(3, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "emotional")))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(2, mapOf("value" to "save_33"), mapOf("ms" to 1200), "emotional"),
        )

        assertThat(r.previousScene).isEqualTo(2)
        assertThat(r.currentScene).isEqualTo(3)
        assertThat(r.responseText).isEqualTo("요셉이 실제로 따른 비율")
        assertThat(r.scenePayload).containsEntry("sceneId", 3)

        // decision 영속 검증
        val cap = argumentCaptor<GameDecision>()
        verify(decisions).save(cap.capture())
        assertThat(cap.firstValue.sceneNumber).isEqualTo(2.toShort())
        assertThat(cap.firstValue.sceneName).isEqualTo("장면2")
    }

    /**
     * R4 동의 카드의 건너뛰기 — 이 use case 가 `next` 보다 목적지를 먼저 본다는 계약.
     *
     * 룻 Scene 1 의 형태다: 진입 카드가 Scene 1·2 를 함께 덮으므로 목적지는 3인데 `next` 는 2다.
     * 2026-08-20 이전 엔진은 결정만 기록하고 `next` 를 따라가서, **사별 서사를 건너뛰겠다고
     * 고른 사용자를 그 카드가 덮기로 한 Scene 2 로 보냈다.**
     */
    @Test
    fun `execute 건너뛰기는 next 가 아니라 카드가 정한 목적지로 간다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "ruth", "룻",
            listOf(
                scene(
                    1, 2, false,
                    mapOf(
                        "trigger_warning" to mapOf(
                            "covers_scenes" to listOf(1, 2),
                            "skip_alternative_scene_id" to 3,
                        ),
                    ),
                ),
                scene(2, 3, false, emptyMap()),
                scene(3, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(1, mapOf("value" to "skip"), null, null),
        )

        assertThat(r.currentScene).describedAs("건너뛴 사용자가 덮인 Scene 2 로 가면 안 된다").isEqualTo(3)
        assertThat(r.scenePayload).containsEntry("sceneId", 3)
        // 건너뛰기도 결정이다 — 기록은 그대로 남는다.
        verify(decisions).save(any())
    }

    @Test
    fun `execute 카드 거절은 종결 payload`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "ruth", "룻",
            listOf(
                scene(
                    3, 4, false,
                    mapOf(
                        "trigger_warning" to mapOf(
                            "covers_scenes" to listOf(3, 5),
                            "skip_alternative_scene_id" to 4,
                            "declined_route" to "closing",
                        ),
                    ),
                ),
                scene(4, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(3, mapOf("value" to "decline"), null, null),
        )

        assertThat(r.scenePayload).isEqualTo(mapOf("type" to "end"))
    }

    /** 마지막 씬의 축약 경로 — 씬은 그대로, 블록이 선언한 override 만 payload 에 덮인다. */
    @Test
    fun `execute 축약 블록 건너뛰기는 같은 scene 에 머물며 자막을 비운다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "ruth", "룻",
            listOf(
                scene(
                    5, null, false,
                    mapOf(
                        "consent_coverage" to mapOf(
                            "inherited" to true,
                            "skip_alternative_scene_id" to "ruth_scene5_alt_short",
                        ),
                        "captions" to listOf("성문 낭독 1", "성문 낭독 2"),
                        "closing_lines" to listOf("마감 한 줄"),
                        "closing_screen" to mapOf("kind" to "closing"),
                        "conditional_blocks" to listOf(
                            mapOf(
                                "id" to "ruth_scene5_alt_short",
                                "renders" to listOf("closing_lines", "closing_screen"),
                                "captions" to emptyList<String>(),
                            ),
                        ),
                    ),
                ),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(5, mapOf("value" to "skip"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(5)
        assertThat(r.scenePayload).containsEntry("captions", emptyList<String>())
        // 축약 경로도 마감에는 도달한다 — 블록이 renders 로 약속한 것들.
        assertThat(r.scenePayload).containsEntry("closing_lines", listOf("마감 한 줄"))
        assertThat(r.scenePayload).containsEntry("conditionalBlockId", "ruth_scene5_alt_short")
    }

    /**
     * 카드 하나가 여러 씬을 덮을 때 — 건너뛰기는 목적지 하나로 끝나지 않는다.
     *
     * 룻의 중간 카드는 Scene 3 과 5 를 함께 덮고 목적지는 4다. 4의 `next` 는 5이므로,
     * 선택을 들고 오지 않으면 **건너뛰기를 고른 사용자가 `next` 를 따라 Scene 5 —
     * 같은 카드가 덮기로 한 씬 — 의 전체 자막으로 흘러들어간다.** 목적지만 지키는 건
     * 절반짜리 보호다.
     */
    @Test
    fun `execute 앞 씬에서 고른 건너뛰기는 같은 카드가 덮은 뒤 씬까지 이어진다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "ruth", "룻",
            listOf(
                scene(4, 5, false, emptyMap()),
                scene(
                    5, null, false,
                    mapOf(
                        "consent_coverage" to mapOf(
                            "inherited" to true,
                            "covered_by_scene" to 3,
                            "skip_alternative_scene_id" to "ruth_scene5_alt_short",
                        ),
                        "captions" to listOf("성문 낭독 1", "성문 낭독 2"),
                        "closing_lines" to listOf("마감 한 줄"),
                        "conditional_blocks" to listOf(
                            mapOf(
                                "id" to "ruth_scene5_alt_short",
                                "renders" to listOf("closing_lines"),
                                "captions" to emptyList<String>(),
                            ),
                        ),
                    ),
                ),
            ),
        )
        // Scene 3 에서 이미 건너뛰기를 골랐다 — 세션에 그 결정이 남아 있다.
        val session = liveSession(sid, "joseph", null)
        session.recordDecision(3, mapOf("value" to "skip"))
        whenever(sessions.findById(sid)).thenReturn(Optional.of(session))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("value" to "go"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(5)
        assertThat(r.scenePayload)
            .describedAs("Scene 3 에서 건너뛴 사용자에게 Scene 5 의 성문 낭독 자막이 그대로 가면 안 된다")
            .containsEntry("captions", emptyList<String>())
        assertThat(r.scenePayload).containsEntry("closing_lines", listOf("마감 한 줄"))
        assertThat(r.scenePayload).containsEntry("conditionalBlockId", "ruth_scene5_alt_short")
    }

    @Test
    fun `execute 건너뛰지 않은 사용자는 덮인 씬의 자막을 그대로 받는다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "ruth", "룻",
            listOf(
                scene(4, 5, false, emptyMap()),
                scene(
                    5, null, false,
                    mapOf(
                        "consent_coverage" to mapOf(
                            "inherited" to true,
                            "covered_by_scene" to 3,
                            "skip_alternative_scene_id" to "ruth_scene5_alt_short",
                        ),
                        "captions" to listOf("성문 낭독 1", "성문 낭독 2"),
                        "conditional_blocks" to listOf(
                            mapOf("id" to "ruth_scene5_alt_short", "captions" to emptyList<String>()),
                        ),
                    ),
                ),
            ),
        )
        val session = liveSession(sid, "joseph", null)
        session.recordDecision(3, mapOf("value" to "continue"))
        whenever(sessions.findById(sid)).thenReturn(Optional.of(session))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("value" to "go"), null, null),
        )

        assertThat(r.scenePayload).containsEntry("captions", listOf("성문 낭독 1", "성문 낭독 2"))
        assertThat(r.scenePayload).doesNotContainKey("conditionalBlockId")
    }

    @Test
    fun `execute 마지막scene next null 이면 end payload`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "moses", "광야",
            listOf(scene(5, null, false, emptyMap())),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)))
        whenever(loader.forCharacter(Character.MOSES)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.MOSES,
            DecideSceneUseCase.Input(5, mapOf("value" to "go"), null, null),
        )

        assertThat(r.previousScene).isEqualTo(5)
        assertThat(r.currentScene).isEqualTo(5)
        assertThat(r.scenePayload).containsEntry("type", "end")
    }

    @Test
    fun `execute realtime llm 성공`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "joseph", "t",
            listOf(
                scene(4, 5, true, mapOf("reactions" to mapOf("reveal" to "정적"))),
                scene(5, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)
        whenever(llm.execute(any(), any(), any()))
            .thenReturn(GenerateLlmResponseUseCase.Result("LLM 실시간 응답", "openai", "gpt", false))

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("priority" to "reveal"), null, null),
        )

        assertThat(r.responseText).isEqualTo("LLM 실시간 응답")
        val keyCap = argumentCaptor<String>()
        verify(llm).execute(any(), keyCap.capture(), any())
        assertThat(keyCap.firstValue).isEqualTo("joseph.s4.reaction")
    }

    @Test
    fun `execute realtime llm 실패시 정적 fallback`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "joseph", "t",
            listOf(
                scene(4, 5, true, mapOf("reactions" to mapOf("reveal" to "정적 fallback 텍스트"))),
                scene(5, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)
        whenever(llm.execute(any(), any(), any()))
            .thenThrow(RuntimeException("sidecar down"))

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(4, mapOf("value" to "reveal"), null, null),
        )

        assertThat(r.responseText).isEqualTo("정적 fallback 텍스트")
    }

    // ── IDOR 회귀 ──

    @Test
    fun `남의 세션은 진행할 수 없다 - 존재를 숨기려 404`() {
        val sid = UUID.randomUUID()
        val victim = liveSession(sid, "joseph", "emotional")
        whenever(sessions.findById(sid)).thenReturn(Optional.of(victim))

        assertThatThrownBy {
            uc.execute(
                UUID.randomUUID(), sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "save_33"), null, "emotional"),
            )
        }
            .isInstanceOf(AppException::class.java)
            // 403 이 아니라 404 — 세션이 존재한다는 사실 자체를 알려주지 않는다.
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)

        // 거부 여부만 보면 부족하다. 소유권 검사는 *쓰기 이전* 이어야 하므로
        // 결정 영속·세션 저장이 아예 일어나지 않았음을 확인한다.
        verify(decisions, never()).save(any())
        verify(sessions, never()).save(any())
        assertThat(victim.decisions).isEmpty()
        assertThat(victim.sceneCountCompleted).isEqualTo(0.toShort())
    }

    @Test
    fun `userId 없는 레거시 세션은 아무도 진행할 수 없다`() {
        val sid = UUID.randomUUID()
        val legacy = GameSession.reconstitute(
            sid, null, null, "joseph", "emotional",
            LocalDateTime.now(), null, null, null, HashMap(), null, null,
            0.toShort(), null, null, null, null,
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(legacy))

        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    // ── 에러 분기 ──
    @Test
    fun `execute 세션없음 E_SESSION_NOT_FOUND`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.empty())
        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_NOT_FOUND)
    }

    @Test
    fun `execute 완료된세션 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(completedSession(sid, "joseph")))
        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute abandoned세션 E_SESSION_INVALID`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(abandonedSession(sid, "joseph")))
        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_SESSION_INVALID)
    }

    @Test
    fun `execute character 불일치 E_CHARACTER_UNKNOWN`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "moses", null)))
        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, null),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_CHARACTER_UNKNOWN)
    }

    @Test
    fun `execute mode 불일치 E_MODE_MISMATCH`() {
        val sid = UUID.randomUUID()
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", "rational")))
        assertThatThrownBy {
            uc.execute(
                owner, sid, Character.JOSEPH,
                DecideSceneUseCase.Input(2, mapOf("value" to "x"), null, "emotional"),
            )
        }
            .isInstanceOf(AppException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.E_MODE_MISMATCH)
    }

    // ── converges_to — 재고 선택은 씬을 넘기지 않는다 ──────────────────────────
    //
    // 이 네 개가 없으면 "수렴" 은 저작 문서에만 있는 말이 된다. 종전 엔진은 어떤 값을
    // 받아도 next 로 넘겼고, 그래서 solomon scene3 의 재고 구간이 통째로 건너뛰어졌다.

    /** solomon scene3 과 같은 중첩 `extras:` 블록 배치. */
    private fun judgmentScene(id: Int, next: Int?): Scenario.Scene = scene(
        id, next, false,
        mapOf(
            "extras" to mapOf(
                "options" to listOf(
                    mapOf("id" to "first_woman", "label" to "첫째 여인에게", "converges_to" to "sword_test"),
                    mapOf("id" to "second_woman", "label" to "둘째 여인에게", "converges_to" to "sword_test"),
                    mapOf("id" to "sword_test", "label" to "칼을 가져오라"),
                ),
                "reconsider_texts" to mapOf(
                    "first_woman" to "왕은 판결을 멈추고 다른 길을 생각했다.",
                    "second_woman" to "증거는 없고 두 주장만 남았다.",
                ),
                "default_choice" to "sword_test",
            ),
        ),
    )

    @Test
    fun `execute converges_to 선택은 같은 scene 에 머물고 재고 텍스트를 돌려준다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario("solomon", "해 아래", listOf(judgmentScene(3, 4), scene(4, 5, false, emptyMap())))
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "solomon", null)))
        whenever(loader.forCharacter(Character.SOLOMON)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.SOLOMON,
            DecideSceneUseCase.Input(3, mapOf("value" to "first_woman"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(3)
        assertThat(r.scenePayload["sceneId"]).isEqualTo(3)
        assertThat(r.responseText).isEqualTo("왕은 판결을 멈추고 다른 길을 생각했다.")
    }

    @Test
    fun `execute 수렴 대상 선택은 평소대로 다음 scene 으로 넘어간다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario("solomon", "해 아래", listOf(judgmentScene(3, 4), scene(4, 5, false, emptyMap())))
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "solomon", null)))
        whenever(loader.forCharacter(Character.SOLOMON)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.SOLOMON,
            DecideSceneUseCase.Input(3, mapOf("value" to "sword_test"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(4)
        assertThat(r.scenePayload["sceneId"]).isEqualTo(4)
    }

    @Test
    fun `execute converges_to 없는 인물은 종전대로 무조건 진행한다`() {
        val sid = UUID.randomUUID()
        val scenario = Scenario(
            "joseph", "곡식 7년",
            listOf(
                scene(2, 3, false, mapOf("options" to listOf(mapOf("id" to "save_33")))),
                scene(3, null, false, emptyMap()),
            ),
        )
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "joseph", null)))
        whenever(loader.forCharacter(Character.JOSEPH)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.JOSEPH,
            DecideSceneUseCase.Input(2, mapOf("value" to "save_33"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(3)
    }

    /** 재고 텍스트가 없어도 수렴은 성립한다 — 씬을 넘기지 않는 것이 본질이다. */
    @Test
    fun `execute 재고 텍스트가 없어도 씬은 넘어가지 않는다`() {
        val sid = UUID.randomUUID()
        val bare = scene(
            3, 4, false,
            mapOf(
                "extras" to mapOf(
                    "options" to listOf(
                        mapOf("id" to "a", "converges_to" to "b"),
                        mapOf("id" to "b"),
                    ),
                ),
            ),
        )
        val scenario = Scenario("solomon", "해 아래", listOf(bare, scene(4, null, false, emptyMap())))
        whenever(sessions.findById(sid)).thenReturn(Optional.of(liveSession(sid, "solomon", null)))
        whenever(loader.forCharacter(Character.SOLOMON)).thenReturn(scenario)

        val r = uc.execute(
            owner, sid, Character.SOLOMON,
            DecideSceneUseCase.Input(3, mapOf("value" to "a"), null, null),
        )

        assertThat(r.currentScene).isEqualTo(3)
        assertThat(r.responseText).isNull()
    }
}
