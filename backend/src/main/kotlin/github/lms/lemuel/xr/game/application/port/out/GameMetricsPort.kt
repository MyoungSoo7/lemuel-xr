package github.lms.lemuel.xr.game.application.port.out

/**
 * 게임 세션 메트릭 아웃바운드 포트 — Micrometer 등 구체 계측 라이브러리를 애플리케이션/웹
 * 계층에서 격리한다. values `PracticeMetricsPort` · safety `SafetyMetricsPort` 와 동일 패턴.
 */
interface GameMetricsPort {

    /** 세션 시작 카운트. `mode` 가 null 이면 어댑터가 "unspecified" 로 기록. */
    fun sessionStarted(character: String, mode: String?)

    /** 세션 완료 카운트. */
    fun sessionCompleted(character: String)
}
