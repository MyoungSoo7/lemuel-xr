package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

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

    /** 디바이스 매핑 조회. 미지원 디바이스면 [ErrorCode.E_VALIDATION]. */
    fun resolve(device: String): Map<String, Any> {
        val provider = byDevice[device.lowercase()]
            ?: throw AppException(ErrorCode.E_VALIDATION, "Unknown device: $device")
        return provider.mapping()
    }
}
