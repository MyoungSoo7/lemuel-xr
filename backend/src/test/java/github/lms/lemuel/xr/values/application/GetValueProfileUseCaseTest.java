package github.lms.lemuel.xr.values.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GetValueProfileUseCase 단위 테스트 — CDR Index 계산·tier 경계값·집계 로직을 촘촘히 커버.
 *
 * <p>순수 로직이므로 JUnit + Mockito 로 repository 만 stub 한다 (Spring 컨텍스트 없이 빠르게).</p>
 */
@ExtendWith(MockitoExtension.class)
class GetValueProfileUseCaseTest {

    @Mock UserValueProfileRepository profiles;
    @Mock UserValuePracticeRepository practices;
    @InjectMocks GetValueProfileUseCase uc;

    private final UUID user = UUID.randomUUID();

    private UserValuePracticeJpaEntity practice(int valueId) {
        var p = new UserValuePracticeJpaEntity();
        p.setValueId((short) valueId);
        p.setPracticedAt(OffsetDateTime.now());
        return p;
    }

    // ─────────────────────── computeCdrIndex 경계 ───────────────────────

    @Test
    void computeCdrIndex_0건이면_0() {
        assertThat(GetValueProfileUseCase.computeCdrIndex(0)).isZero();
    }

    @Test
    void computeCdrIndex_49건이면_100() {
        // 49 = 7 가치 × 7일 = 최대치.
        assertThat(GetValueProfileUseCase.computeCdrIndex(49)).isEqualTo(100);
    }

    @Test
    void computeCdrIndex_49_초과여도_100_상한() {
        assertThat(GetValueProfileUseCase.computeCdrIndex(100)).isEqualTo(100);
    }

    @Test
    void computeCdrIndex_중간값_비례() {
        // 24/49*100 = 48 (정수 나눗셈).
        assertThat(GetValueProfileUseCase.computeCdrIndex(24)).isEqualTo(48);
        // 25/49*100 = 51.
        assertThat(GetValueProfileUseCase.computeCdrIndex(25)).isEqualTo(51);
    }

    @Test
    void computeCdrIndex_1건이면_2() {
        // 1/49*100 = 2 (정수).
        assertThat(GetValueProfileUseCase.computeCdrIndex(1)).isEqualTo(2);
    }

    // ─────────────────────── tierOf 경계 ───────────────────────

    @Test
    void tierOf_80이상은_VERY_READY() {
        assertThat(GetValueProfileUseCase.tierOf(80)).isEqualTo("VERY_READY");
        assertThat(GetValueProfileUseCase.tierOf(100)).isEqualTo("VERY_READY");
    }

    @Test
    void tierOf_50이상_80미만은_NORMAL() {
        assertThat(GetValueProfileUseCase.tierOf(50)).isEqualTo("NORMAL");
        assertThat(GetValueProfileUseCase.tierOf(79)).isEqualTo("NORMAL");
    }

    @Test
    void tierOf_50미만은_BUILD_UP() {
        assertThat(GetValueProfileUseCase.tierOf(49)).isEqualTo("BUILD_UP");
        assertThat(GetValueProfileUseCase.tierOf(0)).isEqualTo("BUILD_UP");
    }

    // ─────────────────────── execute() 통합 로직 ───────────────────────

    @Test
    void execute_프로파일_없으면_빈_valuesJson_그리고_실천0건이면_BUILD_UP() {
        when(profiles.findByUserId(user)).thenReturn(Optional.empty());
        when(practices.findRecent(eq(user), any())).thenReturn(new ArrayList<>());

        var r = uc.execute(user);

        assertThat(r.valuesJson()).isEmpty();
        assertThat(r.totalPractices7d()).isZero();
        assertThat(r.countByValue()).isEmpty();
        assertThat(r.cdrIndex()).isZero();
        assertThat(r.tier()).isEqualTo("BUILD_UP");
    }

    @Test
    void execute_프로파일_있으면_valuesJson_전달() {
        var profile = new UserValueProfileJpaEntity();
        profile.setValuesJson(Map.of("1", Map.of("title", "흔들리지 않는 결정")));
        when(profiles.findByUserId(user)).thenReturn(Optional.of(profile));
        when(practices.findRecent(eq(user), any())).thenReturn(new ArrayList<>());

        var r = uc.execute(user);

        assertThat(r.valuesJson()).containsKey("1");
    }

    @Test
    void execute_value별_카운트_집계() {
        List<UserValuePracticeJpaEntity> recent = List.of(
                practice(1), practice(1), practice(3), practice(7));
        when(profiles.findByUserId(user)).thenReturn(Optional.empty());
        when(practices.findRecent(eq(user), any())).thenReturn(recent);

        var r = uc.execute(user);

        assertThat(r.totalPractices7d()).isEqualTo(4);
        assertThat(r.countByValue())
                .containsEntry("1", 2)
                .containsEntry("3", 1)
                .containsEntry("7", 1);
        // 4건 → 4/49*100 = 8 → BUILD_UP.
        assertThat(r.cdrIndex()).isEqualTo(8);
        assertThat(r.tier()).isEqualTo("BUILD_UP");
    }

    @Test
    void execute_49건이면_VERY_READY() {
        List<UserValuePracticeJpaEntity> recent = new ArrayList<>();
        for (int i = 0; i < 49; i++) recent.add(practice(1 + (i % 7)));
        when(profiles.findByUserId(user)).thenReturn(Optional.empty());
        when(practices.findRecent(eq(user), any())).thenReturn(recent);

        var r = uc.execute(user);

        assertThat(r.cdrIndex()).isEqualTo(100);
        assertThat(r.tier()).isEqualTo("VERY_READY");
    }
}
