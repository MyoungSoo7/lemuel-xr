package github.lms.lemuel.xr.emotion.application.port.out

import github.lms.lemuel.xr.emotion.domain.EmotionLog

/**
 * 감정 분류 로그 out 포트 (ISP) — application 계층이 실제로 쓰는 persist(save) 연산만 노출.
 *
 * 포트는 도메인 타입([EmotionLog]) 으로만 말한다. JPA 엔티티는 어댑터
 * (`EmotionLogPersistenceAdapter`) 내부에만 존재한다.
 */
interface EmotionLogPort {

    /**
     * 로그를 저장하고, 저장된(=id 부여된) 도메인 모델을 반환한다.
     *
     * @param log id 가 null 인 신규 로그
     * @return 영속화되어 id 가 채워진 로그
     */
    fun save(log: EmotionLog): EmotionLog
}
