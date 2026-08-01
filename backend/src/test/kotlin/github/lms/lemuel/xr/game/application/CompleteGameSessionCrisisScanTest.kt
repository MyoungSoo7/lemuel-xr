package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.GameSession
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * 게임 완료 시 사용자가 남기는 `closingMessage` 의 위기 신호 스캔.
 *
 * 이 경로가 비어 있던 것이 문제였다. 일기(JournalGuidance)·전도서(EcclesiastesView)·
 * 실천(PracticeReflection) 세 자유텍스트 경로에는 모두 CrisisKeywordScanner 가 걸려
 * 있는데, 게임 종료 메시지만 스캔 없이 그대로 DB 에 저장됐다.
 *
 * 하필 여기가 위험하다 — 미션의 감정적 정점이고, "요셉의 13년"이나 "엘리야의 로뎀나무"
 * 같은 절망 서사를 막 통과한 직후 사용자가 *자기 이야기* 를 쓰는 자리다.
 *
 * 정책: 저장은 막지 않는다. 사용자가 쓴 것을 삼켜버리면 오히려 해롭고, 이 값은 이미
 * 사용자 자신의 기록이다. 대신 위기 신호를 감지하면 알럿을 남겨 위기 자원 안내가
 * 뜨도록 한다 — 다른 세 경로와 같은 취급이다.
 */
class CompleteGameSessionCrisisScanTest {

    private val scanner = CrisisKeywordScanner(
        "(?<suicideIntent>자살|죽고\\s?싶|죽어\\s?버|뛰어내리)|(?<selfHarm>자해)",
    )
    private val sessions: GameSessionPort = mock()
    private val scenarios: ScenarioYamlLoader = mock()
    private val safetyAlerts: RecordSafetyAlertUseCase = mock()

    private val useCase =
        CompleteGameSessionUseCase(sessions, scenarios, scanner, safetyAlerts, SafetyGateFixtures.sanitizer())

    private fun session(): GameSession {
        val id = UUID.randomUUID()
        val s = GameSession.reconstitute(
            id, null, null, "joseph", null,
            LocalDateTime.now(), null, null, null, null, null, null,
            0.toShort(), null, null, null, null,
        )
        whenever(sessions.findById(id)).thenReturn(Optional.of(s))
        whenever(sessions.save(any())).thenAnswer { inv -> inv.arguments[0] }
        return s
    }

    @Test
    fun `평범한 종료 메시지는 알럿을 만들지 않는다`() {
        val s = session()

        useCase.execute(s.id!!, "completed", "요셉처럼 저도 기다려보려 합니다.")

        verify(safetyAlerts, never()).execute(anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `위기 신호가 담긴 종료 메시지는 알럿을 남긴다`() {
        val s = session()

        useCase.execute(s.id!!, "completed", "다 끝내고 죽고 싶어요")

        verify(safetyAlerts).execute(
            anyOrNull(), anyOrNull(), eq("game_closing_message"),
            argThat<CrisisKeywordScanner.ScanResult> { matchedPattern == "suicide_intent" },
        )
    }

    @Test
    fun `위기 신호가 있어도 세션 완료 자체는 막지 않는다`() {
        val s = session()

        val r = useCase.execute(s.id!!, "completed", "자해를 했어요")

        // 사용자가 쓴 기록을 삼키지 않는다. 완료는 정상 처리하되 알럿만 추가한다.
        assertThat(r.sessionId).isEqualTo(s.id)
        assertThat(r.completedAt).isNotNull()
        verify(sessions).save(any())
    }

    @Test
    fun `종료 메시지가 없으면 스캔하지 않는다`() {
        val s = session()

        useCase.execute(s.id!!, "completed", null)

        verify(safetyAlerts, never()).execute(anyOrNull(), anyOrNull(), any(), any())
    }
}
