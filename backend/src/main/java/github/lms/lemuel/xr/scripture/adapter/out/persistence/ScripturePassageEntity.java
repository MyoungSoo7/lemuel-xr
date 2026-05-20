package github.lms.lemuel.xr.scripture.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "scripture_passages")
@Getter
public class ScripturePassageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String reference;          // 'gen-45:5'

    @Column(nullable = false, length = 20)
    private String translation;        // 'modern' (현대인의 성경) | 'rev' (개역개정)

    @Column(nullable = false, length = 20)
    private String book;

    @Column(nullable = false)
    private Integer chapter;

    @Column(name = "verse_start", nullable = false)
    private Integer verseStart;

    @Column(name = "verse_end")
    private Integer verseEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
}
