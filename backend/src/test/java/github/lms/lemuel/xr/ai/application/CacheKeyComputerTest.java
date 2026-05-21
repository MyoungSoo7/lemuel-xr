package github.lms.lemuel.xr.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CacheKeyComputerTest {

    private final CacheKeyComputer keyer = new CacheKeyComputer();

    @Test
    void 동일_promptKey_와_variables_는_동일_키() {
        String k1 = keyer.compute("joseph.s2.monologue", Map.of("savePercentage", "1/3"));
        String k2 = keyer.compute("joseph.s2.monologue", Map.of("savePercentage", "1/3"));
        assertThat(k1).isEqualTo(k2);
    }

    @Test
    void 변수_순서_달라도_같은_키() {
        String k1 = keyer.compute("k", Map.of("a", 1, "b", 2));
        String k2 = keyer.compute("k", Map.of("b", 2, "a", 1));
        assertThat(k1).isEqualTo(k2);
    }

    @Test
    void 다른_promptKey_다른_키() {
        String k1 = keyer.compute("a", Map.of());
        String k2 = keyer.compute("b", Map.of());
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    void null_variables_도_안전() {
        String k = keyer.compute("k", null);
        assertThat(k).startsWith("k:");
    }

    @Test
    void 키는_promptKey_접두사_포함() {
        String k = keyer.compute("joseph.s4", Map.of("choice", "reveal"));
        assertThat(k).startsWith("joseph.s4:");
    }
}
