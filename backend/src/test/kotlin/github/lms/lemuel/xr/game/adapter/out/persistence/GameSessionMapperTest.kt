package github.lms.lemuel.xr.game.adapter.out.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * GameSessionMapper 단위 테스트 — toDomain/toEntity 왕복 매핑을 모든 필드에 대해 검증.
 */
class GameSessionMapperTest {

    private val id: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val user: UUID = UUID.fromString("77777777-7777-7777-7777-777777777777")
    private val appSession: UUID = UUID.fromString("88888888-8888-8888-8888-888888888888")
    private val started: LocalDateTime = LocalDateTime.of(2026, 7, 7, 10, 0)
    private val completed: LocalDateTime = LocalDateTime.of(2026, 7, 7, 10, 20)
    private val abandoned: LocalDateTime = LocalDateTime.of(2026, 7, 7, 10, 25)
    private val decisions: MutableMap<String, Any?> = mutableMapOf("scene3" to "farmer")
    private val capabilities: Map<String, Any?> = mapOf("webxr" to true)

    private fun fullEntity(): GameSessionJpaEntity =
        GameSessionJpaEntity().apply {
            id = this@GameSessionMapperTest.id
            userId = user
            appSessionId = appSession
            character = "joseph"
            chosenDimension = "emotional"
            startedAt = started
            completedAt = completed
            abandonedAt = abandoned
            triggeredByEmotionLogId = 101L
            decisions = this@GameSessionMapperTest.decisions
            finalOutcome = "farmer_first"
            closingMessage = "well done"
            sceneCountCompleted = 4.toShort()
            durationSeconds = 1200
            deviceType = "quest3"
            capabilities = this@GameSessionMapperTest.capabilities
            assetsManifestVersion = "v2"
        }

    @Test
    fun `toDomain 은 엔티티 모든필드를 도메인으로 복원한다`() {
        val d = GameSessionMapper.toDomain(fullEntity())

        assertThat(d.id).isEqualTo(id)
        assertThat(d.userId).isEqualTo(user)
        assertThat(d.appSessionId).isEqualTo(appSession)
        assertThat(d.character).isEqualTo("joseph")
        assertThat(d.chosenDimension).isEqualTo("emotional")
        assertThat(d.startedAt).isEqualTo(started)
        assertThat(d.completedAt).isEqualTo(completed)
        assertThat(d.abandonedAt).isEqualTo(abandoned)
        assertThat(d.triggeredByEmotionLogId).isEqualTo(101L)
        assertThat(d.decisions).isEqualTo(decisions)
        assertThat(d.finalOutcome).isEqualTo("farmer_first")
        assertThat(d.closingMessage).isEqualTo("well done")
        assertThat(d.sceneCountCompleted).isEqualTo(4.toShort())
        assertThat(d.durationSeconds).isEqualTo(1200)
        assertThat(d.deviceType).isEqualTo("quest3")
        assertThat(d.capabilities).isEqualTo(capabilities)
        assertThat(d.assetsManifestVersion).isEqualTo("v2")
    }

    @Test
    fun `toEntity 는 도메인 모든필드를 엔티티로 매핑한다`() {
        val domain = GameSessionMapper.toDomain(fullEntity())

        val e = GameSessionMapper.toEntity(domain)

        assertThat(e.id).isEqualTo(id)
        assertThat(e.userId).isEqualTo(user)
        assertThat(e.appSessionId).isEqualTo(appSession)
        assertThat(e.character).isEqualTo("joseph")
        assertThat(e.chosenDimension).isEqualTo("emotional")
        assertThat(e.startedAt).isEqualTo(started)
        assertThat(e.completedAt).isEqualTo(completed)
        assertThat(e.abandonedAt).isEqualTo(abandoned)
        assertThat(e.triggeredByEmotionLogId).isEqualTo(101L)
        assertThat(e.decisions).isEqualTo(decisions)
        assertThat(e.finalOutcome).isEqualTo("farmer_first")
        assertThat(e.closingMessage).isEqualTo("well done")
        assertThat(e.sceneCountCompleted).isEqualTo(4.toShort())
        assertThat(e.durationSeconds).isEqualTo(1200)
        assertThat(e.deviceType).isEqualTo("quest3")
        assertThat(e.capabilities).isEqualTo(capabilities)
        assertThat(e.assetsManifestVersion).isEqualTo("v2")
    }
}
