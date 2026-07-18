package github.lms.lemuel.xr.ai.grounding.domain

import kotlin.math.sqrt

/**
 * 두 임베딩 벡터의 코사인 유사도 — 순수 함수. 프레임워크 무관.
 * 크기 0 벡터는 NaN 대신 0.0 을 반환한다(근거 없음으로 취급).
 */
object CosineSimilarity {
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "벡터 길이 불일치: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            na += a[i].toDouble() * a[i]
            nb += b[i].toDouble() * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
