package github.lms.lemuel.xr.game.domain

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * 게임 세션 도메인.
 *
 * 한 세션 = 한 캐릭터 1회 플레이. decisions 에 Scene 별 사용자 선택 기록.
 * Scene 4 에서 *Scene 3 분배 패턴* 을 참조해 실시간 LLM 프롬프트를 구성한다.
 *
 * 프레임워크 무관(framework-free) — jakarta/spring 임포트 없음. 영속화는
 * `GameSessionPersistenceAdapter` 가 `GameSessionJpaEntity` 와 매핑한다.
 *
 * 팩토리([start]/[reconstitute])로만 생성한다 — 기본 생성자는 private.
 */
class GameSession private constructor() {

    var id: UUID? = null
        private set
    var userId: UUID? = null
        private set
    var appSessionId: UUID? = null
        private set

    /** "joseph" (MVP) */
    var character: String? = null
        private set

    /** "emotional" | "rational" | "spiritual" | null */
    var chosenDimension: String? = null
        private set
    var startedAt: LocalDateTime? = null
        private set
    var completedAt: LocalDateTime? = null
        private set
    var abandonedAt: LocalDateTime? = null
        private set
    var triggeredByEmotionLogId: Long? = null
        private set
    var decisions: MutableMap<String, Any?> = HashMap()
        private set

    /** "farmer_first" | "immigrant_first" | "merchant_first" */
    var finalOutcome: String? = null
        private set
    var closingMessage: String? = null
        private set
    var sceneCountCompleted: Short? = 0
        private set
    var durationSeconds: Int? = null
        private set
    var deviceType: String? = null
        private set
    var capabilities: Map<String, Any?>? = null
        private set
    var assetsManifestVersion: String? = null
        private set

    fun recordDecision(sceneId: Int, value: String?) {
        decisions["scene$sceneId"] = value
    }

    fun recordDecision(sceneId: Int, value: Any?) {
        decisions["scene$sceneId"] = value
    }

    /** 진행한 Scene 카운트를 단조 증가(monotonic)로 갱신. */
    fun advanceSceneCount(sceneId: Int) {
        sceneCountCompleted = maxOf((sceneCountCompleted ?: 0).toInt(), sceneId).toShort()
    }

    /** 세션 완료 — outcome/closingMessage 확정, duration 계산. */
    fun complete(outcome: String?, closingMessage: String?) {
        finalOutcome = outcome
        this.closingMessage = closingMessage
        completedAt = LocalDateTime.now()
        startedAt?.let { durationSeconds = Duration.between(it, completedAt).seconds.toInt() }
    }

    fun complete(outcome: String?) = complete(outcome, null)

    /** emergency exit — abandoned_at + final_outcome='safe_exit:...'. */
    fun exit(reason: String?, atScene: Int?) {
        abandonedAt = LocalDateTime.now()
        finalOutcome = "safe_exit:" + (reason ?: "user_choice")
        if (atScene != null) {
            sceneCountCompleted = atScene.toShort()
        }
    }

    /** 이미 종료(완료 or 포기)된 세션인지. */
    fun isTerminated(): Boolean = completedAt != null || abandonedAt != null

    companion object {
        fun start(userId: UUID?, character: String?): GameSession {
            val s = GameSession()
            s.id = UUID.randomUUID()
            s.userId = userId
            s.character = character
            s.startedAt = LocalDateTime.now()
            return s
        }

        /**
         * 유스케이스가 세션 시작 시 부가 컨텍스트(모드/디바이스/캐퍼빌리티/발원 감정로그)를 채운 뒤
         * 저장하기 위한 팩토리. [start] 의 확장판.
         */
        fun start(
            userId: UUID?,
            character: String?,
            chosenDimension: String?,
            deviceType: String?,
            capabilities: Map<String, Any?>?,
            triggeredByEmotionLogId: Long?,
        ): GameSession {
            val s = start(userId, character)
            s.chosenDimension = chosenDimension
            s.deviceType = deviceType
            s.capabilities = capabilities
            s.triggeredByEmotionLogId = triggeredByEmotionLogId
            return s
        }

        /**
         * 영속 저장소에서 읽은 값으로 도메인 세션을 재구성(reconstitute)한다.
         *
         * 어댑터 매퍼 전용 — 저장된 모든 상태를 손실 없이 복원한다. 신규 세션 생성은
         * [start] 를 쓸 것.
         */
        fun reconstitute(
            id: UUID?,
            userId: UUID?,
            appSessionId: UUID?,
            character: String?,
            chosenDimension: String?,
            startedAt: LocalDateTime?,
            completedAt: LocalDateTime?,
            abandonedAt: LocalDateTime?,
            triggeredByEmotionLogId: Long?,
            decisions: MutableMap<String, Any?>?,
            finalOutcome: String?,
            closingMessage: String?,
            sceneCountCompleted: Short?,
            durationSeconds: Int?,
            deviceType: String?,
            capabilities: Map<String, Any?>?,
            assetsManifestVersion: String?,
        ): GameSession {
            val s = GameSession()
            s.id = id
            s.userId = userId
            s.appSessionId = appSessionId
            s.character = character
            s.chosenDimension = chosenDimension
            s.startedAt = startedAt
            s.completedAt = completedAt
            s.abandonedAt = abandonedAt
            s.triggeredByEmotionLogId = triggeredByEmotionLogId
            s.decisions = decisions ?: HashMap()
            s.finalOutcome = finalOutcome
            s.closingMessage = closingMessage
            s.sceneCountCompleted = sceneCountCompleted
            s.durationSeconds = durationSeconds
            s.deviceType = deviceType
            s.capabilities = capabilities
            s.assetsManifestVersion = assetsManifestVersion
            return s
        }
    }
}
