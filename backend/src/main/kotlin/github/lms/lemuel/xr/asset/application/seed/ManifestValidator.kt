package github.lms.lemuel.xr.asset.application.seed

import github.lms.lemuel.xr.asset.domain.XrMode
import org.springframework.stereotype.Component

/** 시드 대상 manifest 의 필수 필드 검증. */
@Component
class ManifestValidator {

    /**
     * mission_id / device_type / version 이 모두 있고, xr_mode 가 (없거나) 아는 값이면 유효.
     *
     * 오타난 xr_mode 를 조용히 VR 로 떨어뜨리면 AR 시드가 VR 로 섞여 들어가므로,
     * 값이 있는데 해석이 안 되면 유효하지 않은 것으로 본다.
     */
    fun isValid(doc: ManifestDocument?): Boolean =
        doc != null &&
            doc.missionId != null &&
            doc.deviceType != null &&
            doc.version != null &&
            XrMode.fromOrDefault(doc.xrMode) != null
}
