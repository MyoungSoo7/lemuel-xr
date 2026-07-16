package github.lms.lemuel.xr.safety.application.port.out

/**
 * 안전(safety) 메트릭 아웃바운드 포트.
 *
 * 애플리케이션·웹 계층이 Micrometer 같은 구체 메트릭 라이브러리에 의존하지 않도록 격리한다 (DIP).
 * `values` 컨텍스트의 `PracticeMetricsPort` 패턴을 따른다.
 */
interface SafetyMetricsPort {

    /**
     * 위기 키워드 매칭으로 safety_alert 가 발생했음을 카운트한다. (`safety.alert`)
     *
     * @param severity 매칭 severity (예: high/critical)
     * @param source 트리거 출처 (없으면 null → "unknown")
     */
    fun recordAlert(severity: String?, source: String?)

    /**
     * 게임 세션 emergency exit 를 사유별로 카운트한다. (`safety.session.exit`)
     *
     * @param reason exit 사유 (없으면 null → "user_choice")
     */
    fun recordSessionExit(reason: String?)
}
