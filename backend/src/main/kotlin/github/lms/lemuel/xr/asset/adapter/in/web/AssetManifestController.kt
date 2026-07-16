package github.lms.lemuel.xr.asset.adapter.`in`.web

import github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping.InputMappingResolver
import github.lms.lemuel.xr.asset.application.GetAssetManifestUseCase
import github.lms.lemuel.xr.asset.domain.AssetManifest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * `/api/config/asset-manifest` — XR 클라이언트가 미션 시작 전 자산 다운로드 결정용.
 * `/api/config/input-mapping` — 디바이스별 입력 시맨틱 매핑 yaml.
 *
 * SRP — manifest 조회는 [GetAssetManifestUseCase] 에 위임하고, 여기서는
 * 도메인 [AssetManifest] → wire [ManifestResponse] 조립만 한다.
 * 컨트롤러가 아웃바운드 포트를 직접 주입받지 않는다.
 */
@RestController
@RequestMapping("/api/config")
class AssetManifestController(
    private val getAssetManifest: GetAssetManifestUseCase,
    private val inputMappings: InputMappingResolver,
) {

    @GetMapping("/asset-manifest")
    fun manifest(
        @RequestParam mission: String,
        @RequestParam device: String,
        @RequestParam(required = false) scene: Short?,
    ): ResponseEntity<ManifestResponse> {
        val m = getAssetManifest.getLatest(mission, scene, device)
        return ResponseEntity.ok(
            ManifestResponse(
                m.id, m.missionId, m.sceneNumber,
                m.deviceType, m.version, m.cdnBaseUrl,
                m.totalSizeBytes, m.manifest,
            ),
        )
    }

    @GetMapping("/input-mapping")
    fun inputMapping(@RequestParam device: String): ResponseEntity<Map<String, Any>> {
        // 디바이스별 시맨틱 액션 매핑 (XR-INTEGRATION §13.5).
        // OCP — 디바이스 추가는 InputMappingProvider 빈 추가만으로 끝. 여기는 손대지 않는다.
        return ResponseEntity.ok(inputMappings.resolve(device))
    }

    data class ManifestResponse(
        val id: UUID,
        val missionId: String,
        val sceneNumber: Short?,
        val deviceType: String,
        val version: String,
        val cdnBaseUrl: String?,
        val totalSizeBytes: Long?,
        val manifest: Map<String, Any?>,
    )
}
