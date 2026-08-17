package github.lms.lemuel.xr.asset.domain

/**
 * 몰입 모드 — 에셋 묶음을 가르는 축. 디바이스와 직교한다.
 *
 * 같은 quest3 라도 [VR] 은 환경 모델을 통째로 받고, [AR] 은 실제 방을 배경으로 쓰므로
 * 환경을 빼고 앵커에 붙일 소품만 받는다. 즉 모드는 "어떤 기기냐" 가 아니라
 * "무엇을 내려받느냐" 를 정한다.
 *
 * 어떤 미션이 어떤 모드를 노출할지는 여기서 정하지 않는다 — 그건 정책이고,
 * `XrModePolicy` 가 설정으로 가진다.
 */
enum class XrMode(val wire: String) {
    VR("vr"),
    AR("ar"),
    ;

    companion object {

        /** wire 문자열(대소문자 무시) → [XrMode]. 모르는 값이면 null. */
        fun from(value: String?): XrMode? {
            val v = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wire == v }
        }

        /** 값이 비어 있으면 [VR] — 모드를 모르는 기존 클라이언트는 VR 로 남는다. */
        fun fromOrDefault(value: String?): XrMode? =
            if (value.isNullOrBlank()) VR else from(value)
    }
}
