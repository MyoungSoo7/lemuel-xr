package github.lms.lemuel.xr.ai.grounding.adapter.out.goldenset

import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.lemuel.xr.ai.grounding.application.port.out.GoldenSetPort
import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * 클래스패스에서 골든셋을 읽는 [GoldenSetPort] 어댑터.
 *
 * 원본은 리포 루트 `eval/grounding/` 이고, 빌드(processResources)가 이를
 * `grounding-golden-set/` 로 복사해 jar 에 넣는다. 따라서 로컬 실행이든 컨테이너든
 * 같은 경로로 읽히고, 배포된 파드도 별도 볼륨 없이 골든셋을 갖는다.
 */
class ClasspathGoldenSetAdapter(
    private val mapper: ObjectMapper,
    private val classLoader: ClassLoader = ClasspathGoldenSetAdapter::class.java.classLoader,
) : GoldenSetPort {

    override fun load(version: String): GoldenSet.Loaded {
        val resolver = PathMatchingResourcePatternResolver(classLoader)
        val root = "${GoldenSet.CLASSPATH_ROOT}/$version"

        val manifestRes = resolver.getResource("classpath:$root/manifest.json")
        check(manifestRes.exists()) {
            "골든셋 manifest 없음: $root/manifest.json — eval/grounding 복사(processResources)가 빠졌을 수 있다"
        }
        val manifest = manifestRes.inputStream.use { mapper.readValue(it, GoldenSet.Manifest::class.java) }

        val fixtures = resolver.getResources("classpath*:$root/fixtures/*.json")
            .sortedBy { it.filename }
            .map { res ->
                val fixture = res.inputStream.use { mapper.readValue(it, GoldenSet.Fixture::class.java) }
                fixture.copy(sourceName = res.filename ?: "")
            }
        // 픽스처 0건은 "표본이 없다" 가 아니라 대개 클래스패스 배선이 깨진 것이다.
        // 조용히 빈 결과를 내면 지표가 전부 null 로 나와 원인을 한참 헤매게 된다.
        check(fixtures.isNotEmpty()) { "픽스처가 한 건도 없다: $root/fixtures/*.json" }
        return GoldenSet.Loaded(manifest, fixtures)
    }
}
