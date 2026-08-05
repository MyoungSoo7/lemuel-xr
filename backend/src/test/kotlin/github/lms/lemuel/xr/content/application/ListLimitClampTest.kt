package github.lms.lemuel.xr.content.application

import github.lms.lemuel.xr.content.application.port.out.DiaryEntryPort
import github.lms.lemuel.xr.content.application.port.out.PracticeReflectionPort
import github.lms.lemuel.xr.safety.application.CrisisKeywordScanner
import github.lms.lemuel.xr.safety.application.RecordSafetyAlertUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * 목록 조회 limit 은 클라이언트가 주는 값이다. 그대로 PageRequest 에 넘기면
 *   - 0·음수 → PageRequest.of 가 IllegalArgumentException → 500
 *   - 아주 큰 값 → 사실상 무제한 조회
 * 가 된다. 두 유스케이스 모두 1..100 으로 조인다.
 *
 * 남의 데이터를 보는 문제는 아니다(쿼리가 이미 userId 로 스코프됨). 자기 요청으로
 * 자기 서버를 깨뜨리는 쪽이라 심각도는 낮지만, 형제 유스케이스들과 동작이 갈리는 게 문제였다.
 */
class ListLimitClampTest {

    private val userId: UUID = UUID.randomUUID()

    // ---- 일기(Theme 1) ----

    private val diaries: DiaryEntryPort = mock()
    private val journalUc = CreateJournalEntryUseCase(diaries)

    private fun journalPageSizeFor(limit: Int): Int {
        val captor = argumentCaptor<Pageable>()
        whenever(diaries.findByUserIdOrderByCreatedAtDesc(any(), any())).thenReturn(emptyList())
        journalUc.list(userId, limit)
        verify(diaries).findByUserIdOrderByCreatedAtDesc(eq(userId), captor.capture())
        return captor.firstValue.pageSize
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    fun `일기 - 0 이하 limit 은 예외 대신 1 로 조여진다`(limit: Int) {
        assertThat(journalPageSizeFor(limit)).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [101, 100_000_000, Int.MAX_VALUE])
    fun `일기 - 과도한 limit 은 100 으로 조여진다`(limit: Int) {
        assertThat(journalPageSizeFor(limit)).isEqualTo(100)
    }

    @Test
    fun `일기 - 정상 범위 limit 은 그대로 통과한다`() {
        assertThat(journalPageSizeFor(20)).isEqualTo(20)
    }

    // ---- 실천·성찰(Theme 6·7) ----

    private val reflections: PracticeReflectionPort = mock()
    private val practiceUc = PracticeReflectionUseCase(
        reflections,
        mock<CrisisKeywordScanner>(),
        mock<RecordSafetyAlertUseCase>(),
    )

    private fun practicePageSizeFor(limit: Int): Int {
        val captor = argumentCaptor<Pageable>()
        whenever(
            reflections.findByUserIdAndTopicIdOrderByCreatedAtDesc(any(), any(), any()),
        ).thenReturn(emptyList())
        practiceUc.list(userId, 6, limit)
        verify(reflections).findByUserIdAndTopicIdOrderByCreatedAtDesc(
            eq(userId), eq(6.toShort()), captor.capture(),
        )
        return captor.firstValue.pageSize
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    fun `실천 - 0 이하 limit 은 예외 대신 1 로 조여진다`(limit: Int) {
        assertThat(practicePageSizeFor(limit)).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [101, 100_000_000, Int.MAX_VALUE])
    fun `실천 - 과도한 limit 은 100 으로 조여진다`(limit: Int) {
        assertThat(practicePageSizeFor(limit)).isEqualTo(100)
    }

    @Test
    fun `실천 - 정상 범위 limit 은 그대로 통과한다`() {
        assertThat(practicePageSizeFor(20)).isEqualTo(20)
    }
}
