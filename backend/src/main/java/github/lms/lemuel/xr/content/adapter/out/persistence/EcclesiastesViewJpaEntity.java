package github.lms.lemuel.xr.content.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ecclesiastes_views")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EcclesiastesViewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chapter_ref", length = 20)
    private String chapterRef;

    @Column(name = "user_season", length = 20)
    private String userSeason;

    @Column(name = "futility_note", columnDefinition = "text")
    private String futilityNote;

    @Column(name = "meaning_note", columnDefinition = "text")
    private String meaningNote;

    @Column(name = "listened_audio")
    private Boolean listenedAudio = false;

    @Column(name = "conclusion_viewed")
    private Boolean conclusionViewed = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
