package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.domain.Emotion

interface ClassifyEmotionUseCase {

    data class Result(val emotion: Emotion, val confidence: Double)

    fun classify(rawText: String): Result
}
