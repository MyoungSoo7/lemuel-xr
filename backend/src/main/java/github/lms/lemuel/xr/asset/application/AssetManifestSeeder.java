package github.lms.lemuel.xr.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.lms.lemuel.xr.asset.adapter.out.persistence.AssetManifestJpaEntity;
import github.lms.lemuel.xr.asset.adapter.out.persistence.AssetManifestRepository;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * classpath:manifests/{mission}/{device}/scene{N}-v{version}.json 을 스캔해
 * asset_manifests 테이블에 upsert. 부팅 1회.
 *
 * <p>(mission_id, scene_number, device_type, version) 이미 있으면 스킵 — 재배포 안전.
 * 새 버전이면 신규 row INSERT (V10 의 superseded_by 흐름은 운영 도구에서 처리).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssetManifestSeeder {

    private final AssetManifestRepository repo;
    private final ObjectMapper json;

    @PostConstruct
    @Transactional
    public void seed() {
        ResourcePatternResolver scanner = new PathMatchingResourcePatternResolver();
        Resource[] files;
        try {
            files = scanner.getResources("classpath*:manifests/**/*.json");
        } catch (Exception e) {
            log.warn("manifest 스캔 실패: {}", e.getMessage());
            return;
        }

        int inserted = 0;
        int skipped = 0;
        for (Resource r : files) {
            try (InputStream in = r.getInputStream()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = json.readValue(in, Map.class);

                String missionId = (String) doc.get("mission_id");
                Short sceneNumber = doc.get("scene_number") == null ? null : ((Number) doc.get("scene_number")).shortValue();
                String deviceType = (String) doc.get("device_type");
                String version = (String) doc.get("version");

                if (missionId == null || deviceType == null || version == null) {
                    log.warn("필수 필드 누락 — 건너뜀: {}", r.getFilename());
                    continue;
                }

                if (repo.existsByMissionIdAndSceneNumberAndDeviceTypeAndVersion(
                        missionId, sceneNumber, deviceType, version)) {
                    skipped++;
                    continue;
                }

                AssetManifestJpaEntity row = new AssetManifestJpaEntity();
                row.setId(UUID.randomUUID());
                row.setMissionId(missionId);
                row.setSceneNumber(sceneNumber);
                row.setDeviceType(deviceType);
                row.setVersion(version);
                row.setCapabilitiesMin(asMap(doc.get("capabilities_min")));
                row.setManifest(asMap(doc.get("manifest")));
                row.setAudioLocale((String) doc.get("audio_locale"));
                row.setTotalSizeBytes(doc.get("total_size_bytes") == null
                        ? null : ((Number) doc.get("total_size_bytes")).longValue());
                row.setCdnBaseUrl((String) doc.get("cdn_base_url"));
                row.setIsActive(true);
                row.setCreatedAt(LocalDateTime.now());

                repo.save(row);
                inserted++;
            } catch (Exception e) {
                log.error("manifest 로드 실패 — {}: {}", r.getFilename(), e.getMessage(), e);
            }
        }
        log.info("asset_manifests seed 완료: inserted={}, skipped={} (총 {} 파일 스캔)",
                inserted, skipped, files.length);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return v == null ? null : (Map<String, Object>) v;
    }
}
