package github.lms.lemuel.xr.common.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot 4 는 Jackson 3 (`tools.jackson.databind.ObjectMapper`) 를 기본 ObjectMapper 로 제공.
 * 일부 레거시 컴포넌트(AssetManifestSeeder 등) 는 Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`)
 * 를 사용 — 별도 Jackson 2 bean 을 명시 제공해 호환성 유지.
 *
 * **KotlinModule 을 반드시 등록한다.** Boot 가 자동 구성하는 건 Jackson 3 매퍼뿐이고,
 * 여기서 손으로 만드는 Jackson 2 매퍼에는 아무 모듈도 안 붙는다. 그 상태로는 Kotlin 의
 * 주 생성자를 못 읽어서, **파라미터에 기본값이 없는 data class 는 역직렬화가 통째로 실패한다**
 * (`no Creators, like default constructor, exist`).
 *
 * 2026-08-05 운영에서 실제로 터졌다: 골든셋 정기 채점이
 * `EvaluateGroundingUseCase.Passage`(기본값 없는 data class) 에서 매 실행 실패했다.
 * 같은 파일의 `GoldenSet.Fixture` 는 *모든* 파라미터에 기본값이 있어 Kotlin 이 no-arg 생성자를
 * 만들어 주는 바람에 우연히 통과했고, 그래서 실패가 한 단계 안쪽에서야 드러났다.
 * `ManifestDocument` 가 멀쩡했던 것도 실력이 아니라 모든 필드에 `@param:JsonProperty` 가
 * 붙어 있던 덕이다 — 셋 다 같은 지뢰를 밟고 있었고 하나만 터졌을 뿐이다.
 *
 * 테스트가 못 잡은 이유도 여기 있다. 테스트는 `jacksonObjectMapper()` 를 직접 넘겨 썼고
 * 프로덕션만 이 맨 매퍼를 주입받았다 — 즉 **테스트와 프로덕션이 서로 다른 매퍼를 쓰고 있었다.**
 * 그래서 회귀 테스트는 이 빈을 그대로 호출한다 (`LegacyJacksonObjectMapperTest`).
 */
@Configuration
class JacksonCompatConfig {

    @Bean
    fun legacyJacksonObjectMapper(): ObjectMapper = jacksonObjectMapper()
}
