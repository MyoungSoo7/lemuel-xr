package github.lms.lemuel.xr.asset.application.seed

import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Component

/** classpath 에서 manifest json 파일들을 찾는다. */
@Component
class ManifestScanner {

    private val resolver: ResourcePatternResolver = PathMatchingResourcePatternResolver()

    /** manifests json 파일 스캔. 실패 시 빈 배열(부팅 계속). */
    fun scan(): Array<Resource> =
        try {
            resolver.getResources("classpath*:manifests/**/*.json")
        } catch (e: Exception) {
            log.warn("manifest 스캔 실패: {}", e.message)
            emptyArray()
        }

    companion object {
        private val log = LoggerFactory.getLogger(ManifestScanner::class.java)
    }
}
