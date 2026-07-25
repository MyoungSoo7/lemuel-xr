package github.lms.lemuel.xr

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Data Definition Standard 강제 — lemuel-xr backend (근거: ~/wiki/DATA-STANDARD.md).
 *
 * 클래스패스/ArchUnit 비의존 **소스 파일 스캔**으로 강제:
 *  - N1(시각): 순간에 LocalDateTime 금지. 기존 위반은 baseline 동결(ratchet), 신규만 차단.
 *    (lemuel-xr 은 LocalDateTime/OffsetDateTime 혼재 → 진짜 래칫 대상. 표준은 UTC OffsetDateTime/Instant.)
 *  - N6a(enum): EnumType.STRING 저장만(ORDINAL/무인자 @Enumerated 금지). 현재 0 → 방지 가드.
 *  - N6b(enum): 마이그레이션 native PG enum(CREATE TYPE ... AS ENUM) 금지. baseline 동결(ratchet).
 */
class DataStandardTest {

    @Test
    fun n1_noNewLocalDateTime() {
        val current = scan("src/main/kotlin", ".kt") { it.contains("LocalDateTime") }
        ratchet(
            "N1", current,
            moduleRoot().resolve("src/test/resources/datastandard/n1-localdatetime-baseline.txt"),
            "신규 LocalDateTime 사용 — 순간은 UTC OffsetDateTime/Instant (~/wiki/DATA-STANDARD.md N1)"
        )
    }

    @Test
    fun n6a_enumStoredAsStringNotOrdinal() {
        val v = scan("src/main/kotlin", ".kt") { c ->
            c.contains("EnumType.ORDINAL") ||
                c.lineSequence().any { it.contains("@Enumerated") && !it.contains("STRING") }
        }
        mustBeZero("N6a", v, "enum은 EnumType.STRING 저장만(ORDINAL/무인자 @Enumerated 금지) (~/wiki/DATA-STANDARD.md N6)")
    }

    @Test
    fun n6b_noNativePostgresEnumInMigrations() {
        val v = scan("src/main/resources", ".sql") { it.uppercase().contains("AS ENUM") }
        ratchet(
            "N6b", v,
            moduleRoot().resolve("src/test/resources/datastandard/n6b-native-enum-baseline.txt"),
            "마이그레이션 native PG enum(CREATE TYPE ... AS ENUM) 금지 — varchar + EnumType.STRING (~/wiki/DATA-STANDARD.md N6)"
        )
    }

    // ---------------- helpers ----------------

    private fun scan(rel: String, ext: String, hit: (String) -> Boolean): Set<String> {
        val root = moduleRoot()
        val dir = root.resolve(rel)
        assertTrue(Files.isDirectory(dir), "$dir 없음")
        return dir.toFile().walkTopDown()
            .filter { it.isFile && it.name.endsWith(ext) }
            .filter { runCatching { hit(it.readText()) }.getOrDefault(false) }
            .map { root.relativize(it.toPath()).toString().replace('\\', '/') }
            .toSortedSet()
    }

    private fun ratchet(id: String, current: Set<String>, baselineFile: Path, message: String) {
        if (!Files.exists(baselineFile)) {
            Files.createDirectories(baselineFile.parent)
            Files.write(baselineFile, current)
            println("$id: baseline 생성(${current.size}건) → 반드시 커밋: $baselineFile")
            return
        }
        val baseline = Files.readAllLines(baselineFile).toSortedSet()
        val introduced = current.toSortedSet().apply { removeAll(baseline) }
        println("$id: current=${current.size} baseline=${baseline.size} introduced=${introduced.size}")
        if (introduced.isNotEmpty()) {
            throw AssertionError("$id 위반 — $message\n  " + introduced.joinToString("\n  "))
        }
    }

    private fun mustBeZero(id: String, violations: Set<String>, message: String) {
        println("$id: violations=${violations.size}")
        if (violations.isNotEmpty()) {
            throw AssertionError("$id 위반 — $message\n  " + violations.joinToString("\n  "))
        }
    }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    private fun moduleRoot(): Path {
        var p: Path? = Path.of(
            LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI()
        )
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }
}
