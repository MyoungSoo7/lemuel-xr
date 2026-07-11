package github.lms.lemuel.xr.values.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RecordPracticeUseCase 단위 테스트 — valueId 경계 검증·저장·메트릭 발행 분기 커버.
 *
 * <p>MeterRegistry 는 실제 {@link SimpleMeterRegistry} 를 써서 Counter 발행 결과를 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RecordPracticeUseCaseTest {

    @Mock UserValuePracticeRepository practices;
    private SimpleMeterRegistry meter;
    private RecordPracticeUseCase uc;

    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meter = new SimpleMeterRegistry();
        uc = new RecordPracticeUseCase(practices, meter);
    }

    @Test
    void valueId_0이면_E_VALIDATION() {
        assertThatThrownBy(() -> uc.execute(user, 0, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode()).isEqualTo(ErrorCode.E_VALIDATION));
    }

    @Test
    void valueId_8이면_E_VALIDATION() {
        assertThatThrownBy(() -> uc.execute(user, 8, null, null, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode()).isEqualTo(ErrorCode.E_VALIDATION));
    }

    @Test
    void 유효한_실천_저장_그리고_필드매핑() {
        UUID gameSession = UUID.randomUUID();
        var saved = uc.execute(user, 3, 120, "감사한 하루", "joseph", gameSession);

        assertThat(saved.getUserId()).isEqualTo(user);
        assertThat(saved.getValueId()).isEqualTo((short) 3);
        assertThat(saved.getDurationSec()).isEqualTo(120);
        assertThat(saved.getNote()).isEqualTo("감사한 하루");
        assertThat(saved.getLinkedCharacter()).isEqualTo("joseph");
        assertThat(saved.getLinkedGameSession()).isEqualTo(gameSession);
        assertThat(saved.getPracticedAt()).isNotNull();

        ArgumentCaptor<UserValuePracticeJpaEntity> captor =
                ArgumentCaptor.forClass(UserValuePracticeJpaEntity.class);
        verify(practices).save(captor.capture());
        assertThat(captor.getValue().getValueId()).isEqualTo((short) 3);

        // Counter 발행 — linked_character 태그 = "joseph".
        var counter = meter.find("values.practice")
                .tag("value_id", "3").tag("linked_character", "joseph").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void linkedCharacter_null이면_none_태그() {
        uc.execute(user, 1, null, null, null, null);

        var counter = meter.find("values.practice")
                .tag("value_id", "1").tag("linked_character", "none").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void 경계_valueId_1과_7은_허용() {
        assertThat(uc.execute(user, 1, null, null, null, null).getValueId()).isEqualTo((short) 1);
        assertThat(uc.execute(user, 7, null, null, null, null).getValueId()).isEqualTo((short) 7);
    }
}
