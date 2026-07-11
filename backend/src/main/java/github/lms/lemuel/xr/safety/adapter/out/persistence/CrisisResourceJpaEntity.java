package github.lms.lemuel.xr.safety.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crisis_resources")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CrisisResourceJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String region;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "contact_type", nullable = false, length = 20)
    private String contactType;

    @Column(name = "contact_value", nullable = false, length = 255)
    private String contactValue;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 50)
    private String hours;

    @Column(length = 30)
    private String category;

    @Column
    private Short priority = 50;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
