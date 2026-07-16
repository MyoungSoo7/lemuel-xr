package github.lms.lemuel.xr.recovery.adapter.`in`.web

import github.lms.lemuel.xr.recovery.application.GetRecoveryMetricsUseCase
import github.lms.lemuel.xr.recovery.application.GetRecoveryMetricsUseCase.MetricDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * RecoveryController 단위 테스트 — RequestContext 로 인증 userId 주입 후 use-case 결과 → 응답 매핑 검증.
 */
class RecoveryControllerTest {

    private val getRecoveryMetrics: GetRecoveryMetricsUseCase = mock()

    @AfterEach
    fun clearContext() {
        RequestContextHolder.resetRequestAttributes()
    }

    private fun bindUser(userId: UUID) {
        val req = MockHttpServletRequest()
        req.setAttribute("xr.userId", userId)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
    }

    @Test
    fun `recent metric DTO매핑`() {
        val uid = UUID.randomUUID()
        bindUser(uid)
        val dto = MetricDto(LocalDate.of(2026, 7, 1), 4, BigDecimal("3.2"), 120, 2, 0)
        whenever(getRecoveryMetrics.execute(eq(uid), eq(30))).thenReturn(listOf(dto))

        val controller = RecoveryController(getRecoveryMetrics)
        val response = controller.recent(30)

        val items = response.body!!.items
        assertThat(items).hasSize(1)
        val result = items[0]
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(result.emotionDiversity).isEqualTo(4)
        assertThat(result.avgIntensity).isEqualByComparingTo("3.2")
        assertThat(result.diaryWords).isEqualTo(120)
        assertThat(result.missionsCompleted).isEqualTo(2)
        assertThat(result.riskSignals).isZero()
    }

    @Test
    fun `recent 빈결과 빈리스트`() {
        val uid = UUID.randomUUID()
        bindUser(uid)
        whenever(getRecoveryMetrics.execute(eq(uid), eq(7))).thenReturn(listOf())

        val controller = RecoveryController(getRecoveryMetrics)
        val response = controller.recent(7)

        assertThat(response.body!!.items).isEmpty()
    }
}
