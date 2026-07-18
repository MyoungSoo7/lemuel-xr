package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import github.lms.lemuel.xr.ai.grounding.application.port.out.GroundingMetricsPort
import github.lms.lemuel.xr.ai.grounding.domain.CosineSimilarity
import github.lms.lemuel.xr.ai.grounding.domain.GroundingPolicy
import github.lms.lemuel.xr.ai.grounding.domain.GroundingStatus
import github.lms.lemuel.xr.ai.grounding.domain.GroundingVerdict
import github.lms.lemuel.xr.ai.grounding.domain.SentenceGrounding
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 섀도우 근거성 게이트 — 생성 묵상의 각 문장이 주어진 성경 본문에 임베딩 근거를 갖는지 판정.
 * 사용자 노출/차단 없음. 판정 + 메트릭만 산출한다. (application: domain + out-port 만 의존, JPA 무접촉)
 *
 * 증거는 scripture 컨텍스트와 결합하지 않도록 로컬 [Passage] 로 받는다(호출자가 매핑).
 */
@Service
class EvaluateGroundingUseCase(
    private val embeddings: EmbeddingPort,
    private val metrics: GroundingMetricsPort,
) {
    data class Passage(val reference: String, val text: String)

    fun evaluate(
        purpose: String,
        meditationText: String,
        passages: List<Passage>,
        policy: GroundingPolicy,
    ): GroundingVerdict {
        metrics.evaluated(purpose)
        val sentences = SentenceSplitter.split(meditationText)
        if (sentences.isEmpty()) {
            metrics.inconclusive(purpose)
            return GroundingVerdict(GroundingStatus.INCONCLUSIVE, 0.0, emptyList(), policy.similarityThreshold)
        }
        if (passages.isEmpty()) {
            return GroundingVerdict(
                GroundingStatus.NO_EVIDENCE,
                1.0,
                sentences.map { SentenceGrounding(it, 0.0, false, null) },
                policy.similarityThreshold,
            )
        }

        val sentenceVecs: List<FloatArray>
        val passageVecs: List<FloatArray>
        try {
            sentenceVecs = embeddings.embed(sentences)
            passageVecs = embeddings.embed(passages.map { it.text })
        } catch (e: Exception) {
            log.warn("임베딩 실패 → INCONCLUSIVE (purpose={}): {}", purpose, e.message)
            metrics.inconclusive(purpose)
            return GroundingVerdict(GroundingStatus.INCONCLUSIVE, 0.0, emptyList(), policy.similarityThreshold)
        }

        val results = sentences.mapIndexed { i, sentence ->
            var best = -1.0
            var bestRef: String? = null
            passageVecs.forEachIndexed { j, pv ->
                val sim = CosineSimilarity.cosine(sentenceVecs[i], pv)
                if (sim > best) {
                    best = sim
                    bestRef = passages[j].reference
                }
            }
            SentenceGrounding(sentence, best, best >= policy.similarityThreshold, bestRef)
        }

        val ungrounded = results.count { !it.grounded }
        val rate = ungrounded.toDouble() / results.size
        metrics.unsupportedRate(purpose, rate)
        val status = policy.verdictStatus(rate)
        if (status == GroundingStatus.REJECTED) metrics.rejected(purpose)
        return GroundingVerdict(status, rate, results, policy.similarityThreshold)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EvaluateGroundingUseCase::class.java)
    }
}
