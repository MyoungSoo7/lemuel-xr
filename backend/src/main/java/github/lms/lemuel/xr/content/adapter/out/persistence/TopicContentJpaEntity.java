package github.lms.lemuel.xr.content.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** topic_contents (V20260522014700) — AR 1~7 큐레이션 카드. */
@Entity
@Table(name = "topic_contents")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class TopicContentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false)
    private Short topicId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "scripture_ref", length = 50)
    private String scriptureRef;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "anchor_character", length = 20)
    private String anchorCharacter;

    @Column(name = "curator", nullable = false, length = 50)
    private String curator;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "target_emotion", length = 30)
    private String targetEmotion;

    @Column(name = "difficulty")
    private Short difficulty;

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;
}
