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
 * 현재 AR 이 열린 미션은 요셉·모세·다윗·예수·엘리야·솔로몬·욥 — AR manifest 가
 * 실제로 있는 미션 전부다. 아직 씬 manifest 가 없는 미션(라합 등 저작 중)은 닫혀 있다.
 * 없는 걸 열어두면 클라이언트는 "지원한다" 고 믿고 빈 씬을 띄운다 — 그래서 조회 전에 막는다.
 *
 * ⚠️ **룻은 시나리오가 열린 뒤에도 AR 은 닫혀 있다** — 이건 저작이 덜 된 게 아니라
 * 결정이다. AR 패스스루는 씬을 사용자의 실제 방에 놓는데, 룻 Scene 4(밤·권력 비대칭)를
 * 그렇게 놓는 것이 VR 과 같은 노출인지에 답할 사람이 없었다. 그 축을 열지 않는 쪽을
 * 골랐고, 이미 만들어 둔 룻 AR 시드 15개도 같이 지웠다 — 목록만 고쳐두면 환경변수
 * `XR_AR_MISSIONS` 한 줄로 되살아나기 때문이다. 경위는 `docs/RUTH-RUNTIME-SIGNOFF.md`.
 *
 * 목록은 `scripts/gen_ar_manifests.py` 의 `AR_MISSIONS` 와 같아야 한다. 한쪽만 늘리면
 * 게이트는 열렸는데 에셋이 없거나(404), 에셋은 있는데 게이트가 닫힌(400) 상태가 된다.
 *
 * 미션 목록을 코드가 아니라 설정(`lemuel.xr.ar-enabled-missions`)에 두는 이유:
 * 새 미션에 AR 씬이 생기면 배포 없이 열 수 있어야 하고, 무엇보다 이 분기가
 * 도메인 안으로 새어 들어가지 않게 한 곳에 묶어두기 위해서다.
 */
@Component
class XrModePolicy(
    @Value("\${lemuel.xr.ar-enabled-missions:joseph,moses,david,jesus,elijah,solomon,job}")
    arEnabledMissions: String,
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
