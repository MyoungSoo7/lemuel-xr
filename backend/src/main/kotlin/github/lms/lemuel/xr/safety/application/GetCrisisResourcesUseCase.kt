package github.lms.lemuel.xr.safety.application

import github.lms.lemuel.xr.safety.application.port.out.CrisisResourcePort
import github.lms.lemuel.xr.safety.domain.CrisisResource
import org.springframework.stereotype.Service

/**
 * 위기 자원 카탈로그 조회 — region/locale 별 활성 자원을 priority 순으로 반환.
 * SafetyController 의 /api/safety/crisis-resources endpoint 에서 호출.
 *
 * 포트가 이미 도메인 모델([CrisisResource])을 반환하므로 JPA 엔티티는
 * 애플리케이션 경계 밖으로 흐르지 않는다. 웹 DTO 변환은 컨트롤러가 담당.
 */
@Service
class GetCrisisResourcesUseCase(
    private val resources: CrisisResourcePort,
) {

    fun execute(region: String, locale: String): List<CrisisResource> =
        resources.findByRegionAndLocaleAndActiveOrderByPriority(region, locale, true)
}
