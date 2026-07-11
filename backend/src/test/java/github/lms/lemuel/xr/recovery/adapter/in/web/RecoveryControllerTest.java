package github.lms.lemuel.xr.recovery.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.recovery.adapter.out.persistence.RecoveryMetricJpaEntity;
import github.lms.lemuel.xr.recovery.adapter.out.persistence.RecoveryMetricRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RecoveryController 단위 테스트 — RequestContext 로 인증 userId 주입 후 metric → DTO 매핑 검증.
 */
@ExtendWith(MockitoExtension.class)
class RecoveryControllerTest {

    @Mock RecoveryMetricRepository metrics;

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindUser(UUID userId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("xr.userId", userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void recent_metric_DTO매핑() {
        UUID uid = UUID.randomUUID();
        bindUser(uid);
        RecoveryMetricJpaEntity m = new RecoveryMetricJpaEntity();
        m.setMetricDate(LocalDate.of(2026, 7, 1));
        m.setEmotionDiversityCount(4);
        m.setAvgIntensity(new BigDecimal("3.2"));
        m.setDiaryWordCount(120);
        m.setMissionCompletedCount(2);
        m.setRiskSignalCount(0);
        when(metrics.findRecent(eq(uid), any(LocalDate.class))).thenReturn(List.of(m));

        RecoveryController controller = new RecoveryController(metrics);
        var response = controller.recent(30);

        var items = response.getBody().items();
        assertThat(items).hasSize(1);
        var dto = items.get(0);
        assertThat(dto.date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dto.emotionDiversity()).isEqualTo(4);
        assertThat(dto.avgIntensity()).isEqualByComparingTo("3.2");
        assertThat(dto.diaryWords()).isEqualTo(120);
        assertThat(dto.missionsCompleted()).isEqualTo(2);
        assertThat(dto.riskSignals()).isZero();
    }

    @Test
    void recent_빈결과_빈리스트() {
        UUID uid = UUID.randomUUID();
        bindUser(uid);
        when(metrics.findRecent(eq(uid), any(LocalDate.class))).thenReturn(List.of());

        RecoveryController controller = new RecoveryController(metrics);
        var response = controller.recent(7);

        assertThat(response.getBody().items()).isEmpty();
    }
}
