package github.lms.lemuel.xr.values.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UpdateValueProfileUseCase 단위 테스트 — 신규 생성·부분 갱신·null-value 삭제 분기 커버.
 */
@ExtendWith(MockitoExtension.class)
class UpdateValueProfileUseCaseTest {

    @Mock UserValueProfileRepository profiles;
    @InjectMocks UpdateValueProfileUseCase uc;

    private final UUID user = UUID.randomUUID();

    @Test
    void 프로파일_없으면_신규_생성() {
        when(profiles.findByUserId(user)).thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var patch = new HashMap<String, Object>();
        patch.put("1", Map.of("title", "흔들리지 않는 결정"));
        var p = uc.execute(user, patch);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getUserId()).isEqualTo(user);
        assertThat(p.getValuesJson()).containsKey("1");
        assertThat(p.getStartedAt()).isNotNull();
        assertThat(p.getLastUpdatedAt()).isNotNull();
    }

    @Test
    void 기존_프로파일_부분_갱신() {
        var existing = new UserValueProfileJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        var current = new HashMap<String, Object>();
        current.put("1", Map.of("title", "old"));
        current.put("2", Map.of("title", "keep"));
        existing.setValuesJson(current);
        when(profiles.findByUserId(user)).thenReturn(Optional.of(existing));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var patch = new HashMap<String, Object>();
        patch.put("1", Map.of("title", "new"));   // 덮어씀
        patch.put("3", Map.of("title", "added"));  // 추가
        var p = uc.execute(user, patch);

        assertThat(p.getValuesJson()).containsKeys("1", "2", "3");
        assertThat(((Map<?, ?>) p.getValuesJson().get("1")).get("title")).isEqualTo("new");
        assertThat(((Map<?, ?>) p.getValuesJson().get("2")).get("title")).isEqualTo("keep");
    }

    @Test
    void null_value_는_키_삭제() {
        var existing = new UserValueProfileJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        var current = new HashMap<String, Object>();
        current.put("1", Map.of("title", "removeme"));
        current.put("2", Map.of("title", "keep"));
        existing.setValuesJson(current);
        when(profiles.findByUserId(user)).thenReturn(Optional.of(existing));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var patch = new HashMap<String, Object>();
        patch.put("1", null);   // 삭제
        var p = uc.execute(user, patch);

        assertThat(p.getValuesJson()).doesNotContainKey("1").containsKey("2");
    }

    @Test
    void 기존_valuesJson_null이면_빈맵으로_시작() {
        var existing = new UserValueProfileJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user);
        existing.setValuesJson(null);
        when(profiles.findByUserId(user)).thenReturn(Optional.of(existing));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var patch = new HashMap<String, Object>();
        patch.put("5", Map.of("title", "fresh"));
        var p = uc.execute(user, patch);

        assertThat(p.getValuesJson()).containsOnlyKeys("5");
    }
}
