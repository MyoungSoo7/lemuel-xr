package github.lms.lemuel.xr.game.application

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import github.lms.lemuel.xr.game.application.port.out.GameSessionPort
import github.lms.lemuel.xr.game.domain.Character
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
