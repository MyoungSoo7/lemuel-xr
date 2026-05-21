package github.lms.lemuel.xr.content.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TopicTest {

    @Test
    void byId_정합() {
        assertThat(Topic.byId(1)).isEqualTo(Topic.JOURNAL);
        assertThat(Topic.byId(7)).isEqualTo(Topic.FEAR);
    }

    @Test
    void 잘못된_id_예외() {
        assertThatThrownBy(() -> Topic.byId(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 모든_topic_은_id_와_제목_가짐() {
        for (Topic t : Topic.values()) {
            assertThat(t.id()).isBetween(1, 7);
            assertThat(t.title()).isNotBlank();
        }
    }
}
