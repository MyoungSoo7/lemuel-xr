package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.values.adapter.out.metrics.MicrometerPracticeMetricsAdapter
import github.lms.lemuel.xr.values.application.port.out.UserValuePracticePort
import github.lms.lemuel.xr.values.domain.UserValuePractice
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * RecordPracticeUseCase 단위 테스트 — valueId 경계 검증·저장·메트릭 발행 분기 커버.
 *
 * MeterRegistry 는 실제 [SimpleMeterRegistry] 를 써서 Counter 발행 결과를 검증한다.
 */
class RecordPracticeUseCaseTest {

    private val practices: UserValuePracticePort = mock()
    private lateinit var meter: SimpleMeterRegistry
    private lateinit var uc: RecordPracticeUseCase

    private val user: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        meter = SimpleMeterRegistry()
        uc = RecordPracticeUseCase(practices, MicrometerPracticeMetricsAdapter(meter))
        // 어댑터는 저장 후 (id 채워진) 도메인을 되돌려준다 — 여기선 인자를 그대로 echo.
        whenever(practices.save(any())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `valueId 0이면 E_VALIDATION`() {
        assertThatThrownBy { uc.execute(user, 0, null, null, null, null) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e -> assertThat((e as AppException).code).isEqualTo(ErrorCode.E_VALIDATION) })
    }

    @Test
    fun `valueId 8이면 E_VALIDATION`() {
        assertThatThrownBy { uc.execute(user, 8, null, null, null, null) }
            .isInstanceOf(AppException::class.java)
            .satisfies({ e -> assertThat((e as AppException).code).isEqualTo(ErrorCode.E_VALIDATION) })
    }

    @Test
    fun `유효한 실천 저장 그리고 필드매핑`() {
        val gameSession = UUID.randomUUID()
        val saved = uc.execute(user, 3, 120, "감사한 하루", "joseph", gameSession)

        assertThat(saved.userId).isEqualTo(user)
        assertThat(saved.valueId).isEqualTo(3.toShort())
        assertThat(saved.durationSec).isEqualTo(120)
        assertThat(saved.note).isEqualTo("감사한 하루")
        assertThat(saved.linkedCharacter).isEqualTo("joseph")
        assertThat(saved.linkedGameSession).isEqualTo(gameSession)
        assertThat(saved.practicedAt).isNotNull()

        val captor = argumentCaptor<UserValuePractice>()
        verify(practices).save(captor.capture())
        assertThat(captor.firstValue.valueId).isEqualTo(3.toShort())

        // Counter 발행 — linked_character 태그 = "joseph".
        val counter = meter.find("values.practice")
            .tag("value_id", "3").tag("linked_character", "joseph").counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `linkedCharacter null이면 none 태그`() {
        uc.execute(user, 1, null, null, null, null)

        val counter = meter.find("values.practice")
            .tag("value_id", "1").tag("linked_character", "none").counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `경계 valueId 1과 7은 허용`() {
        assertThat(uc.execute(user, 1, null, null, null, null).valueId).isEqualTo(1.toShort())
        assertThat(uc.execute(user, 7, null, null, null, null).valueId).isEqualTo(7.toShort())
    }
}
