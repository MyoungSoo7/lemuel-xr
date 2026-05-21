package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scripture_passages")
@Getter
@Setter
public class ScripturePassageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String reference;

    @Column(nullable = false, length = 20)
    private String translation;

    @Column(nullable = false, length = 20)
    private String book;

    @Column(name = "book_code", length = 10)
    private String bookCode;

    @Column(nullable = false)
    private Integer chapter;

    @Column(name = "verse_start", nullable = false)
    private Integer verseStart;

    @Column(name = "verse_end")
    private Integer verseEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "theme_tags", columnDefinition = "text[]")
    private String[] themeTags;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "character_tags", columnDefinition = "text[]")
    private String[] characterTags;
}
