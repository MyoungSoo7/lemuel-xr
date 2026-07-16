package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.application.port.out.UserValueProfilePort
import github.lms.lemuel.xr.values.domain.CdrIndex
import github.lms.lemuel.xr.values.domain.UserValuePractice
import github.lms.lemuel.xr.values.domain.UserValueProfile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

/**
 * GetValueProfileUseCase 단위 테스트 — CDR Index 계산·tier 경계값·집계 로직을 촘촘히 커버.
 *
 * 순수 로직이므로 JUnit + Mockito 로 repository 만 stub 한다 (Spring 컨텍스트 없이 빠르게).
 */
class GetValueProfileUseCaseTest {

    private val profiles: UserValueProfilePort = mock()
    private val practices: UserValuePracticePort = mock()
    private val uc = GetValueProfileUseCase(profiles, practices)

    private val user: UUID = UUID.randomUUID()

    private fun practice(valueId: Int): UserValuePractice =
        UserValuePractice(
            null, user, valueId.toShort(), OffsetDateTime.now(),
            null, null, null, null,
        )

    // ─────────────────────── CdrIndex.fromPractices 경계 ───────────────────────

    @Test
    fun `computeCdrIndex 0건이면 0`() {
        assertThat(CdrIndex.fromPractices(0).value).isZero()
    }

    @Test
    fun `computeCdrIndex 49건이면 100`() {
        // 49 = 7 가치 × 7일 = 최대치.
        assertThat(CdrIndex.fromPractices(49).value).isEqualTo(100)
    }

    @Test
    fun `computeCdrIndex 49 초과여도 100 상한`() {
        assertThat(CdrIndex.fromPractices(100).value).isEqualTo(100)
    }

    @Test
    fun `computeCdrIndex 중간값 비례`() {
        // 24/49*100 = 48 (정수 나눗셈).
        assertThat(CdrIndex.fromPractices(24).value).isEqualTo(48)
        // 25/49*100 = 51.
        assertThat(CdrIndex.fromPractices(25).value).isEqualTo(51)
    }

    @Test
    fun `computeCdrIndex 1건이면 2`() {
        // 1/49*100 = 2 (정수).
        assertThat(CdrIndex.fromPractices(1).value).isEqualTo(2)
    }

    // ─────────────────────── tier 경계 ───────────────────────

    @Test
    fun `tier 80이상은 VERY_READY`() {
        // 40/49*100 = 81 (>= 80).
        assertThat(CdrIndex.fromPractices(40).tier).isEqualTo("VERY_READY")
        assertThat(CdrIndex.fromPractices(49).tier).isEqualTo("VERY_READY")
    }

    @Test
    fun `tier 50이상 80미만은 NORMAL`() {
        // 25/49*100 = 51, 38/49*100 = 77.
        assertThat(CdrIndex.fromPractices(25).tier).isEqualTo("NORMAL")
        assertThat(CdrIndex.fromPractices(38).tier).isEqualTo("NORMAL")
    }

    @Test
    fun `tier 50미만은 BUILD_UP`() {
        // 24/49*100 = 48, 0/49 = 0.
        assertThat(CdrIndex.fromPractices(24).tier).isEqualTo("BUILD_UP")
        assertThat(CdrIndex.fromPractices(0).tier).isEqualTo("BUILD_UP")
    }

    // ─────────────────────── execute() 통합 로직 ───────────────────────

    @Test
    fun `execute 프로파일 없으면 빈 valuesJson 그리고 실천0건이면 BUILD_UP`() {
        whenever(profiles.findByUserId(user)).thenReturn(Optional.empty())
        whenever(practices.findRecent(eq(user), any())).thenReturn(emptyList())

        val r = uc.execute(user)

        assertThat(r.valuesJson).isEmpty()
        assertThat(r.totalPractices7d).isZero()
        assertThat(r.countByValue).isEmpty()
        assertThat(r.cdrIndex).isZero()
        assertThat(r.tier).isEqualTo("BUILD_UP")
    }

    @Test
    fun `execute 프로파일 있으면 valuesJson 전달`() {
        val profile = UserValueProfile(
            UUID.randomUUID(), user,
            mapOf("1" to mapOf("title" to "흔들리지 않는 결정")),
            OffsetDateTime.now(), OffsetDateTime.now(),
        )
        whenever(profiles.findByUserId(user)).thenReturn(Optional.of(profile))
        whenever(practices.findRecent(eq(user), any())).thenReturn(emptyList())

        val r = uc.execute(user)

        assertThat(r.valuesJson).containsKey("1")
    }

    @Test
    fun `execute value별 카운트 집계`() {
        val recent = listOf(practice(1), practice(1), practice(3), practice(7))
        whenever(profiles.findByUserId(user)).thenReturn(Optional.empty())
        whenever(practices.findRecent(eq(user), any())).thenReturn(recent)

        val r = uc.execute(user)

        assertThat(r.totalPractices7d).isEqualTo(4)
        assertThat(r.countByValue)
            .containsEntry("1", 2)
            .containsEntry("3", 1)
            .containsEntry("7", 1)
        // 4건 → 4/49*100 = 8 → BUILD_UP.
        assertThat(r.cdrIndex).isEqualTo(8)
        assertThat(r.tier).isEqualTo("BUILD_UP")
    }

    @Test
    fun `execute 49건이면 VERY_READY`() {
        val recent = (0 until 49).map { i -> practice(1 + (i % 7)) }
        whenever(profiles.findByUserId(user)).thenReturn(Optional.empty())
        whenever(practices.findRecent(eq(user), any())).thenReturn(recent)

        val r = uc.execute(user)

        assertThat(r.cdrIndex).isEqualTo(100)
        assertThat(r.tier).isEqualTo("VERY_READY")
    }
}
