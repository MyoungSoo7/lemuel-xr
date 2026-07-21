package github.lms.lemuel.xr.ai.grounding.application.port.out

/**
 * 텍스트 → 임베딩 벡터 out-port. 반환은 입력과 같은 순서. 실패 시 예외를 던지며,
 * 호출자(EvaluateGroundingUseCase)가 이를 INCONCLUSIVE 로 처리한다. (DIP: 구현 격리)
 */
interface EmbeddingPort {
    fun embed(texts: List<String>): List<FloatArray>
}
