package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.domain.AssetManifest
import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.springframework.stereotype.Service

/**
 * 미션·씬·디바이스로 최신 active asset manifest 를 조회하는 유스케이스.
 *
 * SRP — 인바운드 어댑터(컨트롤러)가 포트를 직접 주입받지 않도록, 조회·부재 예외 처리를
 * 이 유스케이스로 끌어올린다. 컨트롤러는 위임 후 wire 응답 조립만 담당한다.
 */
@Service
class GetAssetManifestUseCase(
    private val manifests: AssetManifestPort,
) {

    /**
     * 최신 active manifest 를 도메인으로 반환. 매칭이 없으면 [AppException] 로 거부.
     *
     * 모드를 생략하면 [XrMode.VR] — 모드를 모르는 기존 클라이언트의 동작은 그대로다.
     */
    fun getLatest(
        missionId: String,
        sceneNumber: Short?,
        deviceType: String,
        xrMode: XrMode = XrMode.VR,
    ): AssetManifest =
        manifests.findLatest(missionId, sceneNumber, deviceType, xrMode)
            .orElseThrow {
                AppException(
                    ErrorCode.E_VALIDATION,
                    "No active manifest for mission=$missionId device=$deviceType mode=${xrMode.wire}",
                )
            }
}
