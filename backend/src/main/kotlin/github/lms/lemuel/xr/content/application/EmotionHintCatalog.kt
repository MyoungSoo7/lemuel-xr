package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.emotion.domain.Emotion
import org.springframework.stereotype.Component

/**
 * 룰 기반 감정 감지용 키워드 카탈로그 (성경 조언 라우팅 목적).
 *
 * 기존 JournalGuidanceController.EMOTION_HINTS 하드코딩을 이관. LLM 대체 — 단순 포함 매칭.
 * 명시 emotion 이 없을 때 일기 텍스트에서 감정을 추정한다.
 */
@Component
class EmotionHintCatalog {

    /**
     * 명시 emotion 우선. 없으면 텍스트에서 룰 기반 키워드 감지. 매칭 없으면 CONFUSED.
     * (기존 JournalGuidanceController.resolveEmotion 규칙과 동일.)
     */
    fun resolve(explicit: String?, text: String?): Emotion {
        if (explicit != null && explicit.isNotBlank()) {
            return Emotion.fromString(explicit)
        }
        if (text == null || text.isBlank()) return Emotion.CONFUSED
        for ((emotion, hints) in EMOTION_HINTS) {
            for (hint in hints) {
                if (text.contains(hint)) return emotion
            }
        }
        return Emotion.CONFUSED
    }

    companion object {
        private val EMOTION_HINTS: Map<Emotion, List<String>> = mapOf(
            Emotion.ANXIOUS to listOf("불안", "걱정", "염려", "초조", "두려", "무서", "긴장"),
            Emotion.SAD to listOf("슬프", "슬픔", "우울", "눈물", "울", "상실", "낙심", "허탈"),
            Emotion.ANGRY to listOf("화", "분노", "짜증", "억울", "미워", "원망", "열받"),
            Emotion.CONFUSED to listOf("혼란", "모르겠", "헷갈", "갈피", "막막", "결정"),
            Emotion.LONELY to listOf("외로", "혼자", "고립", "쓸쓸", "그리워", "소외"),
            Emotion.EXHAUSTED to listOf("지침", "지쳐", "지친", "피곤", "번아웃", "힘들", "탈진", "소진"),
            Emotion.GRATEFUL to listOf("감사", "고마", "다행", "기쁨", "행복", "은혜"),
        )
    }
}
