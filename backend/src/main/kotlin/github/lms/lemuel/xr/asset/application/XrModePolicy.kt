package github.lms.lemuel.xr.asset.application

import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * "어떤 미션이 어떤 몰입 모드를 노출하는가" 정책.
 *
 * 현재는 요셉만 AR 을 연다. AR 은 패스스루·평면 인식·앵커가 필요해 씬을 따로 만들어야
 * 하고(XR-INTEGRATION §13 Phase 3/4), 요셉 외 미션엔 그 에셋이 없다. 없는 걸 열어두면
 * 클라이언트는 "지원한다" 고 믿고 빈 씬을 띄운다 — 그래서 조회 전에 막는다.
 *
 * 미션 목록을 코드가 아니라 설정(`lemuel.xr.ar-enabled-missions`)에 두는 이유:
 * 다윗·모세에 AR 씬이 생기면 배포 없이 열 수 있어야 하고, 무엇보다 이 분기가
 * 도메인 안으로 새어 들어가지 않게 한 곳에 묶어두기 위해서다.
 */
@Component
class XrModePolicy(
    @Value("\${lemuel.xr.ar-enabled-missions:joseph}") arEnabledMissions: String,
) {

    private val arMissions: Set<String> =
        arEnabledMissions.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    init {
        log.info("AR 허용 미션: {}", arMissions.ifEmpty { setOf("(없음)") })
    }

    /** 해당 미션이 그 모드를 노출하는가. VR 은 항상 열려 있다. */
    fun supports(missionId: String, mode: XrMode): Boolean =
        mode == XrMode.VR || missionId.trim().lowercase() in arMissions

    /**
     * 요청 파라미터를 [XrMode] 로 해석하고 미션 정책까지 통과시킨다.
     *
     * @throws AppException 모르는 모드 값이거나, 그 미션이 열지 않은 모드일 때
     */
    fun resolve(missionId: String, requested: String?): XrMode {
        val mode = XrMode.fromOrDefault(requested)
            ?: throw AppException(ErrorCode.E_VALIDATION, "Unknown xr mode: $requested (expected vr|ar)")

        if (!supports(missionId, mode)) {
            throw AppException(
                ErrorCode.E_VALIDATION,
                "Mission '$missionId' does not support ${mode.wire} mode",
            )
        }
        return mode
    }

    companion object {
        private val log = LoggerFactory.getLogger(XrModePolicy::class.java)
    }
}
