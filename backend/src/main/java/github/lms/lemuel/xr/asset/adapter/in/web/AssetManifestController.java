package github.lms.lemuel.xr.asset.adapter.in.web;

import github.lms.lemuel.xr.asset.adapter.out.persistence.AssetManifestJpaEntity;
import github.lms.lemuel.xr.asset.adapter.out.persistence.AssetManifestRepository;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * /api/config/asset-manifest — XR 클라이언트가 미션 시작 전 자산 다운로드 결정용.
 * /api/config/input-mapping — 디바이스별 입력 시맨틱 매핑 yaml.
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class AssetManifestController {

    private final AssetManifestRepository repo;

    @GetMapping("/asset-manifest")
    public ResponseEntity<ManifestResponse> manifest(@RequestParam String mission,
                                                       @RequestParam String device,
                                                       @RequestParam(required = false) Short scene) {
        AssetManifestJpaEntity m = repo.findLatest(mission, scene, device)
                .orElseThrow(() -> new AppException(ErrorCode.E_VALIDATION,
                        "No active manifest for mission=" + mission + " device=" + device));
        return ResponseEntity.ok(new ManifestResponse(
                m.getId(), m.getMissionId(), m.getSceneNumber(),
                m.getDeviceType(), m.getVersion(), m.getCdnBaseUrl(),
                m.getTotalSizeBytes(), m.getManifest()
        ));
    }

    @GetMapping("/input-mapping")
    public ResponseEntity<Map<String, Object>> inputMapping(@RequestParam String device) {
        // 디바이스별 시맨틱 액션 매핑 (XR-INTEGRATION §13.5).
        return switch (device.toLowerCase()) {
            case "quest3" -> ResponseEntity.ok(Map.of(
                    "GRAB", Map.of("source", "controller", "binding", "grip", "fallback",
                            Map.of("source", "hand", "binding", "pinch")),
                    "POINT_AT", Map.of("source", "controller", "binding", "raycast"),
                    "GAZE_DURATION", Map.of("source", "head", "binding", "head_direction_dwell")
            ));
            case "visionpro" -> ResponseEntity.ok(Map.of(
                    "GRAB", Map.of("source", "hand", "binding", "pinch"),
                    "POINT_AT", Map.of("source", "eye+hand", "binding", "look_and_pinch"),
                    "GAZE_DURATION", Map.of("source", "eye", "binding", "eye_dwell")
            ));
            case "galaxyxr" -> ResponseEntity.ok(Map.of(
                    "GRAB", Map.of("source", "hand", "binding", "pinch"),
                    "POINT_AT", Map.of("source", "eye+hand", "binding", "look_and_pinch"),
                    "GAZE_DURATION", Map.of("source", "eye", "binding", "eye_dwell")
            ));
            default -> throw new AppException(ErrorCode.E_VALIDATION, "Unknown device: " + device);
        };
    }

    public record ManifestResponse(UUID id, String missionId, Short sceneNumber,
                                     String deviceType, String version, String cdnBaseUrl,
                                     Long totalSizeBytes, Map<String, Object> manifest) {}
}
