package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 게임 세션 완료. 시나리오의 마지막 scene 에서 *linked_values + value_prompt* 를 추출해
 * 응답에 포함 — 사용자가 *오늘의 AR 가치 실천 prompt* 를 즉시 받음.
 *
 * CROSS-MAPPING-VR-AR.md §3 의 4×7 매핑이 시나리오 yml 의 outro scene 에 박혀있고,
 * 이 use case 가 *VR 세션 끝 → AR 일상 실천* 연결을 만든다.
 */
@Service
class CompleteGameSessionUseCase(
    private val sessions: GameSessionPort,
    private val scenarios: ScenarioYamlLoader,
    private val crisisScanner: CrisisKeywordScanner,
    private val recordSafetyAlert: RecordSafetyAlertUseCase,
) {

    @Transactional
    fun execute(sessionId: UUID, finalOutcome: String?, closingMessage: String?): Result {
        val session = sessions.findById(sessionId)
            .orElseThrow { AppException(ErrorCode.E_SESSION_NOT_FOUND) }
        if (session.isTerminated()) {
            throw AppException(ErrorCode.E_SESSION_INVALID)
        }
        session.complete(finalOutcome, closingMessage)
        sessions.save(session)

        // R1 — 종료 메시지는 사용자 자유 기록이다. 일기·전도서·실천 경로와 동일하게 스캔한다.
        // 이 자리가 특히 위험한 이유: 미션의 감정적 정점이고, 절망 서사(요셉의 13년,
        // 엘리야의 로뎀나무)를 막 통과한 직후 사용자가 *자기 이야기* 를 쓰는 지점이다.
        //
        // 저장을 막지는 않는다. 사용자가 쓴 기록을 삼키면 오히려 해롭고, 이미 본인의
        // 기록이다. 알럿만 남겨 위기 자원 안내가 뜨게 한다.
        val scan = crisisScanner.scan(closingMessage)
        if (scan.matched) {
            recordSafetyAlert.execute(session.userId, null, "game_closing_message", scan)
        }

        // 시나리오의 outro scene 에서 linked_values + value_prompt 추출.
        val vp = extractValuePrompt(session.character)

        return Result(session.id!!, session.completedAt, session.durationSeconds, vp)
    }

    private fun extractValuePrompt(characterStr: String?): ValuePrompt? {
        return try {
            val c = Character.from(characterStr)
            val s = scenarios.forCharacter(c)
            // outro scene 찾기 (next == null 인 scene 또는 마지막)
            val outro = s.scenes.firstOrNull { it.next == null } ?: s.scenes.last()
            val extras = outro.extras ?: return null
            val linkedValues = extras["linked_values"]
            val prompt = extras["value_prompt"]
            if (linkedValues == null && prompt == null) return null
            val values = if (linkedValues is List<*>) {
                linkedValues.filterIsInstance<Int>()
            } else {
                emptyList()
            }
            ValuePrompt(values, prompt?.toString(), characterStr)
        } catch (ex: Exception) {
            null
        }
    }

    /** VR 세션 완료 시 사용자가 받는 AR 가치 실천 prompt. */
    data class ValuePrompt(
        val suggestedValueIds: List<Int>,
        val message: String?,
        val linkedCharacter: String?,
    )

    data class Result(
        val sessionId: UUID,
        val completedAt: LocalDateTime?,
        val durationSeconds: Int?,
        val valuePrompt: ValuePrompt?,
    )
}
