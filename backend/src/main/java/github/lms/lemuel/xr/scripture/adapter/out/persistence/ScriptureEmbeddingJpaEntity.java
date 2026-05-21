package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * pgvector 임베딩 행.
 *
 * <p>vector 타입은 Hibernate 표준 매핑에 없어 columnDefinition 로 지정.
 * JPA 가 영속화하기보다는 native query 로 조회하는 용도 (HNSW 검색).</p>
 */
@Entity
@Table(name = "scripture_embeddings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ScriptureEmbeddingJpaEntity {
    @Id
    @Column(name = "passage_id")
    private Long passageId;

    @Column(name = "embedding", columnDefinition = "vector(1536)", nullable = false)
    private String embedding;   // "[0.012, -0.034, ...]" 직렬화 형태로 다룸

    @Column(name = "embed_model", nullable = false, length = 50)
    private String embedModel;

    @Column(name = "embed_at", nullable = false)
    private LocalDateTime embedAt;
}
