package github.lms.lemuel.xr.values.application

import github.lms.lemuel.xr.values.application.port.out.UserValueProfilePort
import github.lms.lemuel.xr.values.domain.UserValueProfile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** 사용자 7 가치 정의 (전체 또는 부분). */
@Service
class UpdateValueProfileUseCase(
    private val profiles: UserValueProfilePort,
) {

    @Transactional
    fun execute(userId: UUID, valuesPatch: Map<String, Any?>): UserValueProfile {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val existing = profiles.findByUserId(userId).orElse(null)

        val id = existing?.id ?: UUID.randomUUID()
        val startedAt = existing?.startedAt ?: now

        // 부분 갱신 — patch 의 키만 덮어씀. value 가 null 이면 삭제.
        val current = HashMap<String, Any?>(existing?.valuesJson ?: emptyMap())
        for ((key, value) in valuesPatch) {
            if (value == null) {
                current.remove(key)
            } else {
                current[key] = value
            }
        }

        val updated = UserValueProfile(id, userId, current, startedAt, now)
        return profiles.save(updated)
    }
}
