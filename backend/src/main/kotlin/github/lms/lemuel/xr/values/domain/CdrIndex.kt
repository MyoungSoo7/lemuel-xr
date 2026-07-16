package github.lms.lemuel.xr.values.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Civil Defense Readiness Index — CROSS-MAPPING-VR-AR.md §6.
 *
 * 불변 값 객체. 최근 7일 실천 건수로부터 0~100 지수와 tier 를 계산한다.
 *
 * 49 = 7 가치 × 7일. 100 = 매일 7 가치 모두 실천. 0 = 7일간 실천 0건.
 *
 * @property value 0~100 으로 정규화된 CDR 지수
 * @property tier  지수 구간 이름 (VERY_READY / NORMAL / BUILD_UP)
 */
data class CdrIndex(val value: Int, val tier: String) {

    companion object {
        private const val MAX_POSSIBLE = 49

        /** 최근 7일 실천 건수로부터 CDR Index 를 계산한다. */
        fun fromPractices(totalPractices: Int): CdrIndex {
            val raw = min(MAX_POSSIBLE, totalPractices) * 100 / MAX_POSSIBLE
            val value = max(0, min(100, raw))
            return CdrIndex(value, tierOf(value))
        }

        private fun tierOf(cdr: Int): String = when {
            cdr >= 80 -> "VERY_READY"
            cdr >= 50 -> "NORMAL"
            else -> "BUILD_UP"
        }
    }
}
