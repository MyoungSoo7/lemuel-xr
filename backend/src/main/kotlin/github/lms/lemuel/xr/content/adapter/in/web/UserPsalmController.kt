package github.lms.lemuel.xr.content.adapter.`in`.web

import github.lms.lemuel.xr.common.web.RequestContext
import github.lms.lemuel.xr.content.application.CreateUserPsalmUseCase
import github.lms.lemuel.xr.content.domain.UserPsalm
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.UUID

/** /api/content/psalms — Theme 4 사용자 시편 작성. */
@RestController
@RequestMapping("/api/content/psalms")
@Validated
class UserPsalmController(
    private val createUserPsalm: CreateUserPsalmUseCase,
) {

    @PostMapping
    fun create(@RequestBody req: CreateRequest): ResponseEntity<PsalmDto> {
        val p = createUserPsalm.create(
            RequestContext.currentUserId(), req.text, req.form, req.inspiredBy,
        )
        return ResponseEntity.ok(toDto(p))
    }

    private fun toDto(p: UserPsalm): PsalmDto =
        PsalmDto(
            p.id, p.psalmForm, p.rawText,
            p.polishedText, p.acceptedPolished, p.inspiredByPsalm,
            p.createdAt,
        )

    data class CreateRequest(
        @field:NotBlank @field:Size(max = 5000) val text: String?,
        @field:Size(max = 20) val form: String?,
        @field:Size(max = 20) val inspiredBy: String?,
    )

    data class PsalmDto(
        val id: UUID?,
        val form: String?,
        val rawText: String?,
        val polishedText: String?,
        val acceptedPolished: Boolean?,
        val inspiredBy: String?,
        val createdAt: LocalDateTime?,
    )
}
