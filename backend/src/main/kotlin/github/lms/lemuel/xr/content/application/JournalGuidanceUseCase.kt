package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.adapter.`in`.web.JournalGuidanceCatalog
import github.lms.lemuel.xr.emotion.domain.Emotion
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 기준1 일기와 묵상 — 성경 기반 규칙형 조언 유스케이스.
 *
 * R1(법적 의무) 위기 키워드 스캔 + safety_alert 기록 + 룰 기반 감정 감지를 컨트롤러에서 이관.
 * LLM 미사용 — 명시 emotion 우선 → 룰 키워드 감지 → CONFUSED fallback.
 */
@Service
class JournalGuidanceUseCase(
    private val crisisScanner: CrisisKeywordScanner,
    private val recordSafetyAlert: RecordSafetyAlertUseCase,
    private val emotionHints: EmotionHintCatalog,
) {

    /** 감정으로 성경 기반 조언 조회 (카탈로그 조회 — 위기 스캔 없음). */
    fun forEmotion(emotion: String?): JournalGuidanceCatalog.Guidance =
        JournalGuidanceCatalog.forEmotion(Emotion.fromString(emotion))

    /**
     * 일기 텍스트 → 조언. R1: 텍스트를 위기 키워드 스캔 후, 감정 감지 →
     * 성경 구절 + 성찰 질문 반환. 위기 시 상담 자원 동반.
     */
    fun fromText(userId: UUID, text: String?, emotion: String?): FromTextResult {
        // R1 (법적 의무): 일기 텍스트에 자해·자살 키워드가 있으면 즉시 위기 자원 라우팅.
        val scan = crisisScanner.scan(text)
        val alert = recordSafetyAlert.execute(userId, null, "journal_guidance", scan)

        // 감정: 명시 우선 → 룰 기반 키워드 감지 → CONFUSED fallback (LLM 미사용).
        val resolved = emotionHints.resolve(emotion, text)
        val g = JournalGuidanceCatalog.forEmotion(resolved)

        // R1: 위기 매칭이면 카탈로그의 상담 자원을 함께(자원 record 형태 통일).
        val resources: List<Map<String, Any?>> = if (alert.triggered) {
            if (alert.shownResources.isEmpty()) JournalGuidanceCatalog.CRISIS_RESOURCES else alert.shownResources
        } else {
            emptyList()
        }

        return FromTextResult(g, alert.triggered, resources)
    }

    /** 텍스트 조언 결과 — 조언 + 위기 라우팅 신호 + 함께 보일 상담 자원. */
    data class FromTextResult(
        val guidance: JournalGuidanceCatalog.Guidance,
        val crisisRouted: Boolean,
        val resources: List<Map<String, Any?>>,
    )
}
