package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.EcclesiastesViewPort
import github.lms.lemuel.xr.content.domain.EcclesiastesView
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 기준4 전도서 view — 작성/조회 유스케이스.
 *
 * R1(법적 의무) 위기 키워드 스캔 + safety_alert 기록 + 도메인 모델 구성을 컨트롤러에서 이관.
 * 전도서 특성상 nihilism 강화 위험(§4.4)이 커 futility/meaning 자유 기록 양쪽을 스캔한다.
 */
@Service
class EcclesiastesViewUseCase(
    private val views: EcclesiastesViewPort,
    private val crisisScanner: CrisisKeywordScanner,
    private val recordSafetyAlert: RecordSafetyAlertUseCase,
) {

    fun create(
        userId: UUID,
        chapterRef: String?,
        userSeason: String?,
        futilityNote: String?,
        meaningNote: String?,
        listenedAudio: Boolean?,
        conclusionViewed: Boolean?,
    ): CreateResult {
        // R1 (법적 의무): 헛됨/의미 자유 기록 양쪽 모두 스캔. 전도서 특성상 nihilism 강화 위험(§4.4).
        val scan = crisisScanner.scan(joinNotes(futilityNote, meaningNote))
        val alert = recordSafetyAlert.execute(userId, null, "ecclesiastes", scan)

        val view = EcclesiastesView(
            id = null,
            userId = userId,
            chapterRef = chapterRef,
            userSeason = userSeason,
            futilityNote = futilityNote,
            meaningNote = meaningNote,
            listenedAudio = listenedAudio == true,
            conclusionViewed = conclusionViewed == true,
            createdAt = LocalDateTime.now(),
        )
        val saved = views.save(view)

        return CreateResult(saved, alert)
    }

    fun list(userId: UUID, limit: Int): List<EcclesiastesView> =
        views.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(0, limit.coerceIn(1, 100)),
        )

    fun conclusionViewedCount(userId: UUID): Long =
        views.countByUserIdAndConclusionViewedTrue(userId)

    private fun joinNotes(a: String?, b: String?): String? {
        if (a == null) return b
        if (b == null) return a
        return "$a\n$b"
    }

    /** 작성 결과 — 저장된 view + safety_alert 결과(위기 라우팅 신호). */
    data class CreateResult(val view: EcclesiastesView, val alert: RecordSafetyAlertUseCase.Result)
}
