package github.lms.lemuel.xr.asset.application.seed

import org.springframework.stereotype.Component

/** 시드 대상 manifest 의 필수 필드 검증. */
@Component
class ManifestValidator {

    /** mission_id / device_type / version 이 모두 있으면 유효. */
    fun isValid(doc: ManifestDocument?): Boolean =
        doc != null &&
            doc.missionId != null &&
            doc.deviceType != null &&
            doc.version != null
}
