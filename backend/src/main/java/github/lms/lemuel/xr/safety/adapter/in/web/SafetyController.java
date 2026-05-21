package github.lms.lemuel.xr.safety.adapter.in.web;

import github.lms.lemuel.xr.common.web.RequestContext;
import github.lms.lemuel.xr.game.application.ExitSessionUseCase;
import github.lms.lemuel.xr.safety.adapter.out.persistence.CrisisResourceJpaEntity;
import github.lms.lemuel.xr.safety.adapter.out.persistence.CrisisResourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/safety/* — 위기 자원 카탈로그 + 게임 세션 emergency exit.
 *
 * <p>크라이시스 자원 GET 은 인증 불필요 — 가장 절박한 순간에 빠른 접근을 보장.
 * lemuel-xr-mental-health-safety skill 의 R1 safety line 따름.</p>
 */
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private final CrisisResourceRepository resources;
    private final ExitSessionUseCase exitUc;

    @GetMapping("/api/safety/crisis-resources")
    public ResponseEntity<CrisisResponse> resources(
            @RequestParam(defaultValue = "KR") String region,
            @RequestParam(defaultValue = "ko-KR") String locale) {
        List<ResourceDto> items = resources
                .findByRegionAndLocaleAndActiveOrderByPriority(region, locale, true)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(new CrisisResponse(items));
    }

    @PostMapping("/api/game/sessions/{sid}/exit")
    public ResponseEntity<ExitResponse> exit(@PathVariable("sid") UUID sid,
                                              @RequestBody ExitRequest req) {
        var r = exitUc.execute(sid, req.reason(), req.atSceneId());
        return ResponseEntity.ok(new ExitResponse(
                r.sessionId(), r.exitedAt(),
                "잠시 멈추셨네요. 다음에 다시 만나요. 시편 23편 음성을 함께 두고 갈게요.",
                null  // softLandingAudioUrl 은 TTS 사이드카 비동기 생성 후 결정
        ));
    }

    private ResourceDto toDto(CrisisResourceJpaEntity r) {
        return new ResourceDto(r.getName(), r.getContactType(), r.getContactValue(),
                r.getDescription(), r.getHours(), r.getCategory());
    }

    public record CrisisResponse(List<ResourceDto> resources) {}
    public record ResourceDto(String name, String contactType, String contactValue,
                                String description, String hours, String category) {}

    public record ExitRequest(String reason, Integer atSceneId) {}
    public record ExitResponse(UUID sessionId, LocalDateTime exitedAt,
                                String gentleMessage, String softLandingAudioUrl) {}
}
