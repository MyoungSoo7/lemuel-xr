package github.lms.lemuel.xr.content.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_psalms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserPsalmJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "psalm_form", length = 20)
    private String psalmForm;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(name = "raw_text_encrypted")
    private byte[] rawTextEncrypted;

    @Column(name = "polished_text", columnDefinition = "text")
    private String polishedText;

    @Column(name = "accepted_polished")
    private Boolean acceptedPolished = false;

    @Column(name = "inspired_by_psalm", length = 20)
    private String inspiredByPsalm;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
