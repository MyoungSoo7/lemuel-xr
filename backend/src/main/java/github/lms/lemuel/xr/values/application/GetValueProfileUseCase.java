package github.lms.lemuel.xr.values.application;

import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 7 가치 프로파일 + 최근 7일 실천 통계 + CDR Index. */
@Service
@RequiredArgsConstructor
public class GetValueProfileUseCase {

    private final UserValueProfileRepository profiles;
    private final UserValuePracticeRepository practices;

    @Transactional(readOnly = true)
    public Result execute(UUID userId) {
        UserValueProfileJpaEntity p = profiles.findByUserId(userId).orElse(null);
        Map<String, Object> valuesJson = p == null ? new HashMap<>() : p.getValuesJson();

        OffsetDateTime sevenDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        List<UserValuePracticeJpaEntity> recent = practices.findRecent(userId, sevenDaysAgo);

        // value_id (1~7) 별 카운트
        Map<String, Integer> countByValue = new HashMap<>();
        for (UserValuePracticeJpaEntity r : recent) {
            countByValue.merge(String.valueOf(r.getValueId()), 1, Integer::sum);
        }

        int totalPractices = recent.size();
        int cdrIndex = computeCdrIndex(totalPractices);
        String tier = tierOf(cdrIndex);

        return new Result(valuesJson, totalPractices, countByValue, cdrIndex, tier);
    }

    /**
     * Civil Defense Readiness Index — CROSS-MAPPING-VR-AR.md §6.
     *
     * <p>49 = 7 가치 × 7일. 100 = 매일 7 가치 모두 실천. 0 = 7일간 실천 0건.</p>
     */
    static int computeCdrIndex(int totalPractices) {
        int maxPossible = 49;
        int raw = Math.min(maxPossible, totalPractices) * 100 / maxPossible;
        return Math.max(0, Math.min(100, raw));
    }

    static String tierOf(int cdr) {
        if (cdr >= 80) return "VERY_READY";
        if (cdr >= 50) return "NORMAL";
        return "BUILD_UP";
    }

    public record Result(
            Map<String, Object> valuesJson,
            int totalPractices7d,
            Map<String, Integer> countByValue,
            int cdrIndex,
            String tier
    ) {}
}
