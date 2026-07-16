package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.CreateJournalEntryUseCase
import github.lms.lemuel.xr.content.domain.DiaryEntry
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/** /api/content/journal — Theme 1 일기. */
@RestController
@RequestMapping("/api/content/journal")
@Validated
class JournalController(
    private val createJournalEntry: CreateJournalEntryUseCase,
) {

    @PostMapping
    fun create(@RequestBody req: CreateRequest): ResponseEntity<JournalDto> {
        val e = createJournalEntry.create(
            RequestContext.currentUserId(), req.text, req.formType,
            req.emotionLabel, req.intensity,
        )
        return ResponseEntity.ok(toDto(e))
    }

    @GetMapping
    fun list(@RequestParam(defaultValue = "20") limit: Int): ResponseEntity<JournalListResponse> {
        val items = createJournalEntry
            .list(RequestContext.currentUserId(), limit)
            .map { toDto(it) }
        return ResponseEntity.ok(JournalListResponse(items))
    }

    private fun toDto(e: DiaryEntry): JournalDto =
        JournalDto(
            e.id, e.body, e.formType,
            e.emotionLabel, e.intensity, e.wordCount,
            e.meditationText, e.meditationAccepted,
            e.createdAt,
        )

    data class CreateRequest(
        @field:NotBlank @field:Size(max = 5000) val text: String?,
        @field:Size(max = 20) val formType: String?,
        @field:Size(max = 30) val emotionLabel: String?,
        val intensity: Short?,
    )

    data class JournalDto(
        val id: UUID?,
        val text: String?,
        val formType: String?,
        val emotionLabel: String?,
        val intensity: Short?,
        val wordCount: Int?,
        val meditationText: String?,
        val meditationAccepted: Boolean?,
        val createdAt: LocalDateTime?,
    )

    data class JournalListResponse(val items: List<JournalDto>)
}
