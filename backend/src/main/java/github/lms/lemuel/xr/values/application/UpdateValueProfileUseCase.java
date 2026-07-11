package github.lms.lemuel.xr.values.application;

import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValueProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 7 가치 정의 (전체 또는 부분). */
@Service
@RequiredArgsConstructor
public class UpdateValueProfileUseCase {

    private final UserValueProfileRepository profiles;

    @Transactional
    public UserValueProfileJpaEntity execute(UUID userId, Map<String, Object> valuesPatch) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserValueProfileJpaEntity p = profiles.findByUserId(userId).orElseGet(() -> {
            UserValueProfileJpaEntity n = new UserValueProfileJpaEntity();
            n.setId(UUID.randomUUID());
            n.setUserId(userId);
            n.setValuesJson(new HashMap<>());
            n.setStartedAt(now);
            return n;
        });

        // 부분 갱신 — patch 의 키만 덮어씀. value 가 null 이면 삭제.
        Map<String, Object> current = p.getValuesJson() == null ? new HashMap<>() : new HashMap<>(p.getValuesJson());
        for (Map.Entry<String, Object> e : valuesPatch.entrySet()) {
            if (e.getValue() == null) {
                current.remove(e.getKey());
            } else {
                current.put(e.getKey(), e.getValue());
            }
        }
        p.setValuesJson(current);
        p.setLastUpdatedAt(now);
        return profiles.save(p);
    }
}
