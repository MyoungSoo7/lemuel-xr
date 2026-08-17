package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

import github.lms.lemuel.xr.asset.domain.XrMode
import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import org.springframework.stereotype.Component

/**
 * 등록된 모든 [InputMappingProvider] 빈을 device id 로 색인하고 조회한다.
 *
 * OCP — Spring 이 `List<InputMappingProvider>` 를 주입하므로, 새 디바이스는
 * provider 빈 추가만으로 자동 등록된다. 이 클래스도 컨트롤러도 수정할 필요가 없다.
 */
@Component
class InputMappingResolver(providers: List<InputMappingProvider>) {

    private val byDevice: Map<String, InputMappingProvider> =
        providers.associateBy { it.deviceId().lowercase() }

    /**
     * 디바이스 매핑 조회. 미지원 디바이스면 [ErrorCode.E_VALIDATION].
     *
     * [XrMode.AR] 이면 VR 매핑 위에 provider 의 AR 오버레이를 얹는다. 오버레이가 없는
     * 디바이스는 VR 매핑 그대로 — AR 이 그 기기에서 의미 없다는 사실은 매핑이 아니라
     * `XrModePolicy`/manifest 부재가 말해 준다.
     */
    @JvmOverloads
    fun resolve(device: String, mode: XrMode = XrMode.VR): Map<String, Any> {
        val provider = byDevice[device.lowercase()]
            ?: throw AppException(ErrorCode.E_VALIDATION, "Unknown device: $device")
        val base = provider.mapping()
        return if (mode == XrMode.AR) base + provider.arOverlay() else base
    }
}
