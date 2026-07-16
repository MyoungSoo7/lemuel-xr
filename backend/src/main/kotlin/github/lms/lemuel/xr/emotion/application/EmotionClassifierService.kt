package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.application.port.out.EmotionClassificationPort
import github.lms.lemuel.xr.emotion.domain.Emotion
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * 감정 분류 UseCase 구현 ([ClassifyEmotionUseCase] 도메인 포트) — 사이드카 호출은
 * [EmotionClassificationPort] (DIP) 에 위임하고, 이 서비스는 도메인 매핑(Emotion.fromString)
 * + CONFUSED fallback + 캐싱만 책임.
 *
 * backend 는 도메인·DB 만 책임지고 LLM 호출은 사이드카에 위임. HTTP 세부는 out 포트 뒤의
 * `EmotionClassificationSidecarAdapter` 에 캡슐화 (SRP + DIP — application 은 concrete
 * HTTP 클라이언트를 몰라야 한다).
 *
 * Caffeine 캐시로 동일 텍스트 재호출 차단 (사이드카 호출 + LLM 토큰 모두 절약).
 */
@Service
class EmotionClassifierService(
    private val classification: EmotionClassificationPort,
) : ClassifyEmotionUseCase {

    @Cacheable(value = ["emotion-classify"], key = "#rawText")
    override fun classify(rawText: String): ClassifyEmotionUseCase.Result {
        val resp = classification.classify(rawText)
            ?: return ClassifyEmotionUseCase.Result(Emotion.CONFUSED, 0.0)
        return ClassifyEmotionUseCase.Result(Emotion.fromString(resp.emotion), resp.confidence)
    }
}
