package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.domain.GameSession
import java.util.UUID

/**
 * 세션 소유권 집행 (IDOR 차단).
 *
 * sessionId 는 UUID 라 추측이 어렵지만 *비밀이 아니다* — 응답 본문·로그·클라이언트 저장소에 남는다.
 * 그래서 `/decide` `/complete` `/exit` 는 sessionId 만으로 남의 세션을 조작할 수 있었다.
 *
 * 두 가지를 의도적으로 이렇게 뒀다:
 *
 * 1. **404 로 응답한다** (403 아님). 403 은 "그 세션은 존재하지만 네 것이 아니다" 를 알려줘
 *    세션 존재 여부를 유출한다. 남의 세션과 없는 세션은 호출자에게 구분되지 않아야 한다.
 *
 * 2. **[userId] 는 non-null 이다.** `GameSession.userId` 는 nullable 이라
 *    `session.userId == userId` 를 양쪽 nullable 로 비교하면 *둘 다 null 일 때 통과*한다.
 *    호출자가 `RequestContext.currentUserId()`(미인증 시 E_AUTH_REQUIRED) 를 넘기므로
 *    여기 도달한 [userId] 는 절대 null 이 아니고, userId 없는 레거시 세션은 항상 거부된다.
 */
internal fun requireOwner(session: GameSession, userId: UUID) {
    if (session.userId != userId) {
        throw AppException(ErrorCode.E_SESSION_NOT_FOUND)
    }
}
