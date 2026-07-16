package github.lms.lemuel.xr.values.application.port.out

/**
 * 실천 기록 메트릭 아웃바운드 포트.
 *
 * 애플리케이션 계층이 Micrometer 같은 구체 메트릭 라이브러리에 의존하지 않도록 격리한다 (DIP).
 */
interface PracticeMetricsPort {

    /**
     * 실천 1건 기록을 카운트한다.
     *
     * @param valueId         가치 id (1..7)
     * @param linkedCharacter 연계 인물 (없으면 null)
     */
    fun recordPractice(valueId: Int, linkedCharacter: String?)
}
