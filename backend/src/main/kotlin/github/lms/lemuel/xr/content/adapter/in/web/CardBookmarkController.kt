package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.CardBookmarkService
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/**
 * /api/content/bookmarks — AR 토픽 카드 북마크(담기/빼기/내 목록).
 * 소유자 = RequestContext.currentUserId() (익명 게스트 포함, 익명 우선 P1).
 */
@RestController
@RequestMapping("/api/content/bookmarks")
@Validated
class CardBookmarkController(
    private val service: CardBookmarkService,
) {

    /** 담기 — 멱등(이미 담겨 있으면 기존 반환). */
    @PostMapping
    fun add(@RequestBody req: AddRequest): ResponseEntity<BookmarkDto> {
        val b = service.add(RequestContext.currentUserId(), req.topicContentId!!)
        return ResponseEntity.ok(BookmarkDto(b.id, b.topicContentId, b.createdAt))
    }

    /** 빼기 — 토글. */
    @DeleteMapping("/{topicContentId}")
    fun remove(@PathVariable topicContentId: Long): ResponseEntity<Void> {
        service.remove(RequestContext.currentUserId(), topicContentId)
        return ResponseEntity.noContent().build()
    }

    /** '내 북마크' 목록 — 최신순. */
    @GetMapping
    fun list(): ResponseEntity<List<BookmarkedCardDto>> {
        val cards = service.list(RequestContext.currentUserId()).map {
            BookmarkedCardDto(
                it.topicContentId, it.topicId, it.title,
                it.scriptureRef, it.body, it.anchorCharacter, it.bookmarkedAt,
            )
        }
        return ResponseEntity.ok(cards)
    }

    data class AddRequest(
        @field:NotNull val topicContentId: Long?,
    )

    data class BookmarkDto(
        val id: UUID?,
        val topicContentId: Long,
        val createdAt: LocalDateTime?,
    )

    data class BookmarkedCardDto(
        val topicContentId: Long,
        val topicId: Short?,
        val title: String,
        val scriptureRef: String?,
        val body: String,
        val anchorCharacter: String?,
        val bookmarkedAt: LocalDateTime,
    )
}
