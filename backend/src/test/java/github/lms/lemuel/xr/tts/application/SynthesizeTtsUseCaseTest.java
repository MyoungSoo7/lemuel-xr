package github.lms.lemuel.xr.tts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.lms.lemuel.xr.tts.adapter.out.persistence.TtsCacheJpaEntity;
import github.lms.lemuel.xr.tts.adapter.out.persistence.TtsCacheRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SynthesizeTtsUseCase 단위 테스트 — 캐시 히트 / 미스(사이드카 호출) / 키 안정성 분기 커버.
 *
 * <p>외부 TTS 엔진({@link TtsSidecarClient}) 은 Mockito 로 목킹 — 실제 오디오 생성 없음.</p>
 */
@ExtendWith(MockitoExtension.class)
class SynthesizeTtsUseCaseTest {

    @Mock TtsCacheRepository cache;
    @Mock TtsSidecarClient sidecar;
    @InjectMocks SynthesizeTtsUseCase uc;

    @Test
    void 캐시_미스면_사이드카_호출하고_저장_그리고_cached_false() {
        when(cache.findById(anyString())).thenReturn(Optional.empty());
        when(sidecar.synthesize("안녕", "narrator-male-low", 1.0))
                .thenReturn(new TtsSidecarClient.SynthesisResult(
                        "https://r2/audio/abc.wav", 1500, "xtts-v2"));

        var r = uc.execute("안녕", "narrator-male-low", 1.0);

        assertThat(r.cached()).isFalse();
        assertThat(r.audioUrl()).isEqualTo("https://r2/audio/abc.wav");
        assertThat(r.durationMs()).isEqualTo(1500);

        ArgumentCaptor<TtsCacheJpaEntity> captor = ArgumentCaptor.forClass(TtsCacheJpaEntity.class);
        verify(cache).save(captor.capture());
        TtsCacheJpaEntity saved = captor.getValue();
        assertThat(saved.getCacheKey()).hasSize(64); // sha256 hex
        assertThat(saved.getVoiceId()).isEqualTo("narrator-male-low");
        assertThat(saved.getEngine()).isEqualTo("xtts-v2");
        assertThat(saved.getAudioUrl()).isEqualTo("https://r2/audio/abc.wav");
        assertThat(saved.getHitCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 캐시_히트면_사이드카_호출없이_hitCount_증가_그리고_cached_true() {
        var existing = new TtsCacheJpaEntity();
        existing.setCacheKey("key");
        existing.setAudioUrl("https://r2/audio/cached.wav");
        existing.setDurationMs(2000);
        existing.setHitCount(4);
        when(cache.findById(anyString())).thenReturn(Optional.of(existing));

        var r = uc.execute("안녕", "narrator-male-low", 1.0);

        assertThat(r.cached()).isTrue();
        assertThat(r.audioUrl()).isEqualTo("https://r2/audio/cached.wav");
        assertThat(r.durationMs()).isEqualTo(2000);
        assertThat(existing.getHitCount()).isEqualTo(5);
        assertThat(existing.getLastHitAt()).isNotNull();
        verify(sidecar, never()).synthesize(any(), any(), any());
        verify(cache, never()).save(any());
    }

    @Test
    void 동일_입력은_동일_캐시키_다른_입력은_다른키() {
        when(cache.findById(anyString())).thenReturn(Optional.empty());
        when(sidecar.synthesize(any(), any(), any()))
                .thenReturn(new TtsSidecarClient.SynthesisResult("u", 1, "e"));

        uc.execute("텍스트A", "voice1", 1.0);
        uc.execute("텍스트A", "voice1", 1.0);
        uc.execute("텍스트B", "voice1", 1.0);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cache, org.mockito.Mockito.times(3)).findById(keys.capture());
        var captured = keys.getAllValues();
        assertThat(captured.get(0)).isEqualTo(captured.get(1)); // 동일 입력 → 동일 키
        assertThat(captured.get(0)).isNotEqualTo(captured.get(2)); // 다른 텍스트 → 다른 키
    }

    @Test
    void null_voiceId_와_null_rate_도_안정적_키_생성() {
        when(cache.findById(anyString())).thenReturn(Optional.empty());
        when(sidecar.synthesize("안녕", null, null))
                .thenReturn(new TtsSidecarClient.SynthesisResult("u", 1, "e"));

        var r = uc.execute("안녕", null, null);

        assertThat(r.cached()).isFalse();
        verify(cache).save(any());
    }
}
