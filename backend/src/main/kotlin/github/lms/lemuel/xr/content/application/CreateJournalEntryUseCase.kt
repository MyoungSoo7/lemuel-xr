package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.DiaryEntryPort
import github.lms.lemuel.xr.content.domain.DiaryEntry
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/** Theme 1 일기 — 작성/조회 유스케이스. 도메인 모델 구성 로직을 컨트롤러에서 이관. */
@Service
class CreateJournalEntryUseCase(
    private val diaryEntries: DiaryEntryPort,
) {

    fun create(
        userId: UUID,
        text: String?,
        formType: String?,
        emotionLabel: String?,
        intensity: Short?,
    ): DiaryEntry {
        val now = LocalDateTime.now()
        val entry = DiaryEntry(
            id = UUID.randomUUID(),
            userId = userId,
            body = text,
            formType = formType,
            emotionLabel = emotionLabel,
            intensity = intensity,
            wordCount = if (text == null) 0 else text.split(Regex("\\s+")).size,
            meditationText = null,
            meditationAccepted = null,
            createdAt = now,
            updatedAt = now,
        )
        return diaryEntries.save(entry)
    }

    // limit 은 클라이언트가 주므로 그대로 PageRequest 에 넣으면 안 된다.
    // 0·음수는 PageRequest.of 가 예외를 던져 500 이 되고, 큰 값은 무제한 조회가 된다.
    fun list(userId: UUID, limit: Int): List<DiaryEntry> =
        diaryEntries.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit.coerceIn(1, 100)))
}
