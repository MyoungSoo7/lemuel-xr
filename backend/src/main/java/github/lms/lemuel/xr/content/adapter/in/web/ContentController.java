package github.lms.lemuel.xr.content.adapter.in.web;

import github.lms.lemuel.xr.content.domain.Topic;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /api/content/topics — 7 주제 카탈로그. 인증 불필요. */
@RestController
@RequestMapping("/api/content")
public class ContentController {

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
        // MVP 단계: 정적 페이로드. 실제 자산은 R2 manifest 에서 옴.
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

    public record TopicsResponse(List<TopicDto> topics) {}
    public record TopicDto(int id, String key, String title) {}
    public record TopicSceneDto(int topicId, String title, String mode,
                                 SceneAssetsDto scene, int estimatedDurationSec) {}
    public record SceneAssetsDto(String skybox, String bgmId, String narrationId) {}
}
