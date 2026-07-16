package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.values.application.port.out.UserValueProfilePort
import github.lms.lemuel.xr.values.domain.UserValueProfile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

/**
 * UpdateValueProfileUseCase 단위 테스트 — 신규 생성·부분 갱신·null-value 삭제 분기 커버.
 */
class UpdateValueProfileUseCaseTest {

    private val profiles: UserValueProfilePort = mock()
    private val uc = UpdateValueProfileUseCase(profiles)

    private val user: UUID = UUID.randomUUID()

    @Test
    fun `프로파일 없으면 신규 생성`() {
        whenever(profiles.findByUserId(user)).thenReturn(Optional.empty())
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val patch = mutableMapOf<String, Any?>("1" to mapOf("title" to "흔들리지 않는 결정"))
        val p = uc.execute(user, patch)

        assertThat(p.id).isNotNull()
        assertThat(p.userId).isEqualTo(user)
        assertThat(p.valuesJson).containsKey("1")
        assertThat(p.startedAt).isNotNull()
        assertThat(p.lastUpdatedAt).isNotNull()
    }

    @Test
    fun `기존 프로파일 부분 갱신`() {
        val current = mutableMapOf<String, Any?>(
            "1" to mapOf("title" to "old"),
            "2" to mapOf("title" to "keep"),
        )
        val existing = UserValueProfile(
            UUID.randomUUID(), user, current, OffsetDateTime.now(), OffsetDateTime.now(),
        )
        whenever(profiles.findByUserId(user)).thenReturn(Optional.of(existing))
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val patch = mutableMapOf<String, Any?>(
            "1" to mapOf("title" to "new"), // 덮어씀
            "3" to mapOf("title" to "added"), // 추가
        )
        val p = uc.execute(user, patch)

        assertThat(p.valuesJson).containsKeys("1", "2", "3")
        assertThat((p.valuesJson["1"] as Map<*, *>)["title"]).isEqualTo("new")
        assertThat((p.valuesJson["2"] as Map<*, *>)["title"]).isEqualTo("keep")
    }

    @Test
    fun `null value 는 키 삭제`() {
        val current = mutableMapOf<String, Any?>(
            "1" to mapOf("title" to "removeme"),
            "2" to mapOf("title" to "keep"),
        )
        val existing = UserValueProfile(
            UUID.randomUUID(), user, current, OffsetDateTime.now(), OffsetDateTime.now(),
        )
        whenever(profiles.findByUserId(user)).thenReturn(Optional.of(existing))
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val patch = mutableMapOf<String, Any?>("1" to null) // 삭제
        val p = uc.execute(user, patch)

        assertThat(p.valuesJson).doesNotContainKey("1").containsKey("2")
    }

    @Test
    fun `기존 valuesJson null이면 빈맵으로 시작`() {
        val existing = UserValueProfile.of(
            UUID.randomUUID(), user, null, OffsetDateTime.now(), OffsetDateTime.now(),
        )
        whenever(profiles.findByUserId(user)).thenReturn(Optional.of(existing))
        whenever(profiles.save(any())).thenAnswer { it.getArgument(0) }

        val patch = mutableMapOf<String, Any?>("5" to mapOf("title" to "fresh"))
        val p = uc.execute(user, patch)

        assertThat(p.valuesJson).containsOnlyKeys("5")
    }
}
