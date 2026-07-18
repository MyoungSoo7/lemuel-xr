package github.lms.lemuel.xr.ai.grounding.application

import github.lms.lemuel.xr.ai.grounding.application.port.out.EmbeddingPort

/**
 * 결정론적 테스트용 임베더. 알려진 텍스트는 지정 벡터로, 미지 텍스트는 onMissing 으로.
 * failNext=true 면 다음 embed 호출에서 예외(임베딩 실패 경로 검증용).
 */
class FakeEmbeddingAdapter(
    private val vectors: Map<String, FloatArray>,
    private val onMissing: (String) -> FloatArray = { floatArrayOf(0f, 0f, 0f) },
) : EmbeddingPort {
    var failNext: Boolean = false

    override fun embed(texts: List<String>): List<FloatArray> {
        if (failNext) throw RuntimeException("embedding backend down")
        return texts.map { vectors[it] ?: onMissing(it) }
    }
}
