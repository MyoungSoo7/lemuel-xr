package github.lms.lemuel.xr.asset.adapter.`in`.web

import github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping.InputMappingResolver
import github.lms.lemuel.xr.asset.application.GetAssetManifestUseCase
import github.lms.lemuel.xr.asset.application.XrModePolicy
import github.lms.lemuel.xr.asset.domain.AssetManifest
import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
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
    private val xrModes: XrModePolicy,
) {

    /**
     * `mode` 는 vr|ar, 생략하면 vr. 미션이 열지 않은 모드면 [XrModePolicy] 가 400 으로 막는다
     * — 조회해서 "없음" 이 아니라, 애초에 지원하지 않는다고 답해야 클라이언트가
     * 빈 씬을 띄우지 않는다.
     */
    @GetMapping("/asset-manifest")
    fun manifest(
        @RequestParam mission: String,
        @RequestParam device: String,
        @RequestParam(required = false) scene: Short?,
        @RequestParam(required = false) mode: String?,
    ): ResponseEntity<ManifestResponse> {
        val xrMode = xrModes.resolve(mission, mode)
        val m = getAssetManifest.getLatest(mission, scene, device, xrMode)
        return ResponseEntity.ok(
            ManifestResponse(
                m.id, m.missionId, m.sceneNumber,
                m.deviceType, m.xrMode.wire, m.version, m.cdnBaseUrl,
                m.totalSizeBytes, m.manifest,
            ),
        )
    }

    /** 미션이 노출하는 몰입 모드 목록 — 클라이언트가 AR 토글을 그릴지 결정한다. */
    @GetMapping("/xr-modes")
    fun xrModes(@RequestParam mission: String): ResponseEntity<XrModesResponse> =
        ResponseEntity.ok(
            XrModesResponse(
                mission,
                XrMode.entries.filter { xrModes.supports(mission, it) }.map { it.wire },
            ),
        )

    @GetMapping("/input-mapping")
    fun inputMapping(
        @RequestParam device: String,
        @RequestParam(required = false) mode: String?,
    ): ResponseEntity<Map<String, Any>> {
        // 디바이스별 시맨틱 액션 매핑 (XR-INTEGRATION §13.5).
        // OCP — 디바이스 추가는 InputMappingProvider 빈 추가만으로 끝. 여기는 손대지 않는다.
        // 모드는 디바이스와 직교하므로 resolver 가 AR 오버레이를 얹어 준다.
        val xrMode = XrMode.fromOrDefault(mode)
            ?: throw AppException(ErrorCode.E_VALIDATION, "Unknown xr mode: $mode (expected vr|ar)")
        return ResponseEntity.ok(inputMappings.resolve(device, xrMode))
    }

    data class ManifestResponse(
        val id: UUID,
        val missionId: String,
        val sceneNumber: Short?,
        val deviceType: String,
        val xrMode: String,
        val version: String,
        val cdnBaseUrl: String?,
        val totalSizeBytes: Long?,
        val manifest: Map<String, Any?>,
    )

    data class XrModesResponse(
        val missionId: String,
        val modes: List<String>,
    )
}
