package github.lms.lemuel.xr.asset.application.seed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * ManifestScanner 단위 테스트 — classpath 의 manifest json 을 찾아 배열로 반환.
 *
 * 메인 리소스(src/main/resources/manifests)가 테스트 classpath 에도 올라오므로
 * 스캔 결과가 비어있지 않고 모두 .json 임을 검증한다.
 */
class ManifestScannerTest {

    private val scanner = ManifestScanner()

    @Test
    fun `scan 은 classpath manifest json 들을 반환`() {
        val found = scanner.scan()

        assertThat(found).isNotEmpty()
        assertThat(found.toList())
            .allSatisfy { r -> assertThat(r.filename).endsWith(".json") }
    }
}
