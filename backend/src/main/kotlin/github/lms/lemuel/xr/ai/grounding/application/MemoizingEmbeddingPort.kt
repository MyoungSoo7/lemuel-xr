package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort
import java.util.concurrent.ConcurrentHashMap

/**
 * 텍스트 단위 메모이즈 [EmbeddingPort] 데코레이터.
 *
 * 골든셋 한 번 실행에서 같은 성경 본문이 여러 픽스처에 반복 등장하고, 임계치 스윕은 같은 텍스트를
 * 수백 가지 정책으로 재평가한다. 정책은 코사인 **비교 기준**만 바꾸므로 벡터는 한 번만 구하면 된다.
 * 이 데코레이터가 없으면 격자 크기만큼 임베딩 API 를 곱해 호출하게 되고, 비용도 문제지만
 * 무엇보다 스윕이 사실상 불가능해진다.
 *
 * ⚠️ 수명은 **실행 1회**로 한정할 것. 실행 간에 캐시를 살려 두면 임베딩 모델이 바뀌어도 옛 벡터를
 * 그대로 써서, 정작 감시해야 할 모델 드리프트(승격계약 P6)를 스스로 가려 버린다.
 */
class MemoizingEmbeddingPort(private val delegate: EmbeddingPort) : EmbeddingPort {

    private val cache = ConcurrentHashMap<String, FloatArray>()

    /** 위임(=실제 API) 호출로 새로 임베딩한 텍스트 수. 비용 근거로 리포트·로그에 남긴다. */
    @Volatile
    var embeddedTexts: Int = 0
        private set

    val cachedTexts: Int get() = cache.size

    override fun embed(texts: List<String>): List<FloatArray> {
        val missing = texts.filterNot { cache.containsKey(it) }.distinct()
        if (missing.isNotEmpty()) {
            val fresh = delegate.embed(missing)
            check(fresh.size == missing.size) {
                "임베딩 응답 개수 불일치: 요청 ${missing.size} / 응답 ${fresh.size} — 순서 보장이 깨졌다"
            }
            missing.forEachIndexed { i, text -> cache[text] = fresh[i] }
            embeddedTexts += missing.size
        }
        return texts.map { cache.getValue(it) }
    }
}
