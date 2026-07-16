package github.lms.lemuel.xr.emotion.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 감정 분류 로그 1건 — 불변 도메인 모델.
 *
 * Hibernate `EmotionLogJpaEntity` 로부터 격리된 순수 도메인 타입.
 * 영속성 어댑터(`EmotionLogPersistenceAdapter`) 에서만 엔티티 ↔ 이 레코드로 매핑한다.
 * application 계층은 이 타입만 다루고 JPA 엔티티를 절대 import 하지 않는다.
 *
 * docs/safety-guidelines.md §3 (PHI 비수집) — 사용자 자유 텍스트(raw_text) 는
 * 이 모델에 담기지 않는다. 분류에만 사용 후 폐기.
 *
 * @property id                 로그 id (신규 미저장 시 null)
 * @property userId             사용자 id
 * @property appSessionId       앱 세션 id (nullable)
 * @property classifiedEmotion  분류된 감정 이름 (Emotion.name())
 * @property confidence         분류 신뢰도 (0.000~1.000)
 * @property intensity          강도 (nullable)
 * @property chosenDimension    선택 차원 (spiritual/emotional/rational, nullable)
 * @property recommendedTrack   추천 트랙 ("A" | "B", nullable)
 * @property recommendedContent 추천 콘텐츠 키 ("topic:..." | "mission:...", nullable)
 * @property createdAt          생성 시각
 */
data class EmotionLog(
    val id: Long?,
    val userId: UUID?,
    val appSessionId: UUID?,
    val classifiedEmotion: String?,
    val confidence: BigDecimal?,
    val intensity: Short?,
    val chosenDimension: String?,
    val recommendedTrack: String?,
    val recommendedContent: String?,
    val createdAt: LocalDateTime?,
)
