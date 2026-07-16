package github.lms.lemuel.xr.emotion.application

import github.lms.lemuel.xr.emotion.domain.Emotion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 2026-05-22 mission 최종 정착 — *영적 비상 대비 훈련* (큐티+민방위).
 * 4 인물 (JOSEPH·MOSES·DAVID·JESUS) 모두 동등한 교사. JOB·ELIJAH 는 보조.
 */
class EmotionRecommenderTest {

    private val r = EmotionRecommender()

    @Test
    fun `ANXIOUS 는 시편 또는 마음 topic 추천`() {
        val topics = r.trackA(Emotion.ANXIOUS)
        assertThat(topics).extracting<Int> { it.topicId }
            .contains(4, 6)
    }

    @Test
    fun `ANXIOUS 는 MOSES 떨면서 한 발`() {
        val chars = r.trackB(Emotion.ANXIOUS)
        assertThat(chars).extracting<String> { it.character }
            .contains("MOSES")
    }

    @Test
    fun `EXHAUSTED 는 MOSES 광야 40년 핵심`() {
        val chars = r.trackB(Emotion.EXHAUSTED)
        assertThat(chars).extracting<String> { it.character }
            .contains("MOSES")
    }

    @Test
    fun `GRATEFUL 은 JOSEPH 복귀`() {
        // 요셉 복귀 — 4 인물 모두 동등. 감사·회복 결에는 요셉의 *섭리 안의 회복* 핵심.
        val chars = r.trackB(Emotion.GRATEFUL)
        assertThat(chars).extracting<String> { it.character }
            .contains("JOSEPH")
    }

    @Test
    fun `CONFUSED 는 JOSEPH 꿈해석 또는 DAVID 정체성`() {
        val chars = r.trackB(Emotion.CONFUSED)
        assertThat(chars).extracting<String> { it.character }
            .containsAnyOf("JOSEPH", "DAVID")
    }

    @Test
    fun `SAD 는 DAVID 시편 비탄`() {
        val chars = r.trackB(Emotion.SAD)
        assertThat(chars).extracting<String> { it.character }
            .contains("DAVID")
    }

    @Test
    fun `모든 감정에 대해 TrackA TrackB 빈 리스트 아님`() {
        for (e in Emotion.entries) {
            assertThat(r.trackA(e)).isNotEmpty()
            assertThat(r.trackB(e)).isNotEmpty()
        }
    }

    @Test
    fun `모든 감정에 4 인물 중 최소 하나는 추천`() {
        // 4 인물 동등 원칙 — 사용자가 어떤 감정에 진입해도 4명 중 누군가는 만난다.
        for (e in Emotion.entries) {
            val chars = r.trackB(e)
            assertThat(chars).extracting<String> { it.character }
                .containsAnyOf("JOSEPH", "MOSES", "DAVID", "JESUS")
        }
    }
}
