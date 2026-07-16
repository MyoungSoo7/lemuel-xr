package github.lms.lemuel.xr.safety.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "crisis_resources")
class CrisisResourceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 10)
    var region: String? = null

    @Column(nullable = false, length = 10)
    var locale: String? = null

    @Column(nullable = false, length = 100)
    var name: String? = null

    @Column(name = "contact_type", nullable = false, length = 20)
    var contactType: String? = null

    @Column(name = "contact_value", nullable = false, length = 255)
    var contactValue: String? = null

    @Column(columnDefinition = "text")
    var description: String? = null

    @Column(length = 50)
    var hours: String? = null

    @Column(length = 30)
    var category: String? = null

    @Column
    var priority: Short? = 50

    @Column(nullable = false)
    var active: Boolean? = true

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null
}
