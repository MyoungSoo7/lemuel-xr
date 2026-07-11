package github.lms.lemuel.xr.recovery.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.recovery.adapter.out.persistence.RecoveryMetricJpaEntity;
import github.lms.lemuel.xr.recovery.adapter.out.persistence.RecoveryMetricRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/users/me/recovery — 본인 30일 회복 추이 */
@RestController
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryMetricRepository metrics;

    @GetMapping("/api/users/me/recovery")
    public ResponseEntity<RecoveryResponse> recent(@RequestParam(defaultValue = "30") int days) {
        var data = metrics.findRecent(RequestContext.currentUserId(),
                LocalDate.now().minusDays(days));
        var items = data.stream().map(this::toDto).toList();
        return ResponseEntity.ok(new RecoveryResponse(items));
    }

    private MetricDto toDto(RecoveryMetricJpaEntity r) {
        return new MetricDto(r.getMetricDate(), r.getEmotionDiversityCount(),
                r.getAvgIntensity(), r.getDiaryWordCount(),
                r.getMissionCompletedCount(), r.getRiskSignalCount());
    }

    public record RecoveryResponse(List<MetricDto> items) {}
    public record MetricDto(LocalDate date, Integer emotionDiversity, BigDecimal avgIntensity,
                             Integer diaryWords, Integer missionsCompleted, Integer riskSignals) {}
}
