package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.application.seed.ManifestDocument
import github.lms.lemuel.xr.asset.application.seed.ManifestParser
import github.lms.lemuel.xr.asset.application.seed.ManifestScanner
import github.lms.lemuel.xr.asset.application.seed.ManifestValidator
import github.lms.lemuel.xr.asset.domain.AssetManifest
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * classpath:manifests/{mission}/{device}/scene{N}-v{version}.json 을 스캔해
 * asset_manifests 테이블에 upsert. 부팅 1회.
 *
 * (mission_id, scene_number, device_type, version) 이미 있으면 스킵 — 재배포 안전.
 * 새 버전이면 신규 row INSERT (V10 의 superseded_by 흐름은 운영 도구에서 처리).
 *
 * SRP — 스캔([ManifestScanner])·파싱([ManifestParser])·검증
 * ([ManifestValidator]) 은 각각 별도 컴포넌트로 분리했고, 여기서는 그 셋을
 * 순서대로 엮어(orchestrate) 도메인 [AssetManifest] 구성·영속화만 담당한다.
 * 엔티티 매핑은 포트 어댑터가 전담하므로 시더는 JPA 를 알지 못한다.
 */
@Component
class AssetManifestSeeder(
    private val manifests: AssetManifestPort,
    private val scanner: ManifestScanner,
    private val parser: ManifestParser,
    private val validator: ManifestValidator,
) {

    @PostConstruct
    @Transactional
    fun seed() {
        val files = scanner.scan()

        var inserted = 0
        var skipped = 0
        for (r in files) {
            try {
                val doc = parser.parse(r)

                if (!validator.isValid(doc)) {
                    log.warn("필수 필드 누락 — 건너뜀: {}", r.filename)
                    continue
                }

                if (manifests.existsByCoordinates(
                        doc.missionId!!, doc.sceneNumber, doc.deviceType!!, doc.version!!,
                    )
                ) {
                    skipped++
                    continue
                }

                manifests.save(toDomain(doc))
                inserted++
            } catch (e: Exception) {
                log.error("manifest 로드 실패 — {}: {}", r.filename, e.message, e)
            }
        }
        log.info(
            "asset_manifests seed 완료: inserted={}, skipped={} (총 {} 파일 스캔)",
            inserted, skipped, files.size,
        )
    }

    /** 파싱된 [ManifestDocument] 를 신규 도메인 [AssetManifest] 로 (id·createdAt 생성, active=true). */
    private fun toDomain(doc: ManifestDocument): AssetManifest =
        AssetManifest(
            id = UUID.randomUUID(),
            missionId = doc.missionId!!,
            sceneNumber = doc.sceneNumber,
            deviceType = doc.deviceType!!,
            capabilitiesMin = doc.capabilitiesMin,
            version = doc.version!!,
            manifest = doc.manifest ?: emptyMap(),
            audioLocale = doc.audioLocale,
            totalSizeBytes = doc.totalSizeBytes,
            cdnBaseUrl = doc.cdnBaseUrl,
            active = true,
            createdAt = LocalDateTime.now(),
            supersededAt = null,
        )

    companion object {
        private val log = LoggerFactory.getLogger(AssetManifestSeeder::class.java)
    }
}
