package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.common.chatops.TelegramChatOps
import github.lms.lemuel.xr.safety.application.port.out.CrisisResourcePort
import github.lms.lemuel.xr.safety.application.port.out.SafetyAlertPort
import github.lms.lemuel.xr.safety.application.port.out.SafetyMetricsPort
import github.lms.lemuel.xr.safety.domain.CrisisResource
import github.lms.lemuel.xr.safety.domain.SafetyAlert
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 키워드 매칭 → safety_alerts INSERT + 보여줄 crisis_resources 선택.
 * 사용자 노출 텍스트 (alert/disclaimer) 는 lemuel-xr-mental-health-safety skill 가이드 따름.
 */
@Service
class RecordSafetyAlertUseCase(
    private val alerts: SafetyAlertPort,
    private val resources: CrisisResourcePort,
    private val chatOps: TelegramChatOps,
    private val metrics: SafetyMetricsPort,
) {

    @Transactional
    fun execute(
        userId: UUID?,
        emotionLogId: Long?,
        triggerSource: String,
        scan: CrisisKeywordScanner.ScanResult,
    ): Result {
        if (!scan.matched) return Result.notTriggered()

        // 메트릭 — Grafana 안전 row 의 severity 별 카운트.
        metrics.recordAlert(scan.severity, triggerSource)

        // 한국 자원 우선 (region=KR, locale=ko-KR), severity 별 추천
        val active = resources
            .findTop5ByRegionAndLocaleAndActiveTrueOrderByPriorityAsc("KR", "ko-KR")
        val shown = active.map(::toShownMap)

        val saved = alerts.save(
            SafetyAlert(
                id = null,
                userId = userId,
                appSessionId = null,
                emotionLogId = emotionLogId,
                matchedPattern = scan.matchedPattern!!,
                severity = scan.severity!!,
                triggerSource = triggerSource,
                rawExcerptHash = scan.excerptHash,
                shownResources = shown,
                userAcknowledged = false,
                createdAt = LocalDateTime.now(),
            ),
        )

        // high/critical 은 운영자 즉시 알람 — PII 없이 alertId + severity + source + hash 만.
        // ETHICS-LEGAL §2.2: raw text 절대 노출 X. excerptHash 로 사후 추적.
        if (scan.severity in CHATOPS_SEVERITIES) {
            chatOps.notify(
                TelegramChatOps.Severity.CRITICAL,
                "Safety alert " + scan.severity,
                "alertId=" + saved.id +
                    " source=" + triggerSource +
                    " pattern=" + scan.matchedPattern +
                    " hash=" + (scan.excerptHash?.substring(0, 12) ?: "-") +
                    " resources=" + shown.size,
            )
        }

        return Result(true, saved.id, shown)
    }

    private fun toShownMap(r: CrisisResource): Map<String, Any?> =
        mapOf(
            "name" to r.name,
            "contactType" to r.contactType,
            "contactValue" to r.contactValue,
            "hours" to r.hours,
            "category" to r.category,
        )

    data class Result(
        val triggered: Boolean,
        val alertId: Long?,
        val shownResources: List<Map<String, Any?>>,
    ) {
        companion object {
            fun notTriggered(): Result = Result(false, null, emptyList())
        }
    }

    companion object {
        /** 이 severity 이상이면 운영자에게 Telegram 알람. */
        private val CHATOPS_SEVERITIES = setOf("high", "critical")
    }
}
