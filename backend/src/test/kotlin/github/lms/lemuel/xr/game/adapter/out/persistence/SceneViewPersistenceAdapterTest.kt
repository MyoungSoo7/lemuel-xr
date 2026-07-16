package github.lms.lemuel.xr.game.adapter.out.persistence

import github.lms.lemuel.xr.game.domain.SceneView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

/**
 * SceneViewPersistenceAdapter 단위 테스트 — 엔티티→도메인 매핑 커버.
 */
class SceneViewPersistenceAdapterTest {

    private val repository: SceneViewJpaRepository = mock()
    private val adapter = SceneViewPersistenceAdapter(repository)

    private val session: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val entered: LocalDateTime = LocalDateTime.of(2026, 7, 6, 9, 0)
    private val exited: LocalDateTime = LocalDateTime.of(2026, 7, 6, 9, 5)

    private fun fullEntity(): SceneViewJpaEntity =
        SceneViewJpaEntity().apply {
            id = 21L
            gameSessionId = session
            sceneNumber = 2.toShort()
            enteredAt = entered
            exitedAt = exited
            durationSeconds = 300
            exitReason = "advanced"
            skippedSilence = true
        }

    @Test
    fun `findByGameSessionIdOrderByEnteredAt 는 리스트를 도메인으로 매핑한다`() {
        whenever(repository.findByGameSessionIdOrderByEnteredAt(session))
            .thenReturn(listOf(fullEntity()))

        val result = adapter.findByGameSessionIdOrderByEnteredAt(session)

        assertThat(result).hasSize(1)
        val v: SceneView = result[0]
        assertThat(v.id).isEqualTo(21L)
        assertThat(v.gameSessionId).isEqualTo(session)
        assertThat(v.sceneNumber).isEqualTo(2.toShort())
        assertThat(v.enteredAt).isEqualTo(entered)
        assertThat(v.exitedAt).isEqualTo(exited)
        assertThat(v.durationSeconds).isEqualTo(300)
        assertThat(v.exitReason).isEqualTo("advanced")
        assertThat(v.skippedSilence).isTrue()
    }
}
