package github.lms.lemuel.xr.safety.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrisisKeywordScannerTest {

    private final CrisisKeywordScanner scanner = new CrisisKeywordScanner(
            "(자살|자해|죽고\\s?싶|죽어\\s?버|뛰어내리)"
    );

    @Test
    void 일반_텍스트는_매칭_X() {
        var r = scanner.scan("오늘 너무 지쳤어요");
        assertThat(r.matched()).isFalse();
    }

    @Test
    void 자살_키워드_매칭() {
        var r = scanner.scan("자살하고 싶어요");
        assertThat(r.matched()).isTrue();
        assertThat(r.severity()).isEqualTo("high");
    }

    @Test
    void 자해_변형도_매칭() {
        var r = scanner.scan("그냥 죽어 버리고 싶어");
        assertThat(r.matched()).isTrue();
    }

    @Test
    void excerpt_hash_는_64자_hex() {
        var r = scanner.scan("자살 생각이 들어요");
        assertThat(r.excerptHash()).hasSize(64);
        assertThat(r.excerptHash()).matches("[0-9a-f]+");
    }

    @Test
    void 빈_문자열은_무사() {
        var r = scanner.scan("");
        assertThat(r.matched()).isFalse();

        var r2 = scanner.scan(null);
        assertThat(r2.matched()).isFalse();
    }
}
