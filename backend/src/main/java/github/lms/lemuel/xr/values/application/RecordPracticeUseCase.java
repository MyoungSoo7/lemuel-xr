package github.lms.lemuel.xr.values.application;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeJpaEntity;
import github.lms.lemuel.xr.values.adapter.out.persistence.UserValuePracticeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일별 실천 1건 기록. CDR Index 계산의 원천 데이터. */
@Service
public class RecordPracticeUseCase {

    private final UserValuePracticeRepository practices;
    private final MeterRegistry meter;

    public RecordPracticeUseCase(UserValuePracticeRepository practices, MeterRegistry meter) {
        this.practices = practices;
        this.meter = meter;
    }

    @Transactional
    public UserValuePracticeJpaEntity execute(UUID userId, int valueId,
                                                Integer durationSec, String note,
                                                String linkedCharacter, UUID linkedGameSession) {
        if (valueId < 1 || valueId > 7) {
            throw new AppException(ErrorCode.E_VALIDATION, "valueId must be 1..7, got " + valueId);
        }
        UserValuePracticeJpaEntity p = new UserValuePracticeJpaEntity();
        p.setUserId(userId);
        p.setValueId((short) valueId);
        p.setPracticedAt(OffsetDateTime.now(ZoneOffset.UTC));
        p.setDurationSec(durationSec);
        p.setNote(note);
        p.setLinkedCharacter(linkedCharacter);
        p.setLinkedGameSession(linkedGameSession);
        practices.save(p);

        Counter.builder("values.practice")
                .tag("value_id", String.valueOf(valueId))
                .tag("linked_character", linkedCharacter == null ? "none" : linkedCharacter)
                .register(meter).increment();

        return p;
    }
}
