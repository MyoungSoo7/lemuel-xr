package github.lms.lemuel.xr.content.application.port.out

import github.lms.lemuel.xr.content.domain.UserPsalm

/** 사용자 시편(Theme 4) 영속 아웃바운드 포트 — 앱이 실제 호출하는 메서드만 노출 (ISP). */
interface UserPsalmPort {

    fun save(psalm: UserPsalm): UserPsalm
}
