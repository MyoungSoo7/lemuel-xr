package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.LemuelXrApplication
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * `application.yml` 의 `safety.crisis-keywords-regex` 를 **스프링 컨텍스트 없이** 읽는다.
 *
 * 값이 `${'$'}{SAFETY_CRISIS_REGEX:...}` 라는 placeholder 라서, 그냥 읽어다 컴파일하면
 * 문자열 `${'$'}{SAFETY_CRISIS_REGEX:` 로 시작하는 식이 되어 **아무것도 매칭하지 않는다.**
 * 그런데 정규식으로는 유효하므로 컴파일은 성공한다 — 즉 실패가 예외가 아니라 *조용한 0건* 으로
 * 나타난다. 위기 키워드 스캔에서 0건은 곧 "안전하다" 로 읽히는 값이라, 이 한 줄을 빠뜨리면
 * 검사는 초록불인 채 아무 일도 하지 않는다.
 *
 * 두 곳에서 쓴다(`CrisisKeywordDocContractTest`, `GoldenSetIntegrityTest`). 각자 파싱하면
 * 한쪽만 placeholder 처리를 잃어버려도 드러나지 않으므로 여기 한 군데로 모은다.
 */
object RuntimeCrisisRegex {

    /** placeholder 기본값(안쪽 식)만 돌려준다. */
    fun load(): String {
        val app = moduleRoot().resolve("src/main/resources/application.yml").toFile()

        @Suppress("UNCHECKED_CAST")
        val root = app.reader(StandardCharsets.UTF_8).use { Yaml().load<Any?>(it) } as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val safety = root["safety"] as Map<String, Any?>
        val raw = safety["crisis-keywords-regex"].toString()

        val open = raw.indexOf(':')
        check(raw.startsWith("\${") && raw.endsWith("}") && open > 0) {
            "crisis-keywords-regex 가 \${ENV:default} 형태가 아니다: $raw"
        }
        return raw.substring(open + 1, raw.length - 1)
    }

    fun repoRoot(): Path = checkNotNull(moduleRoot().parent) { "리포 루트 탐색 실패" }

    /** LemuelXrApplication code-source 위치에서 위로 올라가 src/main/kotlin 을 가진 모듈 루트. */
    fun moduleRoot(): Path {
        var p: Path? = Path.of(LemuelXrApplication::class.java.protectionDomain.codeSource.location.toURI())
        if (p != null && !Files.isDirectory(p)) p = p.parent
        while (p != null && !Files.isDirectory(p.resolve("src/main/kotlin"))) p = p.parent
        return checkNotNull(p) { "모듈 루트(src/main/kotlin 보유) 탐색 실패" }
    }
}
