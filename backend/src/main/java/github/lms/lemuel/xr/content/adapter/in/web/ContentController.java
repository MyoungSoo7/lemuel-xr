package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentJpaEntity;
import github.lms.lemuel.xr.content.adapter.out.persistence.TopicContentRepository;
import github.lms.lemuel.xr.content.domain.Topic;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/content/topics — 7 주제 카탈로그 + 큐레이션 카드. 인증 불필요. */
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final TopicContentRepository topicContents;

    @GetMapping("/topics")
    public ResponseEntity<TopicsResponse> topics() {
        List<TopicDto> items = Arrays.stream(Topic.values())
                .map(t -> new TopicDto(t.id(), t.name(), t.title()))
                .toList();
        return ResponseEntity.ok(new TopicsResponse(items));
    }

    @GetMapping("/topics/{topicId}/scene")
    public ResponseEntity<TopicSceneDto> scene(@PathVariable int topicId,
                                                @RequestParam(defaultValue = "AUTO") String mode) {
        Topic t = Topic.byId(topicId);
        return ResponseEntity.ok(new TopicSceneDto(
                topicId, t.title(), mode,
                new SceneAssetsDto(
                        "skybox-" + t.name().toLowerCase() + ".exr",
                        "bgm-soft",
                        "narration-" + t.name().toLowerCase() + "-" + mode.toLowerCase()
                ),
                240
        ));
    }

    /**
     * /api/content/topics/{topicId}/cards — 큐레이션 카드 목록.
     * AR 1~7 일상 영적 양식의 핵심 콘텐츠.
     */
    @GetMapping("/topics/{topicId}/cards")
    public ResponseEntity<CardsResponse> cards(@PathVariable int topicId,
                                                  @RequestParam(required = false) String emotion,
                                                  @RequestParam(defaultValue = "5") int limit) {
        Topic.byId(topicId);   // 검증
        List<CardDto> items = topicContents
                .findRelevant((short) topicId, emotion, PageRequest.of(0, Math.min(limit, 20)))
                .stream().map(this::toCard).toList();
        return ResponseEntity.ok(new CardsResponse(topicId, items));
    }

    private CardDto toCard(TopicContentJpaEntity e) {
        return new CardDto(
                e.getId(), e.getTopicId(), e.getTitle(),
                e.getScriptureRef(), e.getBody(),
                e.getAnchorCharacter(), e.getTargetEmotion(), e.getDifficulty(),
                e.getPublishedAt()
        );
    }

    // --- DTOs ---

    public record TopicsResponse(List<TopicDto> topics) {}
    public record TopicDto(int id, String key, String title) {}
    public record TopicSceneDto(int topicId, String title, String mode,
                                 SceneAssetsDto scene, int estimatedDurationSec) {}
    public record SceneAssetsDto(String skybox, String bgmId, String narrationId) {}

    public record CardsResponse(int topicId, List<CardDto> cards) {}
    public record CardDto(Long id, Short topicId, String title,
                            String scriptureRef, String body,
                            String anchorCharacter, String targetEmotion,
                            Short difficulty, OffsetDateTime publishedAt) {}
}
