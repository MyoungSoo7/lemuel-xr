package github.lms.lemuel.xr.emotion.application;

import github.lms.lemuel.xr.emotion.domain.Emotion;

public interface ClassifyEmotionUseCase {
    record Result(Emotion emotion, double confidence) {}

    Result classify(String rawText);
}
