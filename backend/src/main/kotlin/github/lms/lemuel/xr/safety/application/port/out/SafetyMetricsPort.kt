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

    /**
     * 금지 토큰 게이트가 문구를 **차단** 했음을 카운트한다. (`content.gate.block`)
     *
     * 차단은 지금까지 [ForbiddenTokenSanitizer][github.lms.lemuel.xr.safety.application.ForbiddenTokenSanitizer]
     * 의 로그로만 남았고 LLM 경로(`GenerateLlmResponseUseCase`)는 **아무 신호도 남기지 않았다** —
     * 즉 운영자는 게이트가 작동 중인지 죽어 있는지 구분할 수 없었다. 이 카운터가 그 구멍을 메운다.
     *
     * @param axis 안전 축. `T1`(신학 정통성) 또는 `R2_R3`(정신건강).
     * @param layer 어느 층에서 막았는가. `llm`(생성 응답) 또는 `content`(저작 문구).
     *   같은 축이라도 층이 다르면 대응이 다르다 — `content` 는 저작 실수, `llm` 은 모델 드리프트다.
     * @param ruleId 토큰의 **불투명 식별자**(`ForbiddenTokenScanner.ruleIdOf`). 금칙 문구 자체는
     *   가스라이팅 표현이라 메트릭 태그로 내보내지 않는다.
     */
    fun recordGateBlock(axis: String, layer: String, ruleId: String)

    companion object {
        /** LLM 이 방금 생성한 응답에서 막았다. 모델·프롬프트 드리프트의 신호다. */
        const val LAYER_LLM = "llm"

        /** 저작된 고정 문구에서 막았다. 콘텐츠 리뷰가 놓친 것이므로 저작 쪽 결함이다. */
        const val LAYER_CONTENT = "content"

        /**
         * 부팅 시 미리 등록하는 자리표시 `rule_id`.
         *
         * Prometheus 는 **같은 이름의 미터가 같은 태그 키 집합** 을 갖기를 요구한다. 그래서
         * axis×layer 조합을 0 으로 미리 세워 둘 때도 `rule_id` 키를 뺄 수 없다. 실제 규칙과
         * 겹치지 않는 값을 둔다 — 이 계열은 영원히 0 이고, 그 0 이 「아직 안 걸렸다」와
         * 「메트릭이 아예 없다(=게이트가 죽었거나 배포가 안 됐다)」를 구분해 준다.
         */
        const val RULE_ID_NONE = "none"
    }
}
