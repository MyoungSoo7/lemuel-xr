package github.lms.lemuel.xr.emotion.domain

/**
 * 7 가지 감정 분류 enum.
 *
 * 사용자 입력 텍스트를 LLM 으로 분류한 결과.
 * 분류 실패 또는 알 수 없는 값은 [CONFUSED] 로 fallback.
 */
enum class Emotion {
    ANXIOUS, // 불안
    SAD, // 슬픔
    ANGRY, // 분노
    CONFUSED, // 혼란
    LONELY, // 외로움
    EXHAUSTED, // 지침
    GRATEFUL, // 감사
    ;

    companion object {
        fun fromString(value: String?): Emotion {
            if (value == null) return CONFUSED
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                CONFUSED
            }
        }
    }
}
