package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

/**
 * 디바이스별 입력 시맨틱 매핑 제공자 (XR-INTEGRATION §13.5).
 *
 * OCP — 새 디바이스 지원은 이 인터페이스를 구현한 `@Component` 빈 하나를 추가하면 끝이다.
 * 기존 코드(컨트롤러·다른 provider)는 수정하지 않는다.
 */
interface InputMappingProvider {

    /** 이 provider 가 담당하는 디바이스 id (소문자, 예: `"quest3"`). */
    fun deviceId(): String

    /** 해당 디바이스의 액션→바인딩 매핑. */
    fun mapping(): Map<String, Any>
}
