package github.lms.lemuel.xr.theology.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "content_versions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"content_kind", "content_ref", "version"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ContentVersionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "content_kind", nullable = false, length = 30)
    private String contentKind;

    @Column(name = "content_ref", nullable = false, length = 100)
    private String contentRef;

    @Column(nullable = false, length = 20)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> body;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "generated_by", length = 20)
    private String generatedBy;

    @Column(name = "generation_prompt", columnDefinition = "text")
    private String generationPrompt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;
}
