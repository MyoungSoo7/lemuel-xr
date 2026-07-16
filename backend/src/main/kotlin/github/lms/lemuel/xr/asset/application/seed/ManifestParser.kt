package github.lms.lemuel.xr.asset.application.seed

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

/** manifest json 리소스를 타입 세이프한 [ManifestDocument] 로 파싱한다. */
@Component
class ManifestParser(
    private val json: ObjectMapper,
) {

    /** 리소스 스트림을 열어 [ManifestDocument] 로 역직렬화. */
    fun parse(resource: Resource): ManifestDocument =
        resource.inputStream.use { json.readValue(it, ManifestDocument::class.java) }
}
