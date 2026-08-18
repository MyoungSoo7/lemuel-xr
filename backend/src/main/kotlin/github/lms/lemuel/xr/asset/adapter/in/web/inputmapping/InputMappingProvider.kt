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

    /** 해당 디바이스의 액션→바인딩 매핑 (VR 기준). */
    fun mapping(): Map<String, Any>

    /**
     * AR 모드에서 덮어쓰거나 추가할 액션. 기본은 없음 —
     * AR 을 지원하지 않는 디바이스는 이 메서드를 건드리지 않는다(OCP).
     *
     * AR 은 실제 공간을 배경으로 쓰므로 VR 과 입력 의미가 달라진다.
     * 예: 이동은 인공 로코모션이 아니라 사용자의 실제 걸음이고,
     * 배치는 평면 히트테스트가 필요하다.
     */
    fun arOverlay(): Map<String, Any> = emptyMap()
}
