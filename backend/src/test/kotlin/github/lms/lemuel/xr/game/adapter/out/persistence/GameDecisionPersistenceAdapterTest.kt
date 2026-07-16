package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.domain.GameDecision
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

/**
 * GameDecisionPersistenceAdapter 단위 테스트 — 도메인↔엔티티 왕복 매핑 커버.
 */
class GameDecisionPersistenceAdapterTest {

    private val repository: GameDecisionJpaRepository = mock()
    private val adapter = GameDecisionPersistenceAdapter(repository)

    private val session: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val decided: LocalDateTime = LocalDateTime.of(2026, 7, 5, 14, 0)
    private val decision: Map<String, Any?> = mapOf("choice" to "left")
    private val meta: Map<String, Any?> = mapOf("ms" to 320)

    private fun fullEntity(): GameDecisionJpaEntity =
        GameDecisionJpaEntity().apply {
            id = 11L
            gameSessionId = session
            sceneNumber = 3.toShort()
            sceneName = "crossroads"
            decision = this@GameDecisionPersistenceAdapterTest.decision
            interactionMeta = meta
            decidedAt = decided
        }

    private fun fullDomain(): GameDecision =
        GameDecision(11L, session, 3.toShort(), "crossroads", decision, meta, decided)

    @Test
    fun `save 는 toEntity 변환후 저장결과를 도메인으로 반환한다`() {
        whenever(repository.save(any())).thenReturn(fullEntity())

        val result = adapter.save(fullDomain())

        val captor = argumentCaptor<GameDecisionJpaEntity>()
        verify(repository).save(captor.capture())
        val persisted = captor.firstValue
        assertThat(persisted.id).isEqualTo(11L)
        assertThat(persisted.gameSessionId).isEqualTo(session)
        assertThat(persisted.sceneNumber).isEqualTo(3.toShort())
        assertThat(persisted.sceneName).isEqualTo("crossroads")
        assertThat(persisted.decision).isEqualTo(decision)
        assertThat(persisted.interactionMeta).isEqualTo(meta)
        assertThat(persisted.decidedAt).isEqualTo(decided)

        assertThat(result).isEqualTo(fullDomain())
    }
}
