package github.lms.lemuel.xr.game.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GameSessionTest {

    @Test
    fun `start 는 id userId character startedAt 초기화`() {
        val uid = UUID.randomUUID()
        val s = GameSession.start(uid, "joseph")
        assertThat(s.id).isNotNull()
        assertThat(s.userId).isEqualTo(uid)
        assertThat(s.character).isEqualTo("joseph")
        assertThat(s.startedAt).isNotNull()
        assertThat(s.completedAt).isNull()
        assertThat(s.decisions).isEmpty()
    }

    @Test
    fun `recordDecision string`() {
        val s = GameSession.start(UUID.randomUUID(), "moses")
        s.recordDecision(2, "save_33")
        assertThat(s.decisions).containsEntry("scene2", "save_33")
    }

    @Test
    fun `recordDecision object`() {
        val s = GameSession.start(UUID.randomUUID(), "david")
        val payload: Map<String, Any?> = mapOf("priority" to "farmer")
        s.recordDecision(3, payload as Any?)
        assertThat(s.decisions).containsEntry("scene3", payload)
    }

    @Test
    fun `complete 는 finalOutcome completedAt 설정`() {
        val s = GameSession.start(UUID.randomUUID(), "jesus")
        s.complete("immigrant_first")
        assertThat(s.finalOutcome).isEqualTo("immigrant_first")
        assertThat(s.completedAt).isNotNull()
    }
}
