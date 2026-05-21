package github.lms.lemuel.xr.safety.application;

import github.lms.lemuel.xr.common.chatops.TelegramChatOps;
import github.lms.lemuel.xr.safety.adapter.out.persistence.CrisisResourceJpaEntity;
import github.lms.lemuel.xr.safety.adapter.out.persistence.CrisisResourceRepository;
import github.lms.lemuel.xr.safety.adapter.out.persistence.SafetyAlertJpaEntity;
import github.lms.lemuel.xr.safety.adapter.out.persistence.SafetyAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키워드 매칭 → safety_alerts INSERT + 보여줄 crisis_resources 선택.
 * 사용자 노출 텍스트 (alert/disclaimer) 는 lemuel-xr-mental-health-safety skill 가이드 따름.
 */
@Service
public class RecordSafetyAlertUseCase {

    /** 이 severity 이상이면 운영자에게 Telegram 알람. */
    private static final Set<String> CHATOPS_SEVERITIES = Set.of("high", "critical");

    private final SafetyAlertRepository alerts;
    private final CrisisResourceRepository resources;
    private final TelegramChatOps chatOps;
    private final MeterRegistry meter;

    public RecordSafetyAlertUseCase(SafetyAlertRepository alerts,
                                     CrisisResourceRepository resources,
                                     TelegramChatOps chatOps,
                                     MeterRegistry meter) {
        this.alerts = alerts;
        this.resources = resources;
        this.chatOps = chatOps;
        this.meter = meter;
    }

    @Transactional
    public Result execute(UUID userId, Long emotionLogId, String triggerSource,
                          CrisisKeywordScanner.ScanResult scan) {
        if (!scan.matched()) return Result.notTriggered();

        // 메트릭 — Grafana 안전 row 의 severity 별 카운트.
        Counter.builder("safety.alert")
                .tag("severity", scan.severity())
                .tag("source", triggerSource == null ? "unknown" : triggerSource)
                .register(meter).increment();

        // 한국 자원 우선 (region=KR, locale=ko-KR), severity 별 추천
        List<CrisisResourceJpaEntity> active = resources
                .findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc("KR", "ko-KR");
        List<Map<String, Object>> shown = active.stream()
                .map(this::toShownMap).toList();

        SafetyAlertJpaEntity a = new SafetyAlertJpaEntity();
        a.setUserId(userId);
        a.setEmotionLogId(emotionLogId);
        a.setMatchedPattern(scan.matchedPattern());
        a.setSeverity(scan.severity());
        a.setTriggerSource(triggerSource);
        a.setRawExcerptHash(scan.excerptHash());
        a.setShownResources(shown);
        a.setCreatedAt(LocalDateTime.now());
        alerts.save(a);

        // high/critical 은 운영자 즉시 알람 — PII 없이 alertId + severity + source + hash 만.
        // ETHICS-LEGAL §2.2: raw text 절대 노출 X. excerptHash 로 사후 추적.
        if (CHATOPS_SEVERITIES.contains(scan.severity())) {
            chatOps.notify(
                    TelegramChatOps.Severity.CRITICAL,
                    "Safety alert " + scan.severity(),
                    "alertId=" + a.getId()
                            + " source=" + triggerSource
                            + " pattern=" + scan.matchedPattern()
                            + " hash=" + (scan.excerptHash() == null ? "-" : scan.excerptHash().substring(0, 12))
                            + " resources=" + shown.size()
            );
        }

        return new Result(true, a.getId(), shown);
    }

    private Map<String, Object> toShownMap(CrisisResourceJpaEntity r) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", r.getName());
        m.put("contactType", r.getContactType());
        m.put("contactValue", r.getContactValue());
        m.put("hours", r.getHours());
        m.put("category", r.getCategory());
        return m;
    }

    public record Result(boolean triggered, Long alertId, List<Map<String, Object>> shownResources) {
        public static Result notTriggered() { return new Result(false, null, List.of()); }
    }
}
