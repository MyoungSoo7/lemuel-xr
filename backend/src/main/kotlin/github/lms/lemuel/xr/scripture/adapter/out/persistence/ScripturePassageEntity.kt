package github.lms.lemuel.xr.scripture.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "scripture_passages")
class ScripturePassageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 50)
    var reference: String? = null

    @Column(nullable = false, length = 20)
    var translation: String? = null

    @Column(nullable = false, length = 20)
    var book: String? = null

    @Column(name = "book_code", length = 10)
    var bookCode: String? = null

    @Column(nullable = false)
    var chapter: Int? = null

    @Column(name = "verse_start", nullable = false)
    var verseStart: Int? = null

    @Column(name = "verse_end")
    var verseEnd: Int? = null

    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String? = null

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "theme_tags", columnDefinition = "text[]")
    var themeTags: Array<String>? = null

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "character_tags", columnDefinition = "text[]")
    var characterTags: Array<String>? = null
}
