package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.DiaryEntry
import org.springframework.data.domain.Pageable
import java.util.UUID

/** 일기(Theme 1) 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface DiaryEntryPort {

    fun save(entry: DiaryEntry): DiaryEntry

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<DiaryEntry>
}
